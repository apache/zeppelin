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

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manager class for note. It handle all the note related operations, such as get, create,
 * delete & move note.
 *
 * It load 2 kinds of metadata into memory:
 * 1. Mapping from noteId to note name
 * 2. The tree structure of notebook folder
 *
 * Note will be loaded lazily. Initially only noteId nad note name is loaded,
 * other note content is loaded until getNote is called.
 *
 * TODO(zjffdu) implement the lifecycle manager of Note
 * (release memory if note is not used for some period)
 */
@Singleton
public class NoteManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(NoteManager.class);
  public static String TRASH_FOLDER = "~Trash";
  private Folder root;
  private Folder trash;

  private NotebookRepo notebookRepo;
  // noteId -> notePath
  private Map<String, String> notesInfo;

  @Inject
  public NoteManager(NotebookRepo notebookRepo) throws IOException {
    this.notebookRepo = notebookRepo;
    this.root = new Folder("/", notebookRepo);
    this.trash = this.root.getOrCreateFolder(TRASH_FOLDER);
    init();
  }

  // build the tree structure of notes
  private void init() throws IOException {
    this.notesInfo = notebookRepo.list(AuthenticationInfo.ANONYMOUS).values().stream()
        .collect(Collectors.toMap(noteInfo -> noteInfo.getId(), notesInfo -> notesInfo.getPath()));
    this.notesInfo.entrySet().stream()
        .forEach(entry ->
        {
          try {
            addOrUpdateNoteNode(new Note(new NoteInfo(entry.getKey(), entry.getValue())));
          } catch (IOException e) {
            LOGGER.warn(e.getMessage());
          }
        });
  }

  public Map<String, String> getNotesInfo() {
    return notesInfo;
  }

  /**
   * Return java stream instead of List to save memory, otherwise OOM will happen
   * when there's large amount of notes.
   * @return
   */
  public Stream<Note> getNotesStream() {
    return notesInfo.values().stream()
            .map(notePath -> {
              try {
                return getNoteNode(notePath).getNote();
              } catch (Exception e) {
                LOGGER.warn("Fail to load note: " + notePath, e);
                return null;
              }
            })
            .filter(note -> note != null);
  }

  /**
   *
   * @throws IOException
   */
  public void reloadNotes() throws IOException {
    this.root = new Folder("/", notebookRepo);
    this.trash = this.root.getOrCreateFolder(TRASH_FOLDER);
    init();
  }

  private void addOrUpdateNoteNode(Note note, boolean checkDuplicates) throws IOException {
    String notePath = note.getPath();
    String[] tokens = notePath.split("/");
    Folder curFolder = root;
    for (int i = 0; i < tokens.length - 1; ++i) {
      if (!StringUtils.isBlank(tokens[i])) {
        curFolder = curFolder.getOrCreateFolder(tokens[i]);
      }
    }
    if (checkDuplicates && curFolder.containsNote(tokens[tokens.length - 1])) {
      throw new IOException("Note '" + note.getPath() + "' existed");
    }
    curFolder.addNote(tokens[tokens.length -1], note);
    this.notesInfo.put(note.getId(), note.getPath());
  }

  private void addOrUpdateNoteNode(Note note) throws IOException {
    addOrUpdateNoteNode(note, false);
  }

  /**
   * Check whether there exist note under this notePath.
   *
   * @param notePath
   * @return
   */
  public boolean containsNote(String notePath) {
    try {
      getNoteNode(notePath);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Check whether there exist such folder.
   *
   * @param folderPath
   * @return
   */
  public boolean containsFolder(String folderPath) {
    try {
      getFolder(folderPath);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Save note to NoteManager, it won't check duplicates, this is used when updating note.
   *
   * @param note
   * @param subject
   * @throws IOException
   */
  public void saveNote(Note note, AuthenticationInfo subject) throws IOException {
    addOrUpdateNoteNode(note);
    this.notebookRepo.save(note, subject);
    note.setLoaded(true);
  }

  public void addNote(Note note, AuthenticationInfo subject) throws IOException {
    addOrUpdateNoteNode(note, true);
    note.setLoaded(true);
  }

  /**
   * Add or update Note
   *
   * @param note
   * @throws IOException
   */
  public void saveNote(Note note) throws IOException {
    saveNote(note, AuthenticationInfo.ANONYMOUS);
  }

  /**
   * Remove note from NotebookRepo and NoteManager
   *
   * @param noteId
   * @param subject
   * @throws IOException
   */
  public void removeNote(String noteId, AuthenticationInfo subject) throws IOException {
    String notePath = this.notesInfo.remove(noteId);
    Folder folder = getOrCreateFolder(getFolderName(notePath));
    folder.removeNote(getNoteName(notePath));
    this.notebookRepo.remove(noteId, notePath, subject);
  }

  public void moveNote(String noteId,
                       String newNotePath,
                       AuthenticationInfo subject) throws IOException {
    String notePath = this.notesInfo.get(noteId);
    if (noteId == null) {
      throw new IOException("No metadata found for this note: " + noteId);
    }

    // move the old NoteNode from notePath to newNotePath
    NoteNode noteNode = getNoteNode(notePath);
    noteNode.getParent().removeNote(getNoteName(notePath));
    noteNode.setNotePath(newNotePath);
    String newParent = getFolderName(newNotePath);
    Folder newFolder = getOrCreateFolder(newParent);
    newFolder.addNoteNode(noteNode);

    // update noteInfo mapping
    this.notesInfo.put(noteId, newNotePath);

    // update notebookrepo
    this.notebookRepo.move(noteId, notePath, newNotePath, subject);
  }

  public void moveFolder(String folderPath,
                         String newFolderPath,
                         AuthenticationInfo subject) throws IOException {

    // update notebookrepo
    this.notebookRepo.move(folderPath, newFolderPath, subject);

    // update filesystem tree
    Folder folder = getFolder(folderPath);
    folder.getParent().removeFolder(folder.getName(), subject);
    Folder newFolder = getOrCreateFolder(newFolderPath);
    newFolder.getParent().addFolder(newFolder.getName(), folder);

    // update notesInfo
    for (Note note : folder.getRawNotesRecursively()) {
      notesInfo.put(note.getId(), note.getPath());
    }
  }

  /**
   * Remove the folder from the tree and returns the affected NoteInfo under this folder.
   *
   * @param folderPath
   * @param subject
   * @return
   * @throws IOException
   */
  public List<Note> removeFolder(String folderPath, AuthenticationInfo subject) throws IOException {

    // update notebookrepo
    this.notebookRepo.remove(folderPath, subject);

    // update filesystem tree
    Folder folder = getFolder(folderPath);
    List<Note> notes = folder.getParent().removeFolder(folder.getName(), subject);

    // update notesInfo
    for (Note note : notes) {
      this.notesInfo.remove(note.getId());
    }

    return notes;
  }

  /**
   * Get note from NotebookRepo.
   *
   * @param noteId
   * @return return null if not found on NotebookRepo.
   * @throws IOException
   */
  public Note getNote(String noteId, boolean forceLoad) throws IOException {
    String notePath = this.notesInfo.get(noteId);
    if (notePath == null) {
      return null;
    }
    NoteNode noteNode = getNoteNode(notePath);
    return noteNode.getNote(forceLoad);
  }

  /**
   * Get note from NotebookRepo.
   *
   * @param noteId
   * @return return null if not found on NotebookRepo.
   * @throws IOException
   */
  public Note getNote(String noteId) throws IOException {
    String notePath = this.notesInfo.get(noteId);
    if (notePath == null) {
      return null;
    }
    NoteNode noteNode = getNoteNode(notePath);
    return noteNode.getNote();
  }

  /**
   *
   * @param folderName  Absolute path of folder name
   * @return
   */
  public Folder getOrCreateFolder(String folderName) {
    String[] tokens = folderName.split("/");
    Folder curFolder = root;
    for (int i = 0; i < tokens.length; ++i) {
      if (!StringUtils.isBlank(tokens[i])) {
        curFolder = curFolder.getOrCreateFolder(tokens[i]);
      }
    }
    return curFolder;
  }

  private NoteNode getNoteNode(String notePath) throws IOException {
    String[] tokens = notePath.split("/");
    Folder curFolder = root;
    for (int i = 0; i < tokens.length - 1; ++i) {
      if (!StringUtils.isBlank(tokens[i])) {
        curFolder = curFolder.getFolder(tokens[i]);
        if (curFolder == null) {
          throw new IOException("Can not find note: " + notePath);
        }
      }
    }
    NoteNode noteNode = curFolder.getNote(tokens[tokens.length - 1]);
    if (noteNode == null) {
      throw new IOException("Can not find note: " + notePath);
    }
    return noteNode;
  }

  private Folder getFolder(String folderPath) throws IOException {
    String[] tokens = folderPath.split("/");
    Folder curFolder = root;
    for (int i = 0; i < tokens.length; ++i) {
      if (!StringUtils.isBlank(tokens[i])) {
        curFolder = curFolder.getFolder(tokens[i]);
        if (curFolder == null) {
          throw new IOException("Can not find folder: " + folderPath);
        }
      }
    }
    return curFolder;
  }

  public Folder getTrashFolder() {
    return this.trash;
  }

  private String getFolderName(String notePath) {
    int pos = notePath.lastIndexOf("/");
    return notePath.substring(0, pos);
  }

  private String getNoteName(String notePath) {
    int pos = notePath.lastIndexOf("/");
    return notePath.substring(pos + 1);
  }

  /**
   * Represent one folder that could contains sub folders and note files.
   */
  public static class Folder {

    private String name;
    private Folder parent;
    private NotebookRepo notebookRepo;

    // noteName -> NoteNode
    private Map<String, NoteNode> notes = new HashMap<>();
    // folderName -> Folder
    private Map<String, Folder> subFolders = new HashMap<>();

    public Folder(String name, NotebookRepo notebookRepo) {
      this.name = name;
      this.notebookRepo = notebookRepo;
    }

    public Folder(String name, Folder parent, NotebookRepo notebookRepo) {
      this(name, notebookRepo);
      this.parent = parent;
    }

    public synchronized Folder getOrCreateFolder(String folderName) {
      if (StringUtils.isBlank(folderName)) {
        return this;
      }
      if (!subFolders.containsKey(folderName)) {
        subFolders.put(folderName, new Folder(folderName, this, notebookRepo));
      }
      return subFolders.get(folderName);
    }

    public Folder getParent() {
      return parent;
    }

    public void setParent(Folder parent) {
      this.parent = parent;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Folder getFolder(String folderName) {
      return subFolders.get(folderName);
    }

    public Map<String, Folder> getFolders() {
      return subFolders;
    }

    public NoteNode getNote(String noteName) {
      return this.notes.get(noteName);
    }

    public void addNote(String noteName, Note note) {
      notes.put(noteName, new NoteNode(note, this, notebookRepo));
    }

    /**
     * Attach another folder under this folder, this is used when moving folder.
     * The path of notes under this folder also need to be updated.
     */
    public void addFolder(String folderName, Folder folder) throws IOException {
      subFolders.put(folderName, folder);
      folder.setParent(this);
      folder.setName(folderName);
      for (NoteNode noteNode : folder.getNoteNodeRecursively()) {
        noteNode.updateNotePath();
      }
    }

    public boolean containsNote(String noteName) {
      return notes.containsKey(noteName);
    }

    /**
     * Attach note under this folder, this is used when moving note
     * @param noteNode
     */
    public void addNoteNode(NoteNode noteNode) {
      this.notes.put(noteNode.getNoteName(), noteNode);
      noteNode.setParent(this);
    }

    public void removeNote(String noteName) {
      this.notes.remove(noteName);
    }

    public List<Note> removeFolder(String folderName,
                                   AuthenticationInfo subject) throws IOException {
      Folder folder = this.subFolders.remove(folderName);
      return folder.getRawNotesRecursively();
    }

    public List<Note> getRawNotesRecursively() {
      List<Note> notesInfo = new ArrayList<>();
      for (NoteNode noteNode : this.notes.values()) {
        notesInfo.add(noteNode.getRawNote());
      }
      for (Folder folder : subFolders.values()) {
        notesInfo.addAll(folder.getRawNotesRecursively());
      }
      return notesInfo;
    }

    public List<NoteNode> getNoteNodeRecursively() {
      List<NoteNode> notes = new ArrayList<>();
      notes.addAll(this.notes.values());
      for (Folder folder : subFolders.values()) {
        notes.addAll(folder.getNoteNodeRecursively());
      }
      return notes;
    }

    public Map<String, NoteNode> getNotes() {
      return notes;
    }

    public String getPath() {
      // root
      if (name.equals("/")) {
        return name;
      }
      // folder under root
      if (parent.name.equals("/")) {
        return "/" + name;
      }
      // other cases
      return parent.toString() + "/" + name;
    }

    @Override
    public String toString() {
      return getPath();
    }
  }

  /**
   * One node in the file system tree structure which represent the note.
   * This class has 2 usage scenarios:
   * 1. metadata of note (only noteId and note name is loaded via reading the file name)
   * 2. the note object (note content is loaded from NotebookRepo)
   *
   * It will load note from NotebookRepo lazily until method getNote is called.
   */
  public static class NoteNode {

    private Folder parent;
    private Note note;
    private NotebookRepo notebookRepo;

    public NoteNode(Note note, Folder parent, NotebookRepo notebookRepo) {
      this.note = note;
      this.parent = parent;
      this.notebookRepo = notebookRepo;
    }

    public synchronized Note getNote() throws IOException {
        return getNote(true);
    }

    /**
     * This method will load note from NotebookRepo. If you just want to get noteId, noteName or
     * notePath, you can call method getNoteId, getNoteName & getNotePath
     * @return
     * @throws IOException
     */
    public synchronized Note getNote(boolean forceLoad) throws IOException {
      if (!note.isLoaded() && forceLoad) {
        note = notebookRepo.get(note.getId(), note.getPath(), AuthenticationInfo.ANONYMOUS);
        if (parent.toString().equals("/")) {
          note.setPath("/" + note.getName());
        } else {
          note.setPath(parent.toString() + "/" + note.getName());
        }
        note.setCronSupported(ZeppelinConfiguration.create());
        note.setLoaded(true);
      }
      return note;
    }

    public String getNoteId() {
      return this.note.getId();
    }

    public String getNoteName() {
      return this.note.getName();
    }

    public String getNotePath() {
      if (parent.getPath().equals("/")) {
        return parent.getPath() + note.getName();
      } else {
        return parent.getPath() + "/" + note.getName();
      }
    }

    /**
     * This method will just return the note object without checking whether it is loaded
     * from NotebookRepo.
     *
     * @return
     */
    public Note getRawNote() {
      return this.note;
    }

    public Folder getParent() {
      return parent;
    }

    @Override
    public String toString() {
      return getNotePath();
    }

    public void setParent(Folder parent) {
      this.parent = parent;
    }

    public void setNotePath(String notePath) {
      this.note.setPath(notePath);
    }

    /**
     * This is called when the ancestor folder is moved.
     */
    public void updateNotePath() {
      this.note.setPath(getNotePath());
    }
  }


}
