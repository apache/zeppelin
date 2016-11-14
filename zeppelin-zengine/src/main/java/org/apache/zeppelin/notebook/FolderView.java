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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Folder view of notes of Notebook
 */
public class FolderView implements NoteNameListener {
  // key: folderId
  private final Map<String, Folder> folders = new LinkedHashMap<>();
  private final Map<Note, Folder> index = new LinkedHashMap<>();

  public Folder get(String folderId) {
    String normalizedFolderId = Folder.normalizeFolderId(folderId);
    return folders.get(normalizedFolderId);
  }

  /**
   * Rename folder of which id is folderId to newFolderId
   *
   * @param oldFolderId folderId to rename
   * @param newFolderId newFolderId
   * @return `null` if folder not exists, else old Folder.
   *         You can know which notes are renamed via old Folder.
   */
  public Folder renameFolder(String oldFolderId, String newFolderId) {
    if (!hasFolder(oldFolderId))
      return null;

    String normOldFolderId = Folder.normalizeFolderId(oldFolderId);
    String normNewFolderId = Folder.normalizeFolderId(newFolderId);

    // check if oldFolderId and newFolderId are same
    if (normOldFolderId.equals(normNewFolderId))
      return get(normOldFolderId);

    Folder oldFolder = get(normOldFolderId);

    // if the target folder already exists, combine two folder and remove old entry
    if (hasFolder(normNewFolderId)) {
      Folder targetFolder = get(normNewFolderId);
      targetFolder.addNotes(oldFolder.getNotes());
      synchronized (folders) {
        folders.remove(normOldFolderId);
      }
    }
    // else create new entry and remove old entry
    else {
      synchronized (folders) {
        folders.put(normNewFolderId, oldFolder);
        folders.remove(normOldFolderId);
      }
    }

    // rename the folder and notes which belong to it
    oldFolder.setIdAndRenameNotes(normNewFolderId);

    return oldFolder;
  }

  public Folder getFolderOf(Note note) {
    return index.get(note);
  }

  public void putNote(Note note) {
    if (note.isNameEmpty()) {
      return;
    }

    String folderId = note.getFolderId();

    if (!folders.containsKey(folderId)) {
      synchronized (folders) {
        folders.put(folderId, new Folder(folderId));
      }
    }

    Folder folder = folders.get(folderId);
    folder.addNote(note);
    synchronized (index) {
      index.put(note, folder);
    }
  }

  public void removeNote(Note note) {
    if (!index.containsKey(note)) {
      return;
    }

    Folder folder = index.get(note);
    folder.removeNote(note);
    // Remove the folder from folderMap, if there is no note
    if (folder.countNotes() == 0) {
      synchronized (folders) {
        folders.remove(folder.getId());
      }
    }

    synchronized (index) {
      index.remove(note);
    }
  }

  public void clear() {
    synchronized (folders) {
      folders.clear();
    }
    synchronized (index) {
      index.clear();
    }
  }

  public boolean hasFolder(String folderId) {
    return get(folderId) != null;
  }

  public boolean hasNote(Note note) {
    return index.containsKey(note);
  }

  public int countFolders() {
    return folders.size();
  }

  public int countNotes() {
    int count = 0;

    for (Folder folder : folders.values()) {
      count += folder.countNotes();
    }

    return count;
  }

  @Override
  public void onNameChanged(Note note, String oldName) {
    if (note.isNameEmpty()) {
      return;
    }
    System.out.println("onNameChanged" + note.toString());
    // New note
    if (!index.containsKey(note)) {
      putNote(note);
    }
    // Existing note
    else {
      removeNote(note);
      putNote(note);
    }
  }
}
