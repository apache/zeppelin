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

/**
 * Metadata of Note: noteId & note Path
 */
public class NoteInfo {
  String id;
  String path;

  public NoteInfo(String id, String path) {
    super();
    this.id = id;
    this.path = path;
  }

  public NoteInfo(Note note) {
    id = note.getId();
    path = note.getPath();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getNoteName() {
    int pos = this.path.lastIndexOf("/");
    return path.substring(pos + 1);
  }

  public String getParent() {
    int pos = this.path.lastIndexOf("/");
    return path.substring(0, pos);
  }

  @Override
  public String toString() {
    return path + "_" + id + ".zpln";
  }
}
