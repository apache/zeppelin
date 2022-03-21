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

package org.apache.zeppelin.notebook;

import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.SchedulerThreadFactory;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.util.ExecutorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * An special NoteEventListener which handle events asynchronously
 */
public abstract class NoteEventAsyncListener implements NoteEventListener, Closeable {

  private static final Logger LOGGER = LoggerFactory.getLogger(NoteEventAsyncListener.class);

  private final ThreadPoolExecutor executor;
  private final String name;

  protected NoteEventAsyncListener(String name) {
    this.name = name;
    executor = new ThreadPoolExecutor(0, 1, 1, TimeUnit.MINUTES,
            new LinkedBlockingQueue<>(), new SchedulerThreadFactory(name));
  }

  public abstract void handleNoteCreateEvent(NoteCreateEvent noteCreateEvent);

  public abstract void handleNoteRemoveEvent(NoteRemoveEvent noteRemoveEvent);

  public abstract void handleNoteUpdateEvent(NoteUpdateEvent noteUpdateEvent);

  public abstract void handleParagraphCreateEvent(ParagraphCreateEvent paragraphCreateEvent);

  public abstract void handleParagraphRemoveEvent(ParagraphRemoveEvent paragraphRemoveEvent);

  public abstract void handleParagraphUpdateEvent(ParagraphUpdateEvent paragraphUpdateEvent);


  @Override
  public void close() {
    ExecutorUtil.softShutdown(name, executor, 2, TimeUnit.SECONDS);
  }

  @Override
  public void onNoteCreate(Note note, AuthenticationInfo subject) {
    executor.execute(new EventHandling(new NoteCreateEvent(note.getId())));
  }

  @Override
  public void onNoteRemove(Note note, AuthenticationInfo subject) {
    executor.execute(new EventHandling(new NoteRemoveEvent(note.getId())));
  }

  @Override
  public void onNoteUpdate(Note note, AuthenticationInfo subject) {
    executor.execute(new EventHandling(new NoteUpdateEvent(note.getId())));
  }

  @Override
  public void onParagraphCreate(Paragraph p) {
    executor.execute(new EventHandling(new ParagraphCreateEvent(p.getNote().getId(), p.getId())));
  }

  @Override
  public void onParagraphRemove(Paragraph p) {
    executor.execute(new EventHandling(new ParagraphRemoveEvent(p.getNote().getId(), p.getId())));
  }

  @Override
  public void onParagraphUpdate(Paragraph p) {
    executor.execute(new EventHandling(new ParagraphUpdateEvent(p.getNote().getId(), p.getId())));
  }

  @Override
  public void onParagraphStatusChange(Paragraph p, Job.Status status) {
    executor.execute(new EventHandling(new ParagraphStatusChangeEvent(p.getNote().getId(), p.getId())));
  }

  class EventHandling implements Runnable {

    private final NoteEvent event;
    public EventHandling(NoteEvent event) {
      this.event = event;
    }

    @Override
    public void run() {
      try {
        if (event instanceof NoteCreateEvent) {
          handleNoteCreateEvent((NoteCreateEvent) event);
        } else if (event instanceof NoteRemoveEvent) {
          handleNoteRemoveEvent((NoteRemoveEvent) event);
        } else if (event instanceof NoteUpdateEvent) {
          handleNoteUpdateEvent((NoteUpdateEvent) event);
        } else if (event instanceof ParagraphCreateEvent) {
          handleParagraphCreateEvent((ParagraphCreateEvent) event);
        } else if (event instanceof ParagraphRemoveEvent) {
          handleParagraphRemoveEvent((ParagraphRemoveEvent) event);
        } else if (event instanceof ParagraphUpdateEvent) {
          handleParagraphUpdateEvent((ParagraphUpdateEvent) event);
        } else {
          throw new RuntimeException("Unknown event: " + event.getClass().getSimpleName());
        }
      } catch (Exception e) {
        LOGGER.error("Fail to handle NoteEvent", e);
      }
    }
  }

  public boolean isEventQueueEmpty() {
    return executor.getQueue().isEmpty();
  }

  interface NoteEvent {

  }

  public static class NoteCreateEvent implements NoteEvent {
    private final String noteId;

    public NoteCreateEvent(String noteId) {
      this.noteId = noteId;
    }

    public String getNoteId() {
      return noteId;
    }
  }

  public static class NoteUpdateEvent implements NoteEvent {
    private final String noteId;

    public NoteUpdateEvent(String noteId) {
      this.noteId = noteId;
    }

    public String getNoteId() {
      return noteId;
    }
  }


  public static class NoteRemoveEvent implements NoteEvent {
    private final String noteId;

    public NoteRemoveEvent(String noteId) {
      this.noteId = noteId;
    }

    public String getNoteId() {
      return noteId;
    }
  }

  public static class ParagraphCreateEvent implements NoteEvent {
    private final String nodeId;
    private final String paragraphId;

    public ParagraphCreateEvent(String nodeId, String paragraphId) {
      this.nodeId = nodeId;
      this.paragraphId = paragraphId;
    }

    public String getNodeId() {
      return nodeId;
    }

    public String getParagraphId() {
      return paragraphId;
    }
  }

  public static class ParagraphUpdateEvent implements NoteEvent {
    private final String nodeId;
    private final String paragraphId;

    public ParagraphUpdateEvent(String nodeId, String paragraphId) {
      this.nodeId = nodeId;
      this.paragraphId = paragraphId;
    }

    public String getNodeId() {
      return nodeId;
    }

    public String getParagraphId() {
      return paragraphId;
    }
  }

  public static class ParagraphRemoveEvent implements NoteEvent {
    private final String nodeId;
    private final String paragraphId;

    public ParagraphRemoveEvent(String nodeId, String paragraphId) {
      this.nodeId = nodeId;
      this.paragraphId = paragraphId;
    }

    public String getNodeId() {
      return nodeId;
    }

    public String getParagraphId() {
      return paragraphId;
    }
  }

  public static class ParagraphStatusChangeEvent implements NoteEvent {
    private final String nodeId;
    private final String paragraphId;

    public ParagraphStatusChangeEvent(String nodeId, String paragraphId) {
      this.nodeId = nodeId;
      this.paragraphId = paragraphId;
    }

    public String getNodeId() {
      return nodeId;
    }

    public String getParagraphId() {
      return paragraphId;
    }
  }
}
