/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter.remote;

import static org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars
    .ZEPPELIN_INTERPRETER_OUTPUT_EVENTS_PER_BATCH;
import static org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars
    .ZEPPELIN_INTERPRETER_OUTPUT_WORKER_COUNT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;

/**
 * Buffers output in per-note FIFO queues, consumed by a fixed pool of workers. Only one worker
 * owns a note at a time. Callers periodically invoke {@link #flush()} to deliver buffered appends;
 * output boundaries request immediate delivery and expose callback completion to the caller.
 * Queues are retired as soon as their pending and in-flight output has been processed.
 *
 * <p>Accepted boundaries cannot be cancelled. Their completion acknowledges callback delivery,
 * not note idleness. RPC callers wait outside the queue monitor.
 */
public class ParagraphOutputDispatcher implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ParagraphOutputDispatcher.class);

  // Guarded by this: notes, mutable NoteQueue state, and worker startup.
  private final Map<String, NoteQueue> notes = new HashMap<>();
  private final BlockingQueue<NoteQueue> ready = new LinkedBlockingQueue<>();
  private final int eventsPerBatch;
  private final ExecutorService workers;
  private final int workerCount;
  private final RemoteInterpreterProcessListener listener;
  private final AppendOutputRunner appendRunner;
  private volatile boolean closed;
  private boolean workersStarted;

  public ParagraphOutputDispatcher(RemoteInterpreterProcessListener listener) {
    this(listener, ZEPPELIN_INTERPRETER_OUTPUT_WORKER_COUNT.getIntValue(),
        ZEPPELIN_INTERPRETER_OUTPUT_EVENTS_PER_BATCH.getIntValue());
  }

  ParagraphOutputDispatcher(RemoteInterpreterProcessListener listener, int workerCount) {
    this(listener, workerCount, ZEPPELIN_INTERPRETER_OUTPUT_EVENTS_PER_BATCH.getIntValue());
  }

  public ParagraphOutputDispatcher(RemoteInterpreterProcessListener listener, int workerCount,
                                   int eventsPerBatch) {
    if (workerCount < 1) {
      throw new IllegalArgumentException("Output worker count must be positive");
    }
    if (eventsPerBatch < 1) {
      throw new IllegalArgumentException("Output events per batch must be positive");
    }
    this.eventsPerBatch = eventsPerBatch;
    this.listener = listener;
    appendRunner = new AppendOutputRunner(listener);
    workers = Executors.newFixedThreadPool(workerCount, runnable -> {
      Thread thread = new Thread(runnable, "zeppelin-output-worker");
      thread.setDaemon(true);
      return thread;
    });
    this.workerCount = workerCount;
  }

  /** Makes pending notes ready without waiting for any listener callback. */
  public synchronized void flush() {
    if (!closed) {
      for (NoteQueue note : notes.values()) {
        makeReady(note);
      }
    }
  }

  public void appendOutput(String noteId, String paragraphId, int index, String output) {
    enqueue(noteId, new OutputEvent(new AppendOutputBuffer(noteId, paragraphId, index, output)),
        false);
  }

  public Future<Void> updateOutput(String noteId, String paragraphId, int index,
                                   InterpreterResult.Type type, String output) {
    return enqueueBoundary(noteId,
        () -> listener.onOutputUpdated(noteId, paragraphId, index, type, output));
  }

  public Future<Void> updateAllOutput(String noteId, String paragraphId,
                                      List<InterpreterResultMessage> messages) {
    // The caller may change its list before this queued operation gets a worker.
    List<InterpreterResultMessage> replacements = new ArrayList<>(messages);
    return enqueueBoundary(noteId, () -> {
      // Clear and replacements must stay together; later appends belong to the replaced output.
      listener.onOutputClear(noteId, paragraphId);
      for (int i = 0; i < replacements.size(); i++) {
        InterpreterResultMessage message = replacements.get(i);
        listener.onOutputUpdated(noteId, paragraphId, i, message.getType(), message.getData());
      }
    });
  }

  public Future<Void> checkpointOutput(String noteId, String paragraphId) {
    return enqueueBoundary(noteId, () -> listener.checkpointOutput(noteId, paragraphId));
  }

  private Future<Void> enqueueBoundary(String noteId, Runnable callback) {
    OutputEvent event = new OutputEvent(new Boundary(() -> {
      long start = System.nanoTime();
      try {
        callback.run();
      } catch (RuntimeException e) {
        LOGGER.warn("Failed to process output boundary for note {}", noteId, e);
        throw e;
      } finally {
        long time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        LOGGER.debug("Processing output boundary for note {} took {} milliseconds", noteId, time);
      }
    }));
    enqueue(noteId, event, true);
    return event.boundary;
  }

  private synchronized void enqueue(String noteId, OutputEvent event, boolean immediate) {
    if (closed) {
      throw new IllegalStateException("Output dispatcher is stopped");
    }
    if (!workersStarted) {
      workersStarted = true;
      for (int i = 0; i < workerCount; i++) {
        workers.execute(this::consume);
      }
    }
    NoteQueue note = notes.computeIfAbsent(noteId, NoteQueue::new);
    note.events.addLast(event);
    if (immediate) {
      makeReady(note);
    }
  }

  // Caller must hold this monitor: the scheduled check and ready insertion must be atomic.
  private void makeReady(NoteQueue note) {
    note.flushRequested = true;
    if (!note.scheduled) {
      note.scheduled = true;
      ready.offer(note);
    }
  }

  private void consume() {
    while (!closed && !Thread.currentThread().isInterrupted()) {
      NoteQueue note;
      try {
        note = ready.take();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      List<OutputEvent> batch = new ArrayList<>();
      synchronized (this) {
        if (closed) {
          return;
        }
        note.flushRequested = false;
        while (!note.events.isEmpty() && batch.size() < eventsPerBatch) {
          batch.add(note.events.removeFirst());
        }
        note.inFlight = batch;
      }
      try {
        deliver(batch);
      } finally {
        synchronized (this) {
          note.inFlight = null;
          note.scheduled = false;
          if (!closed) {
            if (note.events.isEmpty()) {
              notes.remove(note.noteId);
            } else if (note.flushRequested || batch.size() == eventsPerBatch) {
              makeReady(note);
            }
          }
        }
      }
    }
  }

  private void deliver(List<OutputEvent> batch) {
    List<AppendOutputBuffer> appends = new ArrayList<>();
    for (OutputEvent event : batch) {
      if (closed) {
        return;
      }
      if (event.append != null) {
        appends.add(event.append);
      } else {
        appendRunner.run(appends, () -> !closed);
        appends.clear();
        if (closed) {
          return;
        }
        event.boundary.run();
      }
    }
    if (!closed) {
      appendRunner.run(appends, () -> !closed);
    }
  }

  /**
   * Stops accepting output and fails unfinished boundaries. Remaining output is discarded when
   * workers observe shutdown; in-flight listener calls may finish. Does not wait for worker exit.
   */
  @Override
  public void close() {
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
      IllegalStateException stopped = new IllegalStateException("Output dispatcher is stopped");
      for (NoteQueue note : notes.values()) {
        failBoundaries(note.events, stopped);
        if (note.inFlight != null) {
          failBoundaries(note.inFlight, stopped);
        }
        note.events.clear();
      }
      notes.clear();
      ready.clear();
    }
    workers.shutdownNow();
  }

  private void failBoundaries(Iterable<OutputEvent> events, IllegalStateException stopped) {
    for (OutputEvent event : events) {
      if (event.boundary != null) {
        event.boundary.fail(stopped);
      }
    }
  }

  // Retained note queues, not a worker termination signal.
  synchronized int pendingNoteCount() {
    return notes.size();
  }

  private static class NoteQueue {
    private final String noteId;
    private final ArrayDeque<OutputEvent> events = new ArrayDeque<>();
    // Shutdown must release RPCs waiting on boundaries already drained from events.
    private List<OutputEvent> inFlight;
    // Covers ready and running: releasing ownership before delivery ends would allow two writers.
    private boolean scheduled;
    // A request arriving during delivery must survive until the current owner releases the note.
    private boolean flushRequested;

    private NoteQueue(String noteId) {
      this.noteId = noteId;
    }
  }

  // Exactly one of append and boundary is set.
  private static class OutputEvent {
    private final AppendOutputBuffer append;
    private final Boundary boundary;

    private OutputEvent(AppendOutputBuffer append) {
      this.append = append;
      boundary = null;
    }

    private OutputEvent(Boundary boundary) {
      append = null;
      this.boundary = boundary;
    }
  }

  // FutureTask wakes get() waiters without running externally supplied completion handlers.
  private static final class Boundary extends FutureTask<Void> {
    private Boundary(Runnable callback) {
      super(callback, null);
    }

    private void fail(Throwable failure) {
      setException(failure);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      // Accepted output must stay in the FIFO even if its RPC caller stops waiting.
      return false;
    }
  }
}
