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

import java.util.Observable;

/**
 * Notebook event observer
 */
public class NotebookEventObserver extends Observable {

  /**
   * Notebook event Enum
   */
  public static enum ACTIONS {
    REMOVED, CREATE, RUN,
    BIND_INTERPRETER, CHANGED_CONFIG, CHNAGED_NOTE_NAME, ADD_PARAGRAPH, MOVED_PARAGRAPH,
    RUN_PARAGRAPH
  }

  void notifyChanged(String noteId, ACTIONS action) {
    setChanged();
    NotebookChnagedEvent event = new NotebookChnagedEvent(noteId, action);
    notifyObservers(event);
  }

  /**
   * Notebook Event Model
   */
  public class NotebookChnagedEvent {
    private String noteId;
    private ACTIONS action;

    public NotebookChnagedEvent(String noteId, ACTIONS action) {
      this.noteId = noteId;
      this.action = action;
    }

    public String getNoteId() {
      return noteId;
    }

    public void setNoteId(String noteId) {
      this.noteId = noteId;
    }

    public ACTIONS getAction() {
      return action;
    }

    public void setAction(ACTIONS action) {
      this.action = action;
    }
  }

}
