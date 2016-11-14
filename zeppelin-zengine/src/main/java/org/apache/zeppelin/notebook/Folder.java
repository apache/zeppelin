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

import java.util.*;

/**
 * Represents a folder of Notebook. ID of the folder is a normalized path of it.
 * 'normalized path' means the path that removed '/' from the beginning and the end of the path.
 * e.g. "a/b/c", but not "/a/b/c", "a/b/c/" or "/a/b/c/".
 * One exception can be the root folder, which is '/'.
 */
public class Folder {
  public static final String ROOT_FOLDER_ID = "/";

  private String id;

  // key: noteId
  private final Map<String, Note> notes = new LinkedHashMap<>();

  public Folder(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public static String normalizeFolderId(String id) {
    id = id.trim();
    id = id.replace("\\", "/");
    while (id.contains("///")) {
      id = id.replaceAll("///", "/");
    }
    id = id.replaceAll("//", "/");

    if (id.equals(ROOT_FOLDER_ID)) {
      return ROOT_FOLDER_ID;
    }

    if (id.charAt(0) == '/') {
      id = id.substring(1);
    }
    if (id.charAt(id.length() - 1) == '/') {
      id = id.substring(0, id.length() - 1);
    }

    return id;
  }

  public void setIdAndRenameNotes(String newId) {
    newId = normalizeFolderId(newId);

    synchronized (notes) {
      for (Note note : notes.values()) {
        String noteName = note.getNameWithoutPath();

        String newNotePath;
        if (newId.equals(ROOT_FOLDER_ID)) {
          newNotePath = noteName;
        } else {
          newNotePath = newId + "/" + noteName;
        }

        note.setName(newNotePath);
      }
    }

    id = newId;
  }

  public void addNote(Note note) {
    synchronized (notes) {
      notes.put(note.getId(), note);
    }
  }

  public void addNotes(List<Note> newNotes) {
    synchronized (notes) {
      for (Note note: newNotes) {
        notes.put(note.getId(), note);
      }
    }
  }

  public void removeNote(Note note) {
    synchronized (notes) {
      notes.remove(note.getId());
    }
  }

  public List<Note> getNotes() {
    return new LinkedList<>(notes.values());
  }

  public int countNotes() {
    return notes.size();
  }
}
