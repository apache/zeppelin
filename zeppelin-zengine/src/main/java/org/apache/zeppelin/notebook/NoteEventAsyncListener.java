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
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * An special NoteEventListener which handle events asynchronously
 */
public abstract class NoteEventAsyncListener implements NoteEventListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(NoteEventAsyncListener.class);

  private BlockingQueue<NoteEvent> eventsQueue = new LinkedBlockingQueue<>();

  private Thread eventHandlerThread;

  public NoteEventAsyncListener(String name) {
    this.eventHandlerThread = new EventHandlingThread();
    this.eventHandlerThread.setName(name);
    this.eventHandlerThread.start();
  }

  public abstract void handleNoteCreateEvent(NoteCreateEvent noteCreateEvent) throws Exception;

  public abstract void handleNoteRemoveEvent(NoteRemoveEvent noteRemoveEvent) throws Exception;

  public abstract void handleNoteUpdateEvent(NoteUpdateEvent noteUpdateEvent) throws Exception;

  public abstract void handleParagraphCreateEvent(ParagraphCreateEvent paragraphCreateEvent) throws Exception;

  public abstract void handleParagraphRemoveEvent(ParagraphRemoveEvent paragraphRemoveEvent) throws Exception;

  public abstract void handleParagraphUpdateEvent(ParagraphUpdateEvent paragraphUpdateEvent) throws Exception;


  public void close() {
    this.eventHandlerThread.interrupt();
  }

  @Override
  public void onNoteCreate(Note note, AuthenticationInfo subject) {
    eventsQueue.add(new NoteCreateEvent(note, subject));
  }

  @Override
  public void onNoteRemove(Note note, AuthenticationInfo subject) {
    eventsQueue.add(new NoteRemoveEvent(note, subject));
  }

  @Override
  public void onNoteUpdate(Note note, AuthenticationInfo subject) {
    eventsQueue.add(new NoteUpdateEvent(note, subject));
  }

  @Override
  public void onParagraphCreate(Paragraph p) {
    eventsQueue.add(new ParagraphCreateEvent(p));
  }

  @Override
  public void onParagraphRemove(Paragraph p) {
    eventsQueue.add(new ParagraphRemoveEvent(p));
  }

  @Override
  public void onParagraphUpdate(Paragraph p) {
    eventsQueue.add(new ParagraphUpdateEvent(p));
  }

  @Override
  public void onParagraphStatusChange(Paragraph p, Job.Status status) {
    eventsQueue.add(new ParagraphStatusChangeEvent(p));
  }

  class EventHandlingThread extends Thread {

    @Override
    public void run() {
      while(!Thread.interrupted()) {
        try {
          NoteEvent event = eventsQueue.take();
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
  }

  /**
   * Used for testing
   *
   * @throws InterruptedException
   */
  public void drainEvents() throws InterruptedException {
    while(!eventsQueue.isEmpty()) {
      Thread.sleep(1000);
    }
    Thread.sleep(5000);
  }

  interface NoteEvent {

  }

  public static class NoteCreateEvent implements NoteEvent {
    private Note note;
    private AuthenticationInfo subject;

    public NoteCreateEvent(Note note, AuthenticationInfo subject) {
      this.note = note;
      this.subject = subject;
    }

    public Note getNote() {
      return note;
    }
  }

  public static class NoteUpdateEvent implements NoteEvent {
    private Note note;
    private AuthenticationInfo subject;

    public NoteUpdateEvent(Note note, AuthenticationInfo subject) {
      this.note = note;
      this.subject = subject;
    }

    public Note getNote() {
      return note;
    }
  }


  public static class NoteRemoveEvent implements NoteEvent {
    private Note note;
    private AuthenticationInfo subject;

    public NoteRemoveEvent(Note note, AuthenticationInfo subject) {
      this.note = note;
      this.subject = subject;
    }

    public Note getNote() {
      return note;
    }
  }

  public static class ParagraphCreateEvent implements NoteEvent {
    private Paragraph p;

    public ParagraphCreateEvent(Paragraph p) {
      this.p = p;
    }

    public Paragraph getParagraph() {
      return p;
    }
  }

  public static class ParagraphUpdateEvent implements NoteEvent {
    private Paragraph p;

    public ParagraphUpdateEvent(Paragraph p) {
      this.p = p;
    }

    public Paragraph getParagraph() {
      return p;
    }
  }

  public static class ParagraphRemoveEvent implements NoteEvent {
    private Paragraph p;

    public ParagraphRemoveEvent(Paragraph p) {
      this.p = p;
    }

    public Paragraph getParagraph() {
      return p;
    }
  }

  public static class ParagraphStatusChangeEvent implements NoteEvent {
    private Paragraph p;

    public ParagraphStatusChangeEvent(Paragraph p) {
      this.p = p;
    }

    public Paragraph getParagraph() {
      return p;
    }
  }
}
