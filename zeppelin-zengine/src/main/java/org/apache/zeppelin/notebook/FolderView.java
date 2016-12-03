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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Folder view of notes of Notebook.
 * FolderView allows you to see notes from perspective of folders.
 */
public class FolderView implements NoteNameListener, FolderListener {
  // key: folderId
  private final Map<String, Folder> folders = new LinkedHashMap<>();
  // key: a note, value: a folder where the note belongs to
  private final Map<Note, Folder> index = new LinkedHashMap<>();

  private static final Logger logger = LoggerFactory.getLogger(FolderView.class);

  public Folder getFolder(String folderId) {
    String normalizedFolderId = Folder.normalizeFolderId(folderId);
    return folders.get(normalizedFolderId);
  }

  /**
   * Rename folder of which id is folderId to newFolderId
   *
   * @param oldFolderId folderId to rename
   * @param newFolderId newFolderId
   * @return `null` if folder not exists, else old Folder
   * in order to know which notes and child folders are renamed
   */
  public Folder renameFolder(String oldFolderId, String newFolderId) {
    String normOldFolderId = Folder.normalizeFolderId(oldFolderId);
    String normNewFolderId = Folder.normalizeFolderId(newFolderId);

    if (!hasFolder(normOldFolderId))
      return null;

    if (oldFolderId.equals(Folder.ROOT_FOLDER_ID))  // cannot rename the root folder
      return null;

    // check whether oldFolderId and newFolderId are same or not
    if (normOldFolderId.equals(normNewFolderId))
      return getFolder(normOldFolderId);

    logger.info("Rename {} to {}", normOldFolderId, normNewFolderId);

    Folder oldFolder = getFolder(normOldFolderId);
    removeFolder(oldFolderId);

    oldFolder.rename(normNewFolderId);

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

    Folder folder = getOrCreateFolder(folderId);
    folder.addNote(note);

    synchronized (index) {
      index.put(note, folder);
    }
  }

  private Folder getOrCreateFolder(String folderId) {
    if (folders.containsKey(folderId))
      return folders.get(folderId);

    return createFolder(folderId);
  }

  private Folder createFolder(String folderId) {
    Folder newFolder = new Folder(folderId);
    newFolder.addFolderListener(this);

    logger.info("Create folder {}", folderId);

    synchronized (folders) {
      folders.put(folderId, newFolder);
    }

    Folder parentFolder = getOrCreateFolder(newFolder.getParentFolderId());

    newFolder.setParent(parentFolder);
    parentFolder.addChild(newFolder);

    return newFolder;
  }

  private void removeFolder(String folderId) {
    Folder removedFolder;

    synchronized (folders) {
      removedFolder = folders.remove(folderId);
    }

    if (removedFolder != null) {
      logger.info("Remove folder {}", folderId);
      Folder parent = removedFolder.getParent();
      parent.removeChild(folderId);
      removeFolderIfEmpty(parent.getId());
    }
  }

  private void removeFolderIfEmpty(String folderId) {
    if (!hasFolder(folderId))
      return;

    Folder folder = getFolder(folderId);
    if (folder.countNotes() == 0 && !folder.hasChild()) {
      logger.info("Folder {} is empty", folder.getId());
      removeFolder(folderId);
    }
  }

  public void removeNote(Note note) {
    if (!index.containsKey(note)) {
      return;
    }

    Folder folder = index.get(note);
    folder.removeNote(note);

    removeFolderIfEmpty(folder.getId());

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
    return getFolder(folderId) != null;
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

  /**
   * Fired after a note's setName() run.
   * When the note's name changed, FolderView should check if the note is in the right folder.
   *
   * @param note
   * @param oldName
   */
  @Override
  public void onNoteNameChanged(Note note, String oldName) {
    if (note.isNameEmpty()) {
      return;
    }
    logger.info("Note name changed: {} -> {}", oldName, note.getName());
    // New note
    if (!index.containsKey(note)) {
      putNote(note);
    }
    // Existing note
    else {
      // If the note is in the right place, just return
      Folder folder = index.get(note);
      if (folder.getId().equals(note.getFolderId())) {
        return;
      }
      // The note's folder is changed!
      removeNote(note);
      putNote(note);
    }
  }

  @Override
  public void onFolderRenamed(Folder folder, String oldFolderId) {
    if (getFolder(folder.getId()) == folder)  // the folder is at the right place
      return;
    logger.info("folder renamed: {} -> {}", oldFolderId, folder.getId());

    if (getFolder(oldFolderId) == folder)
      folders.remove(oldFolderId);

    Folder newFolder = getOrCreateFolder(folder.getId());
    newFolder.merge(folder);

    for (Note note : folder.getNotes()) {
      index.put(note, newFolder);
    }
  }
}
