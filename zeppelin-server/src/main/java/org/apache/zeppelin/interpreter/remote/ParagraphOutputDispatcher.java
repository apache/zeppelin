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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;

/**
 * Buffers output in per-note FIFO queues, consumed by a fixed pool of workers. Only one operation
 * owns a note at a time. Callers periodically invoke {@link #flush()} to deliver buffered appends;
 * output boundaries request immediate delivery and expose callback completion to the caller.
 * Checkpoints pause their note until a separate worker pool of the same configured size finishes
 * the save callback.
 * Queues are retired as soon as their pending and in-flight output has been processed.
 *
 * <p>Accepted boundaries cannot be cancelled. Their completion acknowledges callback delivery,
 * not note idleness. RPC callers wait outside the queue monitor.
 */
public class ParagraphOutputDispatcher implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ParagraphOutputDispatcher.class);

  // Guarded by this: notes, mutable NoteQueue state, and worker startup.
  private final Map<String, NoteQueue> notes = new HashMap<>();
  private final BlockingQueue<NoteQueue> readyNotes = new LinkedBlockingQueue<>();
  private final int eventsPerBatch;
  private final ExecutorService outputExecutor;
  private final ExecutorService checkpointExecutor;
  private final int outputWorkerCount;
  private final RemoteInterpreterProcessListener listener;
  private final AppendOutputRunner appendRunner;
  private volatile boolean closed;
  private boolean outputWorkersStarted;

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

    outputExecutor = Executors.newFixedThreadPool(workerCount, runnable -> {
      Thread thread = new Thread(runnable, "zeppelin-output-worker");
      thread.setDaemon(true);
      return thread;
    });

    // Keep save capacity bounded independently from output delivery.
    checkpointExecutor = Executors.newFixedThreadPool(workerCount, runnable -> {
      Thread thread = new Thread(runnable, "zeppelin-output-checkpoint");
      thread.setDaemon(true);
      return thread;
    });
    this.outputWorkerCount = workerCount;
  }

  /** Makes pending notes ready without waiting for any listener callback. */
  public synchronized void flush() {
    if (!closed) {
      for (NoteQueue note : notes.values()) {
        makeReady(note);
      }
    }
  }

  public void appendOutput(String noteId, String paragraphId, int index, String executionOwner,
                           String output) {
    enqueue(noteId, OutputEvent.append(
        new AppendOutputBuffer(noteId, paragraphId, index, executionOwner, output)), false);
  }

  public Future<Void> updateOutput(String noteId, String paragraphId, int index,
                                   String executionOwner, InterpreterResult.Type type,
                                   String output) {
    return enqueueBoundary(noteId, () -> listener.onParagraphOutputUpdated(
        noteId, paragraphId, index, executionOwner, type, output));
  }

  public Future<Void> updateAllOutput(String noteId, String paragraphId, String executionOwner,
                                      List<InterpreterResultMessage> messages) {
    // The caller may change its list before this queued operation gets a worker.
    List<InterpreterResultMessage> replacements = new ArrayList<>(messages);
    return enqueueBoundary(noteId, () -> {
      // Clear and replacements must stay together; later appends belong to the replaced output.
      listener.onParagraphOutputClear(noteId, paragraphId, executionOwner);
      for (int i = 0; i < replacements.size(); i++) {
        InterpreterResultMessage message = replacements.get(i);
        listener.onParagraphOutputUpdated(noteId, paragraphId, i, executionOwner,
            message.getType(), message.getData());
      }
    });
  }

  public Future<Void> checkpointOutput(String noteId, String paragraphId) {
    return enqueueCheckpoint(noteId, () -> listener.checkpointOutput(noteId, paragraphId));
  }

  private Future<Void> enqueueBoundary(String noteId, Runnable callback) {
    Boundary boundary = createBoundary(noteId, callback);
    enqueue(noteId, OutputEvent.boundary(boundary), true);
    return boundary;
  }

  private Future<Void> enqueueCheckpoint(String noteId, Runnable callback) {
    Boundary boundary = createBoundary(noteId, callback);
    enqueue(noteId, OutputEvent.checkpoint(boundary), true);
    return boundary;
  }

  private Boundary createBoundary(String noteId, Runnable callback) {
    return new Boundary(() -> {
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
    });
  }

  private synchronized void enqueue(String noteId, OutputEvent event, boolean immediate) {
    if (closed) {
      throw new IllegalStateException("Output dispatcher is stopped");
    }

    if (!outputWorkersStarted) {
      outputWorkersStarted = true;
      for (int i = 0; i < outputWorkerCount; i++) {
        outputExecutor.execute(this::consume);
      }
    }

    NoteQueue note = notes.computeIfAbsent(noteId, NoteQueue::new);
    note.events.addLast(event);
    if (immediate) {
      makeReady(note);
    }
  }

  // Caller must hold this monitor: the state transition and ready insertion must be atomic.
  private void makeReady(NoteQueue note) {
    note.flushRequested = true;
    if (note.state == NoteState.IDLE) {
      note.state = NoteState.READY;
      readyNotes.offer(note);
    }
  }

  private void consume() {
    while (!closed && !Thread.currentThread().isInterrupted()) {
      NoteQueue note;
      try {
        note = readyNotes.take();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }

      List<OutputEvent> batch = new ArrayList<>();
      synchronized (this) {
        if (closed) {
          return;
        }

        note.state = NoteState.DELIVERING;
        note.flushRequested = false;

        while (!note.events.isEmpty() && batch.size() < eventsPerBatch) {
          OutputEvent event = note.events.removeFirst();
          batch.add(event);
          if (event.kind == OutputEvent.Kind.CHECKPOINT) {
            break;
          }
        }

        note.inFlightBoundaries.clear();
        for (OutputEvent event : batch) {
          if (event.boundary != null) {
            note.inFlightBoundaries.add(event.boundary);
          }
        }
      }

      boolean checkpointHandled = false;
      try {
        checkpointHandled = deliver(note, batch);
      } finally {
        if (!checkpointHandled) {
          releaseNote(note, batch.size() == eventsPerBatch);
        }
      }
    }
  }

  /** Returns whether checkpoint processing has assumed or released ownership of the note. */
  private boolean deliver(NoteQueue note, List<OutputEvent> batch) {
    List<AppendOutputBuffer> appends = new ArrayList<>();
    for (OutputEvent event : batch) {
      if (closed) {
        return false;
      }

      if (event.kind == OutputEvent.Kind.APPEND) {
        appends.add(event.append);
      } else {
        appendRunner.run(appends, () -> !closed);
        appends.clear();

        if (closed) {
          return false;
        }

        if (event.kind == OutputEvent.Kind.CHECKPOINT) {
          submitCheckpoint(note, event.boundary);
          return true;
        }

        event.boundary.run();
        removeInFlightBoundary(note, event.boundary);
      }
    }

    if (!closed) {
      appendRunner.run(appends, () -> !closed);
    }
    return false;
  }

  private void submitCheckpoint(NoteQueue note, Boundary boundary) {
    synchronized (this) {
      if (closed) {
        boundary.fail(new IllegalStateException("Output dispatcher is stopped"));
        return;
      }

      note.inFlightBoundaries.clear();
      note.inFlightBoundaries.add(boundary);
      note.state = NoteState.CHECKPOINTING;
    }

    try {
      checkpointExecutor.execute(() -> {
        try {
          if (!closed) {
            boundary.run();
          }
        } finally {
          releaseNote(note, true);
        }
      });
    } catch (RejectedExecutionException e) {
      boundary.fail(e);
      releaseNote(note, true);
    }
  }

  private synchronized void removeInFlightBoundary(NoteQueue note, Boundary boundary) {
    note.inFlightBoundaries.remove(boundary);
  }

  private synchronized void releaseNote(NoteQueue note, boolean scheduleRemainingEvents) {
    note.inFlightBoundaries.clear();
    note.state = NoteState.IDLE;

    if (!closed) {
      if (note.events.isEmpty()) {
        notes.remove(note.noteId);
      } else if (scheduleRemainingEvents || note.flushRequested) {
        makeReady(note);
      }
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
        for (Boundary boundary : note.inFlightBoundaries) {
          boundary.fail(stopped);
        }
        note.events.clear();
        note.inFlightBoundaries.clear();
      }

      notes.clear();
      readyNotes.clear();
    }

    outputExecutor.shutdownNow();
    checkpointExecutor.shutdownNow();
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
    private final List<Boundary> inFlightBoundaries = new ArrayList<>();
    private NoteState state = NoteState.IDLE;
    // A request arriving during delivery must survive until the current owner releases the note.
    private boolean flushRequested;

    private NoteQueue(String noteId) {
      this.noteId = noteId;
    }
  }

  private enum NoteState {
    IDLE,
    READY,
    DELIVERING,
    CHECKPOINTING
  }

  // Exactly one of append and boundary is set, according to kind.
  private static class OutputEvent {
    private enum Kind {
      APPEND,
      BOUNDARY,
      CHECKPOINT
    }

    private final Kind kind;
    private final AppendOutputBuffer append;
    private final Boundary boundary;

    private OutputEvent(Kind kind, AppendOutputBuffer append, Boundary boundary) {
      this.kind = kind;
      this.append = append;
      this.boundary = boundary;
    }

    private static OutputEvent append(AppendOutputBuffer append) {
      return new OutputEvent(Kind.APPEND, append, null);
    }

    private static OutputEvent boundary(Boundary boundary) {
      return new OutputEvent(Kind.BOUNDARY, null, boundary);
    }

    private static OutputEvent checkpoint(Boundary boundary) {
      return new OutputEvent(Kind.CHECKPOINT, null, boundary);
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
