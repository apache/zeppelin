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

package org.apache.zeppelin.notebook.socket;

import java.util.HashMap;
import java.util.Map;

/**
 * Zeppelin websocket massage template class.
 */
public class Message {
  /**
   * Representation of event type.
   */
  public static enum OP {
    GET_HOME_NOTE,    // [c-s] load note for home screen

    GET_NOTE,         // [c-s] client load note
                      // @param id note id

    NOTE,             // [s-c] note info
                      // @param note serialized Note object

    PARAGRAPH,        // [s-c] paragraph info
                      // @param paragraph serialized paragraph object

    PROGRESS,         // [s-c] progress update
                      // @param id paragraph id
                      // @param progress percentage progress

    NEW_NOTE,         // [c-s] create new notebook
    DEL_NOTE,         // [c-s] delete notebook
                      // @param id note id
    CLONE_NOTE,       // [c-s] clone new notebook
                      // @param id id of note to clone
                      // @param name name for the cloned note
    IMPORT_NOTE,      // [c-s] import notebook
                      // @param object notebook
    NOTE_UPDATE,

    NOTE_RENAME,

    FOLDER_RENAME,

    RUN_PARAGRAPH,    // [c-s] run paragraph
                      // @param id paragraph id
                      // @param paragraph paragraph content.ie. script
                      // @param config paragraph config
                      // @param params paragraph params

    COMMIT_PARAGRAPH, // [c-s] commit paragraph
                      // @param id paragraph id
                      // @param title paragraph title
                      // @param paragraph paragraph content.ie. script
                      // @param config paragraph config
                      // @param params paragraph params

    CANCEL_PARAGRAPH, // [c-s] cancel paragraph run
                      // @param id paragraph id

    MOVE_PARAGRAPH,   // [c-s] move paragraph order
                      // @param id paragraph id
                      // @param index index the paragraph want to go

    INSERT_PARAGRAPH, // [c-s] create new paragraph below current paragraph
                      // @param target index

    COPY_PARAGRAPH,   // [c-s] create new para below current para as a copy of current para
                      // @param target index
                      // @param title paragraph title
                      // @param paragraph paragraph content.ie. script
                      // @param config paragraph config
                      // @param params paragraph params

    EDITOR_SETTING,   // [c-s] ask paragraph editor setting
                      // @param magic magic keyword written in paragraph
                      // ex) spark.spark or spark

    COMPLETION,       // [c-s] ask completion candidates
                      // @param id
                      // @param buf current code
                      // @param cursor cursor position in code

    COMPLETION_LIST,  // [s-c] send back completion candidates list
                      // @param id
                      // @param completions list of string

    LIST_NOTES,                   // [c-s] ask list of note
    RELOAD_NOTES_FROM_REPO,       // [c-s] reload notes from repo

    NOTES_INFO,                   // [s-c] list of note infos
                                  // @param notes serialized List<NoteInfo> object

    PARAGRAPH_REMOVE,
    PARAGRAPH_CLEAR_OUTPUT,       // [c-s] clear output of paragraph
    PARAGRAPH_CLEAR_ALL_OUTPUT,   // [c-s] clear output of all paragraphs
    PARAGRAPH_APPEND_OUTPUT,      // [s-c] append output
    PARAGRAPH_UPDATE_OUTPUT,      // [s-c] update (replace) output
    PING,
    AUTH_INFO,

    ANGULAR_OBJECT_UPDATE,        // [s-c] add/update angular object
    ANGULAR_OBJECT_REMOVE,        // [s-c] add angular object del
    
    ANGULAR_OBJECT_UPDATED,       // [c-s] angular object value updated,

    ANGULAR_OBJECT_CLIENT_BIND,   // [c-s] angular object updated from AngularJS z object

    ANGULAR_OBJECT_CLIENT_UNBIND, // [c-s] angular object unbind from AngularJS z object

    LIST_CONFIGURATIONS,          // [c-s] ask all key/value pairs of configurations
    CONFIGURATIONS_INFO,          // [s-c] all key/value pairs of configurations
                                  // @param settings serialized Map<String, String> object

    CHECKPOINT_NOTE,              // [c-s] checkpoint note to storage repository
                                  // @param noteId
                                  // @param checkpointName

    LIST_REVISION_HISTORY,        // [c-s] list revision history of the notebook
                                  // @param noteId
    NOTE_REVISION,                // [c-s] get certain revision of note
                                  // @param noteId
                                  // @param revisionId
    SET_NOTE_REVISION,            // [c-s] set current notebook head to this revision
                                  // @param noteId
                                  // @param revisionId
    APP_APPEND_OUTPUT,            // [s-c] append output
    APP_UPDATE_OUTPUT,            // [s-c] update (replace) output
    APP_LOAD,                     // [s-c] on app load
    APP_STATUS_CHANGE,            // [s-c] on app status change

    LIST_NOTE_JOBS,               // [c-s] get note job management information
    LIST_UPDATE_NOTE_JOBS,        // [c-s] get job management information for until unixtime
    UNSUBSCRIBE_UPDATE_NOTE_JOBS, // [c-s] unsubscribe job information for job management
                                  // @param unixTime
    GET_INTERPRETER_BINDINGS,     // [c-s] get interpreter bindings
                                  // @param noteId
    SAVE_INTERPRETER_BINDINGS,    // [c-s] save interpreter bindings
                                  // @param noteId
                                  // @param selectedSettingIds
    INTERPRETER_BINDINGS,         // [s-c] interpreter bindings
    GET_INTERPRETER_SETTINGS,     // [c-s] get interpreter settings
    INTERPRETER_SETTINGS,         // [s-c] interpreter settings
    ERROR_INFO,                   // [s-c] error information to be sent
    WATCHER,                      // [s-c] Change websocket to watcher mode.
    PARAGRAPH_ADDED,              // [s-c] paragraph is added
    PARAGRAPH_REMOVED,            // [s-c] paragraph deleted
    PARAGRAPH_MOVED,              // [s-c] paragraph moved
    NOTE_UPDATED                  // [s-c] paragraph updated(name, config)
  }

  public static final Message EMPTY = new Message(null);
  
  public OP op;
  public Map<String, Object> data = new HashMap<>();
  public String ticket = "anonymous";
  public String principal = "anonymous";
  public String roles = "";

  public Message(OP op) {
    this.op = op;
  }

  public Message put(String k, Object v) {
    data.put(k, v);
    return this;
  }

  public Object get(String k) {
    return data.get(k);
  }

  public <T> T getType(String key) {
    return (T) data.get(key);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("Message{");
    sb.append("data=").append(data);
    sb.append(", op=").append(op);
    sb.append('}');
    return sb.toString();
  }
}
