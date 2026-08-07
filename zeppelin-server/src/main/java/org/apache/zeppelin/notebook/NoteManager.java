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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Notebook.NoteProcessor;
import org.apache.zeppelin.notebook.exception.NotePathAlreadyExistsException;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithVersionControl;
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
  private NotebookRepo notebookRepo;
  private NoteCache noteCache;
  private final ZeppelinConfiguration zConf;

  /**
   * The folder tree and the noteId -> notePath mapping. They are held together in one
   * immutable reference so that a reload publishes both at once and concurrent note
   * operations never observe a tree and a mapping that belong to different generations.
   */
  private volatile NoteTree noteTree;
  private long metadataVersion;
  private volatile Throwable metadataUnavailableCause;

  @Inject
  public NoteManager(NotebookRepo notebookRepo, ZeppelinConfiguration zConf) throws IOException {
    this.zConf = zConf;
    this.notebookRepo = notebookRepo;
    this.noteCache = new NoteCache(zConf.getNoteCacheThreshold());
    this.noteTree = buildNoteTree();
  }


  /**
   * Build the tree structure of notes from the NotebookRepo. The tree is fully populated
   * before it is returned, and it is not reachable by other threads until the caller
   * publishes it to {@link #noteTree}.
   */
  private NoteTree buildNoteTree() throws IOException {
    Folder newRoot = new Folder("/", notebookRepo, noteCache, zConf);
    Folder newTrash = newRoot.getOrCreateFolder(TRASH_FOLDER);
    Map<String, String> newNotesInfo =
        notebookRepo.list(AuthenticationInfo.ANONYMOUS).values().stream()
            .collect(Collectors.toConcurrentMap(NoteInfo::getId, NoteInfo::getPath));
    NoteTree newNoteTree = new NoteTree(newRoot, newTrash, newNotesInfo);
    for (Map.Entry<String, String> entry : newNotesInfo.entrySet()) {
      try {
        addOrUpdateNoteNode(newNoteTree, new NoteInfo(entry.getKey(), entry.getValue()), false);
      } catch (IOException e) {
        LOGGER.warn(e.getMessage());
      }
    }
    return newNoteTree;
  }

  public Map<String, String> getNotesInfo() {
    assertMetadataAvailableUnchecked();
    return this.noteTree.notesInfo;
  }

  /** Capture one immutable generation of the note-id/path index for authorization preflight. */
  public synchronized NoteMetadataSnapshot getNotesInfoSnapshot() throws IOException {
    assertMetadataAvailable();
    return new NoteMetadataSnapshot(
        metadataVersion,
        Collections.unmodifiableMap(new LinkedHashMap<>(noteTree.notesInfo)));
  }


  /**
   * Rebuild the notebook metadata from the NotebookRepo. The new tree is built completely
   * before it replaces the current one, so a concurrent note operation sees either the
   * previous tree or the new one, never a partially rebuilt tree.
   *
   * @throws IOException
   */
  public synchronized void reloadNotes() throws IOException {
    NoteTree reloadedTree = buildNoteTree();
    this.noteTree = reloadedTree;
    metadataUnavailableCause = null;
    metadataVersion++;
  }

  /**
   *
   * @return current cache size
   */
  public int getCacheSize() {
    return this.noteCache.getSize();
  }

  private void addOrUpdateNoteNode(NoteTree tree, NoteInfo noteInfo, boolean checkDuplicates)
      throws IOException {
    String notePath = noteInfo.getPath();

    if (checkDuplicates && !isNotePathAvailable(tree, notePath)) {
      throw new NotePathAlreadyExistsException("Note '" + notePath + "' existed");
    }

    String[] tokens = notePath.split("/");
    Folder curFolder = tree.root;
    for (int i = 0; i < tokens.length - 1; ++i) {
      if (!StringUtils.isBlank(tokens[i])) {
        curFolder = curFolder.getOrCreateFolder(tokens[i]);
      }
    }

    curFolder.addNote(tokens[tokens.length -1], noteInfo);
    tree.notesInfo.put(noteInfo.getId(), noteInfo.getPath());
  }

  /**
   * Check whether there exist note under this notePath.
   *
   * @param notePath
   * @return
   */
  public boolean containsNote(String notePath) {
    assertMetadataAvailableUnchecked();
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
    assertMetadataAvailableUnchecked();
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
  public synchronized void saveNote(Note note, AuthenticationInfo subject) throws IOException {
    assertMetadataAvailable();
    if (note.isRemoved()) {
      LOGGER.warn("Try to save note: {} when it is removed", note.getId());
    } else {
      // Make sure to execute `notebookRepo.save()` successfully in concurrent context
      // Otherwise, the NullPointerException will be thrown when invoking notebookRepo.get() in the following operations.
      String previousPath = noteTree.notesInfo.get(note.getId());
      addOrUpdateNoteNode(this.noteTree, new NoteInfo(note), false);
      noteCache.putNote(note);
      if (!StringUtils.equals(previousPath, note.getPath())) {
        metadataVersion++;
      }
      this.notebookRepo.save(note, subject);
    }
  }

  public synchronized void addNote(Note note, AuthenticationInfo subject) throws IOException {
    assertMetadataAvailable();
    addOrUpdateNoteNode(this.noteTree, new NoteInfo(note), true);
    noteCache.putNote(note);
    metadataVersion++;
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
   * Restore a note revision and publish it to the note cache and path index atomically with
   * respect to note and folder moves.
   */
  public synchronized Note setNoteRevision(
      String noteId,
      String notePath,
      String revisionId,
      AuthenticationInfo subject) throws IOException {
    assertMetadataAvailable();
    String currentPath = noteTree.notesInfo.get(noteId);
    if (currentPath == null) {
      throw new IOException("No metadata found for this note: " + noteId);
    }
    if (!StringUtils.equals(currentPath, notePath)) {
      throw new IOException("Note path changed while setting the revision");
    }

    Note note = ((NotebookRepoWithVersionControl) notebookRepo)
        .setNoteRevision(noteId, notePath, revisionId, subject);
    if (note != null) {
      saveNote(note, subject);
    }
    return note;
  }

  /**
   * Remove note from NotebookRepo and NoteManager
   *
   * @param noteId
   * @param subject
   * @throws IOException
   */
  public synchronized void removeNote(String noteId, AuthenticationInfo subject) throws IOException {
    assertMetadataAvailable();
    NoteTree tree = this.noteTree;
    String notePath = tree.notesInfo.remove(noteId);
    Folder folder = getOrCreateFolder(tree, getFolderName(notePath));
    folder.removeNote(getNoteName(notePath));
    noteCache.removeNote(noteId);
    metadataVersion++;
    this.notebookRepo.remove(noteId, notePath, subject);
  }

  public void moveNote(String noteId,
                       String newNotePath,
                       AuthenticationInfo subject) throws IOException {
    if (noteId == null) {
      throw new IOException("No metadata found for this note: " + noteId);
    }

    String notePath;
    synchronized (this) {
      assertMetadataAvailable();
      NoteTree tree = this.noteTree;
      if (!isNotePathAvailable(tree, newNotePath)) {
        throw new NotePathAlreadyExistsException("Note '" + newNotePath + "' existed");
      }

      notePath = tree.notesInfo.get(noteId);
      NoteNode noteNode = getNoteNode(tree, notePath);

      // Move durable state first. If the repository rejects the destination, the in-memory
      // path index and cached note must remain on the source path.
      this.notebookRepo.move(noteId, notePath, newNotePath, subject);

      // move the old NoteNode from notePath to newNotePath
      noteNode.getParent().removeNote(getNoteName(notePath));
      noteNode.setNotePath(newNotePath);
      String newParent = getFolderName(newNotePath);
      Folder newFolder = getOrCreateFolder(tree, newParent);
      newFolder.addNoteNode(noteNode);

      // update noteInfo mapping
      tree.notesInfo.put(noteId, newNotePath);
      updateCachedNotePath(noteId, newNotePath);
      metadataVersion++;

      // The cache may evict the note while many notes are moved concurrently. Reload it through
      // the new metadata path so the repository-backed object also receives the updated path.
      if (!StringUtils.equals(notePath, newNotePath)) {
        processNote(noteId,
          note -> {
            note.setPath(newNotePath);
            return null;
          });
      }

      // save note if note name is changed, because we need to update the note field in note json.
      String oldNoteName = getNoteName(notePath);
      String newNoteName = getNoteName(newNotePath);
      if (!StringUtils.equals(oldNoteName, newNoteName)) {
        processNote(noteId,
          note -> {
            this.notebookRepo.save(note, subject);
            return null;
          });
      }
    }
  }

  public synchronized void moveFolder(String folderPath,
                                      String newFolderPath,
                                      AuthenticationInfo subject) throws IOException {
    moveFolder(folderPath, newFolderPath, subject, -1);
  }

  public synchronized void moveFolder(
      String folderPath,
      String newFolderPath,
      AuthenticationInfo subject,
      long expectedMetadataVersion) throws IOException {
    moveFolder(folderPath, newFolderPath, subject, expectedMetadataVersion, true);
  }

  public synchronized void moveFolder(
      String folderPath,
      String newFolderPath,
      AuthenticationInfo subject,
      long expectedMetadataVersion,
      boolean mergeExistingDestination) throws IOException {

    assertMetadataVersion(expectedMetadataVersion);

    NoteTree tree = this.noteTree;
    Folder folder = getFolder(tree, folderPath);
    String sourceFolderPath = folder.getPath();
    String destinationFolderPath = normalizeFolderPath(newFolderPath);
    if (StringUtils.equals(sourceFolderPath, destinationFolderPath)) {
      return;
    }
    if (folder == tree.root) {
      throw new IOException("Can not move the root folder");
    }
    if (destinationFolderPath.startsWith(sourceFolderPath + "/")) {
      throw new IOException(
          "Can not move folder '" + sourceFolderPath + "' into its own descendant");
    }
    if (containsNote(destinationFolderPath)) {
      throw new NotePathAlreadyExistsException(
          "Path '" + destinationFolderPath + "' existed");
    }

    if (containsFolder(destinationFolderPath)) {
      if (!mergeExistingDestination) {
        throw new NotePathAlreadyExistsException(
            "Path '" + destinationFolderPath + "' existed");
      }
      Folder destinationFolder = getFolder(tree, destinationFolderPath);
      if (isSameOrDescendantFolder(destinationFolder, folder)) {
        throw new IOException(
            "Can not move folder '" + sourceFolderPath + "' into its own descendant");
      }
      mergeFolder(tree, folder, destinationFolder, subject);
      return;
    }

    // update notebookrepo
    this.notebookRepo.move(sourceFolderPath, destinationFolderPath, subject);

    // update filesystem tree
    folder.getParent().removeFolder(folder.getName(), subject);
    Folder newFolder = getOrCreateFolder(tree, destinationFolderPath);
    newFolder.getParent().addFolder(newFolder.getName(), folder);

    // update notesInfo
    for (NoteInfo noteInfo : folder.getNoteInfoRecursively()) {
      tree.notesInfo.put(noteInfo.getId(), noteInfo.getPath());
      updateCachedNotePath(noteInfo.getId(), noteInfo.getPath());
    }
    metadataVersion++;
  }

  private static String normalizeFolderPath(String folderPath) {
    StringBuilder normalized = new StringBuilder();
    for (String token : folderPath.split("/")) {
      if (!StringUtils.isBlank(token)) {
        normalized.append('/').append(token);
      }
    }
    return normalized.length() == 0 ? "/" : normalized.toString();
  }

  private static boolean isSameOrDescendantFolder(Folder folder, Folder possibleAncestor) {
    for (Folder current = folder; current != null; current = current.parent) {
      if (current == possibleAncestor) {
        return true;
      }
    }
    return false;
  }

  private void mergeFolder(
      NoteTree tree,
      Folder sourceFolder,
      Folder destinationFolder,
      AuthenticationInfo subject) throws IOException {
    String sourceFolderPath = sourceFolder.getPath();
    String destinationFolderPath = destinationFolder.getPath();
    List<FolderNoteMove> noteMoves = new ArrayList<>();
    for (NoteInfo noteInfo : sourceFolder.getNoteInfoRecursively()) {
      noteMoves.add(
          new FolderNoteMove(
              noteInfo.getId(),
              noteInfo.getPath(),
              rebasePath(noteInfo.getPath(), sourceFolderPath, destinationFolderPath)));
    }
    noteMoves.sort((first, second) -> first.sourcePath.compareTo(second.sourcePath));

    assertFolderMergeDoesNotOverwrite(tree, sourceFolder, noteMoves, destinationFolderPath);
    moveFolderNotesWithRollback(noteMoves, subject);

    // Publish the in-memory change only after every durable note move succeeds.
    sourceFolder.getParent().getFolders().remove(sourceFolder.getName());
    mergeFolderTrees(sourceFolder, destinationFolder);
    for (FolderNoteMove noteMove : noteMoves) {
      tree.notesInfo.put(noteMove.noteId, noteMove.destinationPath);
      updateCachedNotePath(noteMove.noteId, noteMove.destinationPath);
    }
    metadataVersion++;
  }

  private void assertFolderMergeDoesNotOverwrite(
      NoteTree tree,
      Folder sourceFolder,
      List<FolderNoteMove> noteMoves,
      String destinationFolderPath) throws IOException {
    Set<String> sourceNoteIds = new HashSet<>();
    for (FolderNoteMove noteMove : noteMoves) {
      sourceNoteIds.add(noteMove.noteId);
    }
    Set<String> remainingNotePaths = new HashSet<>();
    for (Map.Entry<String, String> entry : tree.notesInfo.entrySet()) {
      if (!sourceNoteIds.contains(entry.getKey())) {
        remainingNotePaths.add(entry.getValue());
      }
    }

    Set<String> remainingFolderPaths = new HashSet<>();
    collectFolderPathsExcept(tree.root, sourceFolder, remainingFolderPaths);

    Set<String> destinationFolderPaths = new HashSet<>();
    collectRebasedFolderPaths(
        sourceFolder, sourceFolder.getPath(), destinationFolderPath, destinationFolderPaths);
    for (String folderPath : destinationFolderPaths) {
      if (remainingNotePaths.contains(folderPath)) {
        throw new NotePathAlreadyExistsException("Path '" + folderPath + "' existed");
      }
    }

    Set<String> destinationNotePaths = new HashSet<>();
    for (FolderNoteMove noteMove : noteMoves) {
      if (!destinationNotePaths.add(noteMove.destinationPath)
          || remainingNotePaths.contains(noteMove.destinationPath)
          || remainingFolderPaths.contains(noteMove.destinationPath)
          || destinationFolderPaths.contains(noteMove.destinationPath)) {
        throw new NotePathAlreadyExistsException(
            "Path '" + noteMove.destinationPath + "' existed");
      }
    }
  }

  private static void collectFolderPathsExcept(
      Folder folder, Folder excludedFolder, Set<String> folderPaths) {
    if (folder == excludedFolder) {
      return;
    }
    folderPaths.add(folder.getPath());
    for (Folder child : folder.getFolders().values()) {
      collectFolderPathsExcept(child, excludedFolder, folderPaths);
    }
  }

  private static void collectRebasedFolderPaths(
      Folder folder,
      String sourceFolderPath,
      String destinationFolderPath,
      Set<String> folderPaths) {
    folderPaths.add(rebasePath(folder.getPath(), sourceFolderPath, destinationFolderPath));
    for (Folder child : folder.getFolders().values()) {
      collectRebasedFolderPaths(
          child, sourceFolderPath, destinationFolderPath, folderPaths);
    }
  }

  private static String rebasePath(
      String path, String sourceFolderPath, String destinationFolderPath) {
    String relativePath = path.substring(sourceFolderPath.length());
    if ("/".equals(destinationFolderPath)) {
      return relativePath.isEmpty() ? "/" : relativePath;
    }
    return destinationFolderPath + relativePath;
  }

  private void moveFolderNotesWithRollback(
      List<FolderNoteMove> noteMoves, AuthenticationInfo subject) throws IOException {
    List<FolderNoteMove> attemptedMoves = new ArrayList<>();
    try {
      for (FolderNoteMove noteMove : noteMoves) {
        // A repository move may copy or update part of its state before reporting failure.
        // Record the attempt first so compensation also covers that ambiguous current move.
        attemptedMoves.add(noteMove);
        notebookRepo.move(
            noteMove.noteId, noteMove.sourcePath, noteMove.destinationPath, subject);
      }
    } catch (IOException | RuntimeException failure) {
      boolean rollbackFailed = false;
      for (int i = attemptedMoves.size() - 1; i >= 0; i--) {
        FolderNoteMove attemptedMove = attemptedMoves.get(i);
        try {
          notebookRepo.move(
              attemptedMove.noteId,
              attemptedMove.destinationPath,
              attemptedMove.sourcePath,
              subject);
        } catch (IOException | RuntimeException rollbackFailure) {
          failure.addSuppressed(rollbackFailure);
          rollbackFailed = true;
        }
      }

      if (rollbackFailed) {
        // A failed compensation means the durable paths are no longer known. Poison metadata
        // before attempting a reload so lock-free readers cannot use the stale tree meanwhile.
        metadataUnavailableCause = failure;
        for (FolderNoteMove noteMove : noteMoves) {
          noteCache.removeNote(noteMove.noteId);
        }
        try {
          reloadNotes();
        } catch (IOException | RuntimeException reloadFailure) {
          failure.addSuppressed(reloadFailure);
        }
      }
      throw failure;
    }
  }

  private static void mergeFolderTrees(Folder sourceFolder, Folder destinationFolder) {
    for (Map.Entry<String, NoteNode> entry : sourceFolder.getNotes().entrySet()) {
      NoteNode noteNode = entry.getValue();
      destinationFolder.getNotes().put(entry.getKey(), noteNode);
      noteNode.setParent(destinationFolder);
      noteNode.updateNotePath();
    }

    for (Map.Entry<String, Folder> entry : sourceFolder.getFolders().entrySet()) {
      Folder sourceChild = entry.getValue();
      Folder destinationChild = destinationFolder.getFolder(entry.getKey());
      if (destinationChild == null) {
        destinationFolder.getFolders().put(entry.getKey(), sourceChild);
        sourceChild.setParent(destinationFolder);
        for (NoteNode noteNode : sourceChild.getNoteNodeRecursively()) {
          noteNode.updateNotePath();
        }
      } else {
        mergeFolderTrees(sourceChild, destinationChild);
      }
    }
  }

  /**
   * Returns the NoteInfo of all notes under the given folder, without removing them.
   *
   * @param folderPath
   * @return
   * @throws IOException
   */
  public List<NoteInfo> getNoteInfoRecursively(String folderPath) throws IOException {
    assertMetadataAvailable();
    return getFolder(folderPath).getNoteInfoRecursively();
  }

  /**
   * Remove the folder from the tree and returns the affected NoteInfo under this folder.
   *
   * @param folderPath
   * @param subject
   * @return
   * @throws IOException
   */
  public synchronized List<NoteInfo> removeFolder(
      String folderPath, AuthenticationInfo subject) throws IOException {
    return removeFolder(folderPath, subject, -1);
  }

  public synchronized List<NoteInfo> removeFolder(
      String folderPath,
      AuthenticationInfo subject,
      long expectedMetadataVersion) throws IOException {

    return removeFolder(
        folderPath, subject, expectedMetadataVersion, Collections.emptyList());
  }

  synchronized List<NoteInfo> removeFolder(
      String folderPath,
      AuthenticationInfo subject,
      long expectedMetadataVersion,
      List<Note> loadedNotes) throws IOException {

    assertMetadataVersion(expectedMetadataVersion);

    List<Note> newlyRemovedNotes = new ArrayList<>();
    for (Note note : loadedNotes) {
      if (!note.isRemoved()) {
        note.setRemoved(true);
        newlyRemovedNotes.add(note);
      }
    }

    try {
      // update notebookrepo
      this.notebookRepo.remove(folderPath, subject);

      // update filesystem tree
      NoteTree tree = this.noteTree;
      Folder folder = getFolder(tree, folderPath);
      List<NoteInfo> noteInfos = folder.getNoteInfoRecursively();
      if (folder == tree.trash) {
        folder.clear();
      } else {
        folder.getParent().removeFolder(folder.getName(), subject);
      }

      // update notesInfo and evict the deleted notes from the cache, mirroring removeNote
      for (NoteInfo noteInfo : noteInfos) {
        tree.notesInfo.remove(noteInfo.getId());
        this.noteCache.removeNote(noteInfo.getId());
      }
      metadataVersion++;

      return noteInfos;
    } catch (IOException | RuntimeException e) {
      for (Note note : newlyRemovedNotes) {
        note.setRemoved(false);
      }
      throw e;
    }
  }

  /**
   * Restore every direct child of the trash against one authorized metadata generation.
   * Structural changes are blocked for the full preflight and move sequence so a note cannot
   * be added to the authorized folder after its ACL was checked.
   *
   * @return note-id to restored path for callers that need to report the restored entries
   */
  public synchronized Map<String, String> restoreAllFromTrash(
      AuthenticationInfo subject, long expectedMetadataVersion) throws IOException {
    assertMetadataVersion(expectedMetadataVersion);

    NoteTree tree = this.noteTree;
    List<NoteNode> notes = new ArrayList<>(tree.trash.getNotes().values());
    List<Folder> folders = new ArrayList<>(tree.trash.getFolders().values());
    Map<String, String> restoredPaths = new LinkedHashMap<>();
    Map<String, Boolean> destinations = new LinkedHashMap<>();
    String trashPrefix = "/" + TRASH_FOLDER;

    for (NoteNode noteNode : notes) {
      String destination = noteNode.getNotePath().substring(trashPrefix.length());
      checkRestoreDestination(destination, destinations);
    }
    for (Folder folder : folders) {
      String destination = folder.getPath().substring(trashPrefix.length());
      checkRestoreDestination(destination, destinations);
    }

    boolean mutated = false;
    try {
      for (NoteNode noteNode : notes) {
        String noteId = noteNode.getNoteId();
        String oldPath = noteNode.getNotePath();
        String newPath = oldPath.substring(trashPrefix.length());
        notebookRepo.move(noteId, oldPath, newPath, subject);
        noteNode.getParent().removeNote(getNoteName(oldPath));
        noteNode.setNotePath(newPath);
        getOrCreateFolder(tree, getFolderName(newPath)).addNoteNode(noteNode);
        tree.notesInfo.put(noteId, newPath);
        updateCachedNotePath(noteId, newPath);
        restoredPaths.put(noteId, newPath);
        mutated = true;
      }
      for (Folder folder : folders) {
        String oldPath = folder.getPath();
        String newPath = oldPath.substring(trashPrefix.length());
        notebookRepo.move(oldPath, newPath, subject);
        folder.getParent().removeFolder(folder.getName(), subject);
        Folder destination = getOrCreateFolder(tree, newPath);
        destination.getParent().addFolder(destination.getName(), folder);
        for (NoteInfo noteInfo : folder.getNoteInfoRecursively()) {
          tree.notesInfo.put(noteInfo.getId(), noteInfo.getPath());
          updateCachedNotePath(noteInfo.getId(), noteInfo.getPath());
          restoredPaths.put(noteInfo.getId(), noteInfo.getPath());
        }
        mutated = true;
      }
    } finally {
      if (mutated) {
        metadataVersion++;
      }
    }
    return restoredPaths;
  }

  private void checkRestoreDestination(
      String destination, Map<String, Boolean> destinations) throws IOException {
    if (destinations.put(destination, Boolean.TRUE) != null
        || containsNote(destination)
        || containsFolder(destination)) {
      throw new NotePathAlreadyExistsException("Path '" + destination + "' existed");
    }
  }

  private void assertMetadataVersion(long expectedMetadataVersion) throws IOException {
    assertMetadataAvailable();
    if (expectedMetadataVersion >= 0 && metadataVersion != expectedMetadataVersion) {
      throw new IOException("Notebook metadata changed while authorizing the folder operation");
    }
  }

  private void assertMetadataAvailable() throws IOException {
    Throwable cause = metadataUnavailableCause;
    if (cause != null) {
      throw new IOException(
          "Notebook metadata is unavailable after repository recovery failed", cause);
    }
  }

  private void assertMetadataAvailableUnchecked() {
    Throwable cause = metadataUnavailableCause;
    if (cause != null) {
      throw new IllegalStateException(
          "Notebook metadata is unavailable after repository recovery failed", cause);
    }
  }

  private void updateCachedNotePath(String noteId, String notePath) {
    Note note = noteCache.getNote(noteId);
    if (note != null) {
      note.setPath(notePath);
    }
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
    assertMetadataAvailable();
    // Read the tree once, so that the mapping lookup below and the tree traversal that
    // follows it are both resolved against the same generation of the metadata.
    NoteTree tree = this.noteTree;
    if (tree == null || noteId == null || !tree.notesInfo.containsKey(noteId)) {
      return noteProcessor.process(null);
    }
    String notePath = tree.notesInfo.get(noteId);
    NoteNode noteNode = getNoteNode(tree, notePath);
    if (!StringUtils.equals(noteId, noteNode.getNoteId())) {
      throw new IOException("Note metadata changed while resolving note: " + noteId);
    }
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
    assertMetadataAvailableUnchecked();
    return getOrCreateFolder(this.noteTree, folderName);
  }

  private static Folder getOrCreateFolder(NoteTree tree, String folderName) {
    String[] tokens = folderName.split("/");
    Folder curFolder = tree.root;
    for (int i = 0; i < tokens.length; ++i) {
      if (!StringUtils.isBlank(tokens[i])) {
        curFolder = curFolder.getOrCreateFolder(tokens[i]);
      }
    }
    return curFolder;
  }

  private NoteNode getNoteNode(String notePath) throws IOException {
    return getNoteNode(this.noteTree, notePath);
  }

  private static NoteNode getNoteNode(NoteTree tree, String notePath) throws IOException {
    String[] tokens = notePath.split("/");
    if (tokens.length == 0) {
      throw new IOException("Can not find note: " + notePath);
    }
    Folder curFolder = tree.root;
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
    return getFolder(this.noteTree, folderPath);
  }

  private static Folder getFolder(NoteTree tree, String folderPath) throws IOException {
    String[] tokens = folderPath.split("/");
    Folder curFolder = tree.root;
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
    assertMetadataAvailableUnchecked();
    return this.noteTree.trash;
  }

  private String getFolderName(String notePath) {
    int pos = notePath.lastIndexOf('/');
    return notePath.substring(0, pos);
  }

  private String getNoteName(String notePath) {
    int pos = notePath.lastIndexOf('/');
    return notePath.substring(pos + 1);
  }

  private static boolean isNotePathAvailable(NoteTree tree, String notePath) {
    String[] tokens = notePath.split("/");
    Folder curFolder = tree.root;
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
    assertMetadataAvailable();
    NoteNode noteNode = getNoteNode(notePath);
    return noteNode.getNoteId();
  }

  /** Immutable note metadata generation used to bind authorization to a later mutation. */
  public static final class NoteMetadataSnapshot {
    private final long version;
    private final Map<String, String> notesInfo;

    NoteMetadataSnapshot(long version, Map<String, String> notesInfo) {
      this.version = version;
      this.notesInfo = notesInfo;
    }

    public long getVersion() {
      return version;
    }

    public Map<String, String> getNotesInfo() {
      return notesInfo;
    }
  }

  /**
   * The two indexes that together locate a note: the folder tree and the noteId -> notePath
   * mapping. A note lookup resolves the id through the mapping and then walks the tree, so
   * the two must belong to the same generation. Holding them in one immutable reference lets
   * a reload replace both of them in a single assignment.
   */
  private static class NoteTree {
    private final Folder root;
    private final Folder trash;
    // noteId -> notePath
    private final Map<String, String> notesInfo;

    NoteTree(Folder root, Folder trash, Map<String, String> notesInfo) {
      this.root = root;
      this.trash = trash;
      this.notesInfo = notesInfo;
    }
  }

  private static final class FolderNoteMove {
    private final String noteId;
    private final String sourcePath;
    private final String destinationPath;

    private FolderNoteMove(String noteId, String sourcePath, String destinationPath) {
      this.noteId = noteId;
      this.sourcePath = sourcePath;
      this.destinationPath = destinationPath;
    }
  }

  /**
   * Represent one folder that could contains sub folders and note files.
   */
  public static class Folder {

    private String name;
    private Folder parent;
    private NotebookRepo notebookRepo;
    private NoteCache noteCache;
    private final ZeppelinConfiguration zConf;

    // noteName -> NoteNode
    private Map<String, NoteNode> notes = new ConcurrentHashMap<>();
    // folderName -> Folder
    private Map<String, Folder> subFolders = new ConcurrentHashMap<>();

    public Folder(String name, NotebookRepo notebookRepo, NoteCache noteCache,
        ZeppelinConfiguration zConf) {
      this.name = name;
      this.zConf = zConf;
      this.notebookRepo = notebookRepo;
      this.noteCache = noteCache;
    }

    public Folder(String name, Folder parent, NotebookRepo notebookRepo, NoteCache noteCache,
        ZeppelinConfiguration zConf) {
      this(name, notebookRepo, noteCache, zConf);
      this.parent = parent;
    }

    public synchronized Folder getOrCreateFolder(String folderName) {
      if (StringUtils.isBlank(folderName)) {
        return this;
      }
      if (!subFolders.containsKey(folderName)) {
        subFolders.put(folderName, new Folder(folderName, this, notebookRepo, noteCache, zConf));
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
      notes.put(noteName, new NoteNode(noteInfo, this, notebookRepo, noteCache, zConf));
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

    private void clear() {
      notes.clear();
      subFolders.clear();
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
    private ZeppelinConfiguration zConf;

    public NoteNode(NoteInfo noteInfo, Folder parent, NotebookRepo notebookRepo,
        NoteCache noteCache, ZeppelinConfiguration zConf) {
      this.noteInfo = noteInfo;
      this.parent = parent;
      this.notebookRepo = notebookRepo;
      this.noteCache = noteCache;
      this.zConf = zConf;
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
          note.setCronSupported(zConf);
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
          cleanupCache();
          return false;
        }
      }

      private void cleanupCache() {
        Iterator<Map.Entry<String, Note>> iterator = this.entrySet().iterator();
        int count = 0;
        // if size >= shrinked_size and have next() try remove
        while ((this.size() - 1) >= NoteCache.this.threshold && iterator.hasNext()) {
          Map.Entry<String, Note> noteEntry = iterator.next();
          final Note note = noteEntry.getValue();
          final Lock lock = note.getLock().writeLock();
          if (lock.tryLock()) { // avoid eviction in case the note is in use
            try {
              iterator.remove(); // remove LRU element from LinkedHashMap
              LOGGER.debug("Remove note {} from LRU Cache", note.getId());
              ++count;
            } finally {
              lock.unlock();
            }
          }
        }
        LOGGER.info("The cache cleanup removes {} entries", count);
      }
    }
  }



}
