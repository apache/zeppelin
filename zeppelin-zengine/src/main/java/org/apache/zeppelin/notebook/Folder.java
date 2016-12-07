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

  private Folder parent;
  private Map<String, Folder> children = new LinkedHashMap<>();

  // key: noteId
  private final Map<String, Note> notes = new LinkedHashMap<>();

  private List<FolderListener> listeners = new LinkedList<>();

  private static final Logger logger = LoggerFactory.getLogger(Folder.class);

  public Folder(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    if (getId().equals(ROOT_FOLDER_ID))
      return ROOT_FOLDER_ID;

    String path = getId();

    int lastSlashIndex = path.lastIndexOf("/");
    if (lastSlashIndex < 0) {   // This folder is under the root
      return path;
    }

    return path.substring(lastSlashIndex + 1);
  }

  public String getParentFolderId() {
    if (getId().equals(ROOT_FOLDER_ID))
      return ROOT_FOLDER_ID;

    int lastSlashIndex = getId().lastIndexOf("/");
    // The root folder
    if (lastSlashIndex < 0) {
      return Folder.ROOT_FOLDER_ID;
    }

    return getId().substring(0, lastSlashIndex);
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

  /**
   * Rename this folder as well as the notes and the children belong to it
   *
   * @param newId
   */
  public void rename(String newId) {
    if (getId().equals(ROOT_FOLDER_ID))   // root folder cannot be renamed
      return;

    String oldId = getId();
    id = normalizeFolderId(newId);
    logger.info("Rename {} to {}", oldId, getId());

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

    for (Folder child : children.values()) {
      child.rename(getId() + "/" + child.getName());
    }

    notifyRenamed(oldId);
  }

  /**
   * Merge folder's notes and child folders
   *
   * @param folder
   */
  public void merge(Folder folder) {
    logger.info("Merge {} into {}", folder.getId(), getId());
    addNotes(folder.getNotes());
  }

  public void addFolderListener(FolderListener listener) {
    listeners.add(listener);
  }

  public void notifyRenamed(String oldFolderId) {
    for (FolderListener listener : listeners) {
      listener.onFolderRenamed(this, oldFolderId);
    }
  }

  public Folder getParent() {
    return parent;
  }

  public Map<String, Folder> getChildren() {
    return children;
  }

  public void setParent(Folder parent) {
    logger.info("Set parent of {} to {}", getId(), parent.getId());
    this.parent = parent;
  }

  public void addChild(Folder child) {
    if (child == this) // prevent the root folder from setting itself as child
      return;
    children.put(child.getId(), child);
  }

  public void removeChild(String folderId) {
    logger.info("Remove child {} from {}", folderId, getId());
    children.remove(folderId);
  }

  public void addNote(Note note) {
    logger.info("Add note {} to folder {}", note.getId(), getId());
    synchronized (notes) {
      notes.put(note.getId(), note);
    }
  }

  public void addNotes(List<Note> newNotes) {
    synchronized (notes) {
      for (Note note : newNotes) {
        notes.put(note.getId(), note);
      }
    }
  }

  public void removeNote(Note note) {
    logger.info("Remove note {} from folder {}", note.getId(), getId());
    synchronized (notes) {
      notes.remove(note.getId());
    }
  }

  public List<Note> getNotes() {
    return new LinkedList<>(notes.values());
  }

  public List<Note> getNotesRecursively() {
    List<Note> notes = getNotes();

    for (Folder child : children.values()) {
      notes.addAll(child.getNotesRecursively());
    }

    return notes;
  }

  public int countNotes() {
    return notes.size();
  }

  public boolean hasChild() {
    return children.size() > 0;
  }
}
