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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Notebook.NoteProcessor;
import org.apache.zeppelin.notebook.exception.NotePathAlreadyExistsException;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;

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
 */
@Singleton
public class NoteManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(NoteManager.class);
  public static final String TRASH_FOLDER = "~Trash";
  private Folder root;
  private Folder trash;

  private NotebookRepo notebookRepo;
  private NoteCache noteCache;
  // noteId -> notePath
  private Map<String, String> notesInfo;

  @Inject
  public NoteManager(NotebookRepo notebookRepo, ZeppelinConfiguration conf) throws IOException {
    this.notebookRepo = notebookRepo;
    this.noteCache = new NoteCache(conf.getNoteCacheThreshold());
    this.root = new Folder("/", notebookRepo, noteCache);
    this.trash = this.root.getOrCreateFolder(TRASH_FOLDER);
    init();
  }


  // build the tree structure of notes
  private void init() throws IOException {
    this.notesInfo = notebookRepo.list(AuthenticationInfo.ANONYMOUS).values().stream()
        .collect(Collectors.toConcurrentMap(NoteInfo::getId, NoteInfo::getPath));
    this.notesInfo.entrySet().stream()
        .forEach(entry ->
        {
          try {
            addOrUpdateNoteNode(new NoteInfo(entry.getKey(), entry.getValue()));
          } catch (IOException e) {
            LOGGER.warn(e.getMessage());
          }
        });
  }

  public Map<String, String> getNotesInfo() {
    return notesInfo;
  }


  /**
   *
   * @throws IOException
   */
  public void reloadNotes() throws IOException {
    this.root = new Folder("/", notebookRepo, noteCache);
    this.trash = this.root.getOrCreateFolder(TRASH_FOLDER);
    init();
  }

  /**
   *
   * @return current cache size
   */
  public int getCacheSize() {
    return this.noteCache.getSize();
  }

  private void addOrUpdateNoteNode(NoteInfo noteInfo, boolean checkDuplicates) throws IOException {
    String notePath = noteInfo.getPath();

    if (checkDuplicates && !isNotePathAvailable(notePath)) {
      throw new NotePathAlreadyExistsException("Note '" + notePath + "' existed");
    }

    String[] tokens = notePath.split("/");
    Folder curFolder = root;
    for (int i = 0; i < tokens.length - 1; ++i) {
      if (!StringUtils.isBlank(tokens[i])) {
        curFolder = curFolder.getOrCreateFolder(tokens[i]);
      }
    }

    curFolder.addNote(tokens[tokens.length -1], noteInfo);
    this.notesInfo.put(noteInfo.getId(), noteInfo.getPath());
  }

  private void addOrUpdateNoteNode(NoteInfo noteInfo) throws IOException {
    addOrUpdateNoteNode(noteInfo, false);
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
   * Only save note in loaded state. Unload state means its content is empty.
   *
   * @param note
   * @param subject
   * @throws IOException
   */
  public void saveNote(Note note, AuthenticationInfo subject) throws IOException {
    if (note.isRemoved()) {
      LOGGER.warn("Try to save note: {} when it is removed", note.getId());
    } else {
      addOrUpdateNoteNode(new NoteInfo(note));
      noteCache.putNote(note);
      // Make sure to execute `notebookRepo.save()` successfully in concurrent context
      // Otherwise, the NullPointerException will be thrown when invoking notebookRepo.get() in the following operations.
      synchronized (this) {
        this.notebookRepo.save(note, subject);
      }
    }
  }

  public void addNote(Note note, AuthenticationInfo subject) throws IOException {
    addOrUpdateNoteNode(new NoteInfo(note), true);
    noteCache.putNote(note);
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
    noteCache.removeNote(noteId);
    this.notebookRepo.remove(noteId, notePath, subject);
  }

  public void moveNote(String noteId,
                       String newNotePath,
                       AuthenticationInfo subject) throws IOException {
    if (noteId == null) {
      throw new IOException("No metadata found for this note: " + noteId);
    }

    if (!isNotePathAvailable(newNotePath)) {
      throw new NotePathAlreadyExistsException("Note '" + newNotePath + "' existed");
    }

    // move the old NoteNode from notePath to newNotePath
    String notePath = this.notesInfo.get(noteId);
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

    // Update path of the note
    if (!StringUtils.equalsIgnoreCase(notePath, newNotePath)) {
      processNote(noteId,
        note -> {
          note.setPath(newNotePath);
          return null;
        });
    }

    // save note if note name is changed, because we need to update the note field in note json.
    String oldNoteName = getNoteName(notePath);
    String newNoteName = getNoteName(newNotePath);
    if (!StringUtils.equalsIgnoreCase(oldNoteName, newNoteName)) {
      processNote(noteId,
        note -> {
          this.notebookRepo.save(note, subject);
          return null;
        });
    }
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
    for (NoteInfo noteInfo : folder.getNoteInfoRecursively()) {
      notesInfo.put(noteInfo.getId(), noteInfo.getPath());
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
  public List<NoteInfo> removeFolder(String folderPath, AuthenticationInfo subject) throws IOException {

    // update notebookrepo
    this.notebookRepo.remove(folderPath, subject);

    // update filesystem tree
    Folder folder = getFolder(folderPath);
    List<NoteInfo> noteInfos = folder.getParent().removeFolder(folder.getName(), subject);

    // update notesInfo
    for (NoteInfo noteInfo : noteInfos) {
      this.notesInfo.remove(noteInfo.getId());
    }

    return noteInfos;
  }

  /**
   * Process note from NotebookRepo in an eviction aware manner.
   *
   * @param noteId
   * @param reload
   * @param noteProcessor
   * @return result of the noteProcessor
   * @throws IOException
   */
  public <T> T processNote(String noteId, boolean reload, NoteProcessor<T> noteProcessor)
      throws IOException {
    if (this.notesInfo == null || noteId == null || !this.notesInfo.containsKey(noteId)) {
      return noteProcessor.process(null);
    }
    String notePath = this.notesInfo.get(noteId);
    NoteNode noteNode = getNoteNode(notePath);
    return noteNode.loadAndProcessNote(reload, noteProcessor);
  }

  /**
   * Process note from NotebookRepo in an eviction aware manner.
   *
   * @param noteId
   * @param noteProcessor
   * @return result of the noteProcessor
   * @throws IOException
   */
  public <T> T processNote(String noteId, NoteProcessor<T> noteProcessor) throws IOException {
    return processNote(noteId, false, noteProcessor);
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
    int pos = notePath.lastIndexOf('/');
    return notePath.substring(0, pos);
  }

  private String getNoteName(String notePath) {
    int pos = notePath.lastIndexOf('/');
    return notePath.substring(pos + 1);
  }

  private boolean isNotePathAvailable(String notePath) {
    String[] tokens = notePath.split("/");
    Folder curFolder = root;
    for (int i = 0; i < tokens.length - 1; ++i) {
      if (!StringUtils.isBlank(tokens[i])) {
        curFolder = curFolder.getFolder(tokens[i]);
        if (curFolder == null) {
          return true;
        }
      }
    }
    if (curFolder.containsNote(tokens[tokens.length - 1])) {
      return false;
    }

    return true;
  }

  public String getNoteIdByPath(String notePath) throws IOException {
    NoteNode noteNode = getNoteNode(notePath);
    return noteNode.getNoteId();
  }

  /**
   * Represent one folder that could contains sub folders and note files.
   */
  public static class Folder {

    private String name;
    private Folder parent;
    private NotebookRepo notebookRepo;
    private NoteCache noteCache;

    // noteName -> NoteNode
    private Map<String, NoteNode> notes = new ConcurrentHashMap<>();
    // folderName -> Folder
    private Map<String, Folder> subFolders = new ConcurrentHashMap<>();

    public Folder(String name, NotebookRepo notebookRepo, NoteCache noteCache) {
      this.name = name;
      this.notebookRepo = notebookRepo;
      this.noteCache = noteCache;
    }

    public Folder(String name, Folder parent, NotebookRepo notebookRepo, NoteCache noteCache) {
      this(name, notebookRepo, noteCache);
      this.parent = parent;
    }

    public synchronized Folder getOrCreateFolder(String folderName) {
      if (StringUtils.isBlank(folderName)) {
        return this;
      }
      if (!subFolders.containsKey(folderName)) {
        subFolders.put(folderName, new Folder(folderName, this, notebookRepo, noteCache));
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

    public void addNote(String noteName, NoteInfo noteInfo) {
      notes.put(noteName, new NoteNode(noteInfo, this, notebookRepo, noteCache));
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

    public List<NoteInfo> removeFolder(String folderName,
                                   AuthenticationInfo subject) throws IOException {
      Folder folder = this.subFolders.remove(folderName);
      return folder.getNoteInfoRecursively();
    }

    public List<NoteInfo> getNoteInfoRecursively() {
      List<NoteInfo> notesInfo = new ArrayList<>();
      for (NoteNode noteNode : this.notes.values()) {
        notesInfo.add(noteNode.getNoteInfo());
      }
      for (Folder folder : subFolders.values()) {
        notesInfo.addAll(folder.getNoteInfoRecursively());
      }
      return notesInfo;
    }

    public List<NoteNode> getNoteNodeRecursively() {
      List<NoteNode> noteNodeRecursively = new ArrayList<>();
      noteNodeRecursively.addAll(this.notes.values());
      for (Folder folder : subFolders.values()) {
        noteNodeRecursively.addAll(folder.getNoteNodeRecursively());
      }
      return noteNodeRecursively;
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
   * <br>
   * It will load note from NotebookRepo lazily until method getNote is called.
   * A NoteCache ensures to free up resources, because its size is limited.
   */
  public static class NoteNode {

    private Folder parent;
    private NoteInfo noteInfo;
    private NotebookRepo notebookRepo;
    private NoteCache noteCache;

    public NoteNode(NoteInfo noteInfo, Folder parent, NotebookRepo notebookRepo, NoteCache noteCache) {
      this.noteInfo = noteInfo;
      this.parent = parent;
      this.notebookRepo = notebookRepo;
      this.noteCache = noteCache;
    }

    /**
     * This method will process note in a eviction aware manner by loading it from NotebookRepo.
     *
     * If you just want to get noteId, noteName or
     * notePath, you can call method getNoteId, getNoteName & getNotePath
     *
     * @param reload force a reload from {@link NotebookRepo}
     * @param noteProcessor callback
     * @return result of the noteProcessor
     * @throws IOException
     */
    public <T> T loadAndProcessNote(boolean reload, NoteProcessor<T> noteProcessor)
        throws IOException {
      // load note
      Note note;
      synchronized (this) {
        note = noteCache.getNote(noteInfo.getId());
        if (note == null || reload) {
          note = notebookRepo.get(noteInfo.getId(), noteInfo.getPath(), AuthenticationInfo.ANONYMOUS);
          if (parent.toString().equals("/")) {
            note.setPath("/" + note.getName());
          } else {
            note.setPath(parent.toString() + "/" + note.getName());
          }
          note.setCronSupported(ZeppelinConfiguration.create());
          noteCache.putNote(note);
        }
      }
      try {
        note.getLock().readLock().lock();
        // process note
        return noteProcessor.process(note);
      } finally {
        note.getLock().readLock().unlock();
      }
    }

    public String getNoteId() {
      return this.noteInfo.getId();
    }

    public String getNoteName() {
      return this.noteInfo.getNoteName();
    }

    public String getNotePath() {
      if (parent.getPath().equals("/")) {
        return parent.getPath() + noteInfo.getNoteName();
      } else {
        return parent.getPath() + "/" + noteInfo.getNoteName();
      }
    }

    public NoteInfo getNoteInfo() {
      return this.noteInfo;
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
      this.noteInfo.setPath(notePath);
    }

    /**
     * This is called when the ancestor folder is moved.
     */
    public void updateNotePath() {
      this.noteInfo.setPath(getNotePath());
    }
  }

  /**
   * Leverage a simple LRU cache for notes.
   * Notes are not evicted in case they are currently in use (have a lock).
   */
  private static class NoteCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoteCache.class);

    private final int threshold;
    private final Map<String, Note> lruCache;
    private final Counter cacheHit;
    private final Counter cacheMiss;

    public NoteCache(final int threshold) {
      // Registering the threshold to compare the configured threshold with the actual note cache
      this.threshold = Metrics.gauge("zeppelin_note_cache_threshold", Tags.empty(), threshold);
      // use a synchronized map to make the NoteCache thread-safe
      this.lruCache = Metrics.gaugeMapSize("zeppelin_note_cache", Tags.empty(), Collections.synchronizedMap(new LRUCache()));
      this.cacheHit = Metrics.counter("zeppelin_note_cache_hit", Tags.empty());
      this.cacheMiss = Metrics.counter("zeppelin_note_cache_miss", Tags.empty());
    }

    public int getSize() {
      return lruCache.size();
    }

    public Note getNote(String noteId) {
      Note note = lruCache.get(noteId);
      if (note != null) {
        cacheHit.increment();
      } else {
        cacheMiss.increment();
      }
      return note;
    }

    public void putNote(Note note) {
      lruCache.put(note.getId(), note);
    }

    public Note removeNote(String noteId) {
      return lruCache.remove(noteId);
    }

    private class LRUCache extends LinkedHashMap<String, Note> {

      private static final long serialVersionUID = 1L;

      public LRUCache() {
        super(NoteCache.this.threshold, 0.5f, true /* lru by access mode */);
      }

      @Override
      protected boolean removeEldestEntry(java.util.Map.Entry<String, Note> eldest) {
        if (size() <= NoteCache.this.threshold) {
          return false;
        }

        final Note eldestNote = eldest.getValue();
        final Lock lock = eldestNote.getLock().writeLock();
        if (lock.tryLock()) { // avoid eviction in case the note is in use
          try {
            return true;
          } finally {
            lock.unlock();
          }
        } else {
          LOGGER.info("Can not evict note {}, because the write lock can not be acquired. {} notes currently loaded.",
              eldestNote.getId(), size());
          return false;
        }
      }
    }

  }



}
