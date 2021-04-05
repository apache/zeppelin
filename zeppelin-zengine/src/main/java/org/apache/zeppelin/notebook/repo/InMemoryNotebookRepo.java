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


package org.apache.zeppelin.notebook.repo;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.pf4j.Extension;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Extension
public class InMemoryNotebookRepo implements NotebookRepo {

  private Map<String, Note> notes = new HashMap<>();

  @Override
  public void init(ZeppelinConfiguration zConf) throws IOException {

  }

  @Override
  public Map<String, NoteInfo> list(AuthenticationInfo subject) throws IOException {
    Map<String, NoteInfo> notesInfo = new HashMap<>();
    for (Note note : notes.values()) {
      notesInfo.put(note.getId(), new NoteInfo(note));
    }
    return notesInfo;
  }

  @Override
  public Note get(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
    if (!notePath.startsWith("/")) {
      throw new RuntimeException(String.format("notePath '%s' is not started with '/'", notePath));
    }
    return notes.get(noteId);
  }

  @Override
  public void save(Note note, AuthenticationInfo subject) throws IOException {
    notes.put(note.getId(), note);
  }

  @Override
  public void move(String noteId, String notePath, String newNotePath, AuthenticationInfo subject) {
    if (!newNotePath.startsWith("/")) {
      throw new RuntimeException(String.format("newNotePath '%s' is not started with '/'", newNotePath));
    }
    if (newNotePath.startsWith("//")) {
      throw new RuntimeException(String.format("newNotePath '%s' is started with '//'", newNotePath));
    }
  }

  @Override
  public void move(String folderPath, String newFolderPath, AuthenticationInfo subject) {
    if (!folderPath.startsWith("/")) {
      throw new RuntimeException(String.format("folderPath '%s' is not started with '/'", folderPath));
    }
    if (folderPath.startsWith("//")) {
      throw new RuntimeException(String.format("folderPath '%s' is started with '//'", folderPath));
    }
    if (!newFolderPath.startsWith("/")) {
      throw new RuntimeException(String.format("newFolderPath '%s' is not started with '/'", newFolderPath));
    }
    if (newFolderPath.startsWith("//")) {
      throw new RuntimeException(String.format("newFolderPath '%s' is started with '//'", newFolderPath));
    }
  }

  @Override
  public void remove(String noteId, String notePath, AuthenticationInfo subject) throws IOException {
    if (!notePath.startsWith("/")) {
      throw new RuntimeException(String.format("notePath '%s' is not started with '/'", notePath));
    }
    notes.remove(noteId);
  }

  @Override
  public void remove(String folderPath, AuthenticationInfo subject) {
    if (!folderPath.startsWith("/")) {
      throw new RuntimeException(String.format("folderPath '%s' is not started with '/'", folderPath));
    }
  }

  @Override
  public void close() {

  }

  @Override
  public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
    return Collections.emptyList();
  }

  @Override
  public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {

  }

  public void reset() {
    this.notes.clear();
  }
}