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
package org.apache.zeppelin.background;

import org.apache.zeppelin.notebook.Note;

/**
 * TaskContext
 */
public class TaskContext {
  private final String revId;
  private final Note note;
  private String id;

  public TaskContext(Note note, String revId) {
    id = getTaskId(note.getId(), revId);
    this.note = note;
    this.revId = revId;
  }

  public String getRevId() {
    return revId;
  }

  public Note getNote() {
    return note;
  }

  public String getId() {
    return id;
  }

  public static String getTaskId(String noteId, String revId) {
    return (noteId + "-" + revId).toLowerCase();
  }
}
