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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterNotFoundException;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.notebook.NoteManager.Folder;
import org.apache.zeppelin.notebook.NoteManager.NoteNode;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepoSync;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithVersionControl;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithVersionControl.Revision;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High level api of Notebook related operations, such as create, move & delete note/folder.
 * It will also do other thing which is caused by these operation, such as update index,
 * refresh cron and update InterpreterSetting, these are done through NoteEventListener.
 *
 */
public class Notebook {
  private static final Logger LOGGER = LoggerFactory.getLogger(Notebook.class);

  private AuthorizationService authorizationService;
  private NoteManager noteManager;
  private InterpreterFactory replFactory;
  private InterpreterSettingManager interpreterSettingManager;
  private ZeppelinConfiguration conf;
  private ParagraphJobListener paragraphJobListener;
  private NotebookRepo notebookRepo;
  private SearchService noteSearchService;
  private List<NoteEventListener> noteEventListeners = new ArrayList<>();
  private Credentials credentials;

  /**
   * Main constructor \w manual Dependency Injection
   *
   * @throws IOException
   * @throws SchedulerException
   */
  public Notebook(
      ZeppelinConfiguration conf,
      AuthorizationService authorizationService,
      NotebookRepo notebookRepo,
      NoteManager noteManager,
      InterpreterFactory replFactory,
      InterpreterSettingManager interpreterSettingManager,
      SearchService noteSearchService,
      Credentials credentials)
      throws IOException {
    this.conf = conf;
    this.authorizationService = authorizationService;
    this.noteManager = noteManager;
    this.notebookRepo = notebookRepo;
    this.replFactory = replFactory;
    this.interpreterSettingManager = interpreterSettingManager;
    // TODO(zjffdu) cycle refer, not a good solution
    this.interpreterSettingManager.setNotebook(this);
    this.noteSearchService = noteSearchService;
    this.credentials = credentials;
    this.noteEventListeners.add(this.noteSearchService);
    this.noteEventListeners.add(this.interpreterSettingManager);

    if (conf.isIndexRebuild()) {
      noteSearchService.startRebuildIndex(getNoteStream());
    }
  }

  public void recoveryIfNecessary() {
    if (conf.isRecoveryEnabled()) {
      recoverRunningParagraphs();
    }
  }

  private void recoverRunningParagraphs() {
    Thread thread = new Thread(() -> {
      getNoteStream().forEach(note -> {
        try {
          boolean hasRecoveredParagraph = false;
          for (Paragraph paragraph : note.getParagraphs()) {
            if (paragraph.getStatus() == Job.Status.RUNNING) {
              paragraph.recover();
              hasRecoveredParagraph = true;
            }
          }
          // unload note to save memory when there's no paragraph recovering.
          if (!hasRecoveredParagraph) {
            note.unLoad();
          }
        } catch (Exception e) {
          LOGGER.warn("Fail to recovery note: {}", note.getPath(), e);
        }
      });
    });
    thread.setName("Recovering-Thread");
    thread.start();
    LOGGER.info("Start paragraph recovering thread");

    try {
      thread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Inject
  public Notebook(
      ZeppelinConfiguration conf,
      AuthorizationService authorizationService,
      NotebookRepo notebookRepo,
      NoteManager noteManager,
      InterpreterFactory replFactory,
      InterpreterSettingManager interpreterSettingManager,
      SearchService noteSearchService,
      Credentials credentials,
      NoteEventListener noteEventListener)
      throws IOException {
    this(
        conf,
        authorizationService,
        notebookRepo,
        noteManager,
        replFactory,
        interpreterSettingManager,
        noteSearchService,
        credentials);
    if (null != noteEventListener) {
      this.noteEventListeners.add(noteEventListener);
    }
    this.paragraphJobListener = (ParagraphJobListener) noteEventListener;
  }

  public NoteManager getNoteManager() {
    return noteManager;
  }

  /**
   * This method will be called only NotebookService to register {@link *
   * org.apache.zeppelin.notebook.ParagraphJobListener}.
   */
  public void setParagraphJobListener(ParagraphJobListener paragraphJobListener) {
    this.paragraphJobListener = paragraphJobListener;
  }

  /**
   * Creating new note. defaultInterpreterGroup is not provided, so the global
   * defaultInterpreterGroup (zeppelin.interpreter.group.default) is used
   *
   * @param notePath
   * @param subject
   * @return
   * @throws IOException
   */
  public Note createNote(String notePath,
                         AuthenticationInfo subject) throws IOException {
    return createNote(notePath, interpreterSettingManager.getDefaultInterpreterSetting().getName(),
        subject);
  }

  /**
   * Creating new note. defaultInterpreterGroup is not provided, so the global
   * defaultInterpreterGroup (zeppelin.interpreter.group.default) is used
   *
   * @param notePath
   * @param subject
   * @param save
   * @return
   * @throws IOException
   */
  public Note createNote(String notePath,
                         AuthenticationInfo subject,
                         boolean save) throws IOException {
    return createNote(notePath, interpreterSettingManager.getDefaultInterpreterSetting().getName(),
            subject, save);
  }

  /**
   * Creating new note.
   *
   * @param notePath
   * @param defaultInterpreterGroup
   * @param subject
   * @return
   * @throws IOException
   */
  public Note createNote(String notePath,
                         String defaultInterpreterGroup,
                         AuthenticationInfo subject) throws IOException {
    return createNote(notePath, defaultInterpreterGroup, subject, true);
  }

  /**
   * Creating new note.
   *
   * @param notePath
   * @param defaultInterpreterGroup
   * @param subject
   * @return
   * @throws IOException
   */
  public Note createNote(String notePath,
                         String defaultInterpreterGroup,
                         AuthenticationInfo subject,
                         boolean save) throws IOException {
    Note note =
            new Note(notePath, defaultInterpreterGroup, replFactory, interpreterSettingManager,
                    paragraphJobListener, credentials, noteEventListeners);
    noteManager.addNote(note, subject);
    // init noteMeta
    authorizationService.createNoteAuth(note.getId(), subject);
    authorizationService.saveNoteAuth(note.getId(), subject);
    if (save) {
      noteManager.saveNote(note, subject);
    }
    fireNoteCreateEvent(note, subject);
    return note;
  }

  /**
   * Export existing note.
   *
   * @param noteId - the note ID to clone
   * @return Note JSON
   * @throws IOException, IllegalArgumentException
   */
  public String exportNote(String noteId) throws IOException {
    try {
      Note note = getNote(noteId);
      if (note == null) {
        throw new IOException("Note " + noteId + " not found");
      }
      return note.toJson();
    } catch (IOException e) {
      throw new IOException(noteId + " not found");
    }
  }

  /**
   * import JSON as a new note.
   *
   * @param sourceJson - the note JSON to import
   * @param notePath   - the path of the new note
   * @return note ID
   * @throws IOException
   */
  public Note importNote(String sourceJson, String notePath, AuthenticationInfo subject)
      throws IOException {
    Note oldNote = Note.fromJson(null, sourceJson);
    if (notePath == null) {
      notePath = oldNote.getName();
    }
    Note newNote = createNote(notePath, subject);
    List<Paragraph> paragraphs = oldNote.getParagraphs();
    for (Paragraph p : paragraphs) {
      newNote.addCloneParagraph(p, subject);
    }
    noteManager.saveNote(newNote, subject);
    return newNote;
  }

  /**
   * Clone existing note.
   *
   * @param sourceNoteId - the note ID to clone
   * @param newNotePath  - the path of the new note
   * @return noteId
   * @throws IOException
   */
  public Note cloneNote(String sourceNoteId, String newNotePath, AuthenticationInfo subject)
      throws IOException {
    Note sourceNote = getNote(sourceNoteId);
    if (sourceNote == null) {
      throw new IOException("Source note: " + sourceNoteId + " not found");
    }
    Note newNote = createNote(newNotePath, subject, false);
    List<Paragraph> paragraphs = sourceNote.getParagraphs();
    for (Paragraph p : paragraphs) {
      newNote.addCloneParagraph(p, subject);
    }

    newNote.setConfig(new HashMap<>(sourceNote.getConfig()));
    newNote.setInfo(new HashMap<>(sourceNote.getInfo()));
    newNote.setDefaultInterpreterGroup(sourceNote.getDefaultInterpreterGroup());
    newNote.setNoteForms(new HashMap<>(sourceNote.getNoteForms()));
    newNote.setNoteParams(new HashMap<>(sourceNote.getNoteParams()));
    newNote.setRunning(false);

    saveNote(newNote, subject);
    authorizationService.cloneNoteMeta(newNote.getId(), sourceNoteId, subject);
    return newNote;
  }

  public void removeNote(Note note, AuthenticationInfo subject) throws IOException {
    LOGGER.info("Remove note: {}", note.getId());
    // Set Remove to true to cancel saving this note
    note.setRemoved(true);
    noteManager.removeNote(note.getId(), subject);
    authorizationService.removeNoteAuth(note.getId());
    fireNoteRemoveEvent(note, subject);
  }

  public void removeCorruptedNote(String noteId, AuthenticationInfo subject) throws IOException {
    LOGGER.info("Remove corrupted note: {}", noteId);
    noteManager.removeNote(noteId, subject);
    authorizationService.removeNoteAuth(noteId);
  }

  public void removeNote(String noteId, AuthenticationInfo subject) throws IOException {
    Note note = getNote(noteId);
    removeNote(note, subject);
  }

  /**
   * Get note from NotebookRepo and also initialize it with other properties that is not
   * persistent in NotebookRepo, such as paragraphJobListener.
   * @param noteId
   * @return null if note not found.
   * @throws IOException when fail to get it from NotebookRepo.
   */
  public Note getNote(String noteId) throws IOException {
    return getNote(noteId, false);
  }

  /**
   * Get note from NotebookRepo and also initialize it with other properties that is not
   * persistent in NotebookRepo, such as paragraphJobListener.
   * @param noteId
   * @return null if note not found.
   * @throws IOException when fail to get it from NotebookRepo.
   */
  public Note getNote(String noteId, boolean reload) throws IOException {
    Note note = noteManager.getNote(noteId, reload);
    if (note == null) {
      return null;
    }
    note.setInterpreterFactory(replFactory);
    note.setInterpreterSettingManager(interpreterSettingManager);
    note.setParagraphJobListener(paragraphJobListener);
    note.setNoteEventListeners(noteEventListeners);
    note.setCredentials(credentials);
    for (Paragraph p : note.getParagraphs()) {
      p.setNote(note);
    }
    return note;
  }

  public void saveNote(Note note, AuthenticationInfo subject) throws IOException {
    noteManager.saveNote(note, subject);
  }

  public void updateNote(Note note, AuthenticationInfo subject) throws IOException {
    noteManager.saveNote(note, subject);
    fireNoteUpdateEvent(note, subject);
  }

  public boolean containsNote(String notePath) {
    return noteManager.containsNote(notePath);
  }

  public boolean containsFolder(String folderPath) {
    return noteManager.containsFolder(folderPath);
  }

  public void moveNote(String noteId, String newNotePath, AuthenticationInfo subject) throws IOException {
    LOGGER.info("Move note {} to {}", noteId, newNotePath);
    noteManager.moveNote(noteId, newNotePath, subject);
  }

  public void moveFolder(String folderPath, String newFolderPath, AuthenticationInfo subject) throws IOException {
    LOGGER.info("Move folder from {} to {}", folderPath, newFolderPath);
    noteManager.moveFolder(folderPath, newFolderPath, subject);
  }

  public void removeFolder(String folderPath, AuthenticationInfo subject) throws IOException {
    LOGGER.info("Remove folder {}", folderPath);
    // TODO(zjffdu) NotebookRepo.remove is called twice here
    List<Note> notes = noteManager.removeFolder(folderPath, subject);
    for (Note note : notes) {
      fireNoteRemoveEvent(note, subject);
    }
  }

  public void emptyTrash(AuthenticationInfo subject) throws IOException {
    LOGGER.info("Empty Trash");
    removeFolder("/" + NoteManager.TRASH_FOLDER, subject);
  }

  public void restoreAll(AuthenticationInfo subject) throws IOException {
    NoteManager.Folder trash = noteManager.getTrashFolder();
    // restore notes under trash folder
    // If the value changes in the loop, a concurrent modification exception is thrown.
    // Collector implementation of collect methods to maintain immutability.
    List<NoteNode> notes = trash.getNotes().values().stream().collect(Collectors.toList());
    for (NoteManager.NoteNode noteNode : notes) {
      moveNote(noteNode.getNoteId(), noteNode.getNotePath().replace("/~Trash", ""), subject);
    }
    // restore folders under trash folder
    List<Folder> folders = trash.getFolders().values().stream().collect(Collectors.toList());
    for (NoteManager.Folder folder : folders) {
      moveFolder(folder.getPath(), folder.getPath().replace("/~Trash", ""), subject);
    }
  }

  public Revision checkpointNote(String noteId, String notePath, String checkpointMessage,
      AuthenticationInfo subject) throws IOException {
    if (((NotebookRepoSync) notebookRepo).isRevisionSupportedInDefaultRepo()) {
      return ((NotebookRepoWithVersionControl) notebookRepo)
          .checkpoint(noteId, notePath, checkpointMessage, subject);
    } else {
      return null;
    }
  }

  public List<Revision> listRevisionHistory(String noteId,
                                            String notePath,
                                            AuthenticationInfo subject) throws IOException {
    if (((NotebookRepoSync) notebookRepo).isRevisionSupportedInDefaultRepo()) {
      return ((NotebookRepoWithVersionControl) notebookRepo)
          .revisionHistory(noteId, notePath, subject);
    } else {
      return null;
    }
  }

  public Note setNoteRevision(String noteId, String notePath, String revisionId, AuthenticationInfo subject)
      throws IOException {
    if (((NotebookRepoSync) notebookRepo).isRevisionSupportedInDefaultRepo()) {
      Note note = ((NotebookRepoWithVersionControl) notebookRepo)
              .setNoteRevision(noteId, notePath, revisionId, subject);
      noteManager.saveNote(note);
      return note;
    } else {
      return null;
    }
  }

  public Note getNoteByRevision(String noteId, String noteName,
                                String revisionId, AuthenticationInfo subject)
      throws IOException {
    if (((NotebookRepoSync) notebookRepo).isRevisionSupportedInDefaultRepo()) {
      return ((NotebookRepoWithVersionControl) notebookRepo).get(noteId, noteName,
          revisionId, subject);
    } else {
      return null;
    }
  }

  @SuppressWarnings("rawtypes")
  public Note loadNoteFromRepo(String id, AuthenticationInfo subject) {
    Note note = null;
    try {
      note = noteManager.getNote(id);
    } catch (IOException e) {
      LOGGER.error("Fail to get note: {}", id, e);
      return null;
    }
    if (note == null) {
      return null;
    }

    //Manually inject ALL dependencies, as DI constructor was NOT used
    note.setCredentials(this.credentials);

    note.setInterpreterFactory(replFactory);
    note.setInterpreterSettingManager(interpreterSettingManager);

    note.setParagraphJobListener(this.paragraphJobListener);
    note.setCronSupported(getConf());

    if (note.getDefaultInterpreterGroup() == null) {
      note.setDefaultInterpreterGroup(conf.getString(ConfVars.ZEPPELIN_INTERPRETER_GROUP_DEFAULT));
    }

    Map<String, SnapshotAngularObject> angularObjectSnapshot = new HashMap<>();

    // restore angular object --------------
    Date lastUpdatedDate = new Date(0);
    for (Paragraph p : note.getParagraphs()) {
      p.setNote(note);
      if (p.getDateFinished() != null && lastUpdatedDate.before(p.getDateFinished())) {
        lastUpdatedDate = p.getDateFinished();
      }
    }

    Map<String, List<AngularObject>> savedObjects = note.getAngularObjects();

    if (savedObjects != null) {
      for (Entry<String, List<AngularObject>> intpGroupNameEntry : savedObjects.entrySet()) {
        String intpGroupName = intpGroupNameEntry.getKey();
        List<AngularObject> objectList = intpGroupNameEntry.getValue();

        for (AngularObject object : objectList) {
          SnapshotAngularObject snapshot = angularObjectSnapshot.get(object.getName());
          if (snapshot == null || snapshot.getLastUpdate().before(lastUpdatedDate)) {
            angularObjectSnapshot.put(object.getName(),
                new SnapshotAngularObject(intpGroupName, object, lastUpdatedDate));
          }
        }
      }
    }

    note.setNoteEventListeners(this.noteEventListeners);

    for (Entry<String, SnapshotAngularObject> angularObjectSnapshotEntry : angularObjectSnapshot.entrySet()) {
      String name = angularObjectSnapshotEntry.getKey();
      SnapshotAngularObject snapshot = angularObjectSnapshotEntry.getValue();
      List<InterpreterSetting> settings = interpreterSettingManager.get();
      for (InterpreterSetting setting : settings) {
        InterpreterGroup intpGroup = setting.getInterpreterGroup(note.getExecutionContext());
        if (intpGroup != null && intpGroup.getId().equals(snapshot.getIntpGroupId())) {
          AngularObjectRegistry registry = intpGroup.getAngularObjectRegistry();
          String noteId = snapshot.getAngularObject().getNoteId();
          String paragraphId = snapshot.getAngularObject().getParagraphId();
          // at this point, remote interpreter process is not created.
          // so does not make sense add it to the remote.
          //
          // therefore instead of addAndNotifyRemoteProcess(), need to use add()
          // that results add angularObject only in ZeppelinServer side not remoteProcessSide
          registry.add(name, snapshot.getAngularObject().get(), noteId, paragraphId);
        }
      }
    }

    return note;
  }

  /**
   * Reload all notes from repository after clearing `notes` and `folders`
   * to reflect the changes of added/deleted/modified notes on file system level.
   *
   * @throws IOException
   */
  public void reloadAllNotes(AuthenticationInfo subject) throws IOException {
    this.noteManager.reloadNotesInfo();

    if (notebookRepo instanceof NotebookRepoSync) {
      NotebookRepoSync mainRepo = (NotebookRepoSync) notebookRepo;
      if (mainRepo.getRepoCount() > 1) {
        mainRepo.sync(subject);
      }
    }
  }

  private class SnapshotAngularObject {
    String intpGroupId;
    AngularObject angularObject;
    Date lastUpdate;

    SnapshotAngularObject(String intpGroupId, AngularObject angularObject, Date lastUpdate) {
      super();
      this.intpGroupId = intpGroupId;
      this.angularObject = angularObject;
      this.lastUpdate = lastUpdate;
    }

    String getIntpGroupId() {
      return intpGroupId;
    }

    AngularObject getAngularObject() {
      return angularObject;
    }

    Date getLastUpdate() {
      return lastUpdate;
    }
  }

  public List<NoteInfo> getNotesInfo() {
    return noteManager.getNotesInfo().entrySet().stream()
        .map(entry -> new NoteInfo(entry.getKey(), entry.getValue()))
        .collect(Collectors.toList());
  }

  public Stream<Note> getNoteStream() {
    return noteManager.getNotesStream().map(note -> {
      note.setInterpreterFactory(replFactory);
      note.setInterpreterSettingManager(interpreterSettingManager);
      note.setParagraphJobListener(paragraphJobListener);
      note.setNoteEventListeners(noteEventListeners);
      note.setCredentials(credentials);
      for (Paragraph p : note.getParagraphs()) {
        p.setNote(note);
        p.setListener(paragraphJobListener);
      }
      return note;
    });
  }

  @VisibleForTesting
  public List<Note> getAllNotes(Function<Note, Boolean> func){
    return getAllNotes().stream()
        .filter(note -> func.apply(note))
        .collect(Collectors.toList());
  }

  @VisibleForTesting
  public List<Note> getAllNotes() {
    List<Note> notes = getNoteStream().collect(Collectors.toList());
    Collections.sort(notes, Comparator.comparing(Note::getPath));
    return notes;
  }

  public List<NoteInfo> getNotesInfo(Function<String, Boolean> func) {
    String homescreenNoteId = conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN);
    boolean hideHomeScreenNotebookFromList =
        conf.getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN_HIDE);

    synchronized (noteManager.getNotesInfo()) {
      List<NoteInfo> notesInfo = noteManager.getNotesInfo().entrySet().stream().filter(entry ->
              func.apply(entry.getKey()) &&
              ((!hideHomeScreenNotebookFromList) ||
                  ((hideHomeScreenNotebookFromList) && !entry.getKey().equals(homescreenNoteId))))
          .map(entry -> new NoteInfo(entry.getKey(), entry.getValue()))
          .collect(Collectors.toList());

      notesInfo.sort((note1, note2) -> {
            String name1 = note1.getId();
            if (note1.getPath() != null) {
              name1 = note1.getPath();
            }
            String name2 = note2.getId();
            if (note2.getPath() != null) {
              name2 = note2.getPath();
            }
            return name1.compareTo(name2);
          });
      return notesInfo;
    }
  }

  public List<InterpreterSetting> getBindedInterpreterSettings(String noteId) throws IOException {
    Note note  = getNote(noteId);
    if (note == null) {
      return new ArrayList<>();
    }
    Set<InterpreterSetting> settings = new HashSet<>();
    for (Paragraph p : note.getParagraphs()) {
      try {
        Interpreter intp = p.getBindedInterpreter();
        settings.add((
                (ManagedInterpreterGroup) intp.getInterpreterGroup()).getInterpreterSetting());
      } catch (InterpreterNotFoundException e) {
        // ignore this
      }
    }
    // add the default interpreter group
    InterpreterSetting defaultIntpSetting =
            interpreterSettingManager.getByName(note.getDefaultInterpreterGroup());
    if (defaultIntpSetting != null) {
      settings.add(defaultIntpSetting);
    }
    return new ArrayList<>(settings);
  }

  public InterpreterFactory getInterpreterFactory() {
    return replFactory;
  }

  public InterpreterSettingManager getInterpreterSettingManager() {
    return interpreterSettingManager;
  }

  public ZeppelinConfiguration getConf() {
    return conf;
  }

  public void close() {
    this.notebookRepo.close();
    this.noteSearchService.close();
  }

  public void addNotebookEventListener(NoteEventListener listener) {
    noteEventListeners.add(listener);
  }

  private void fireNoteCreateEvent(Note note, AuthenticationInfo subject) throws IOException {
    for (NoteEventListener listener : noteEventListeners) {
      listener.onNoteCreate(note, subject);
    }
  }

  private void fireNoteUpdateEvent(Note note, AuthenticationInfo subject) throws IOException {
    for (NoteEventListener listener : noteEventListeners) {
      listener.onNoteUpdate(note, subject);
    }
  }

  private void fireNoteRemoveEvent(Note note, AuthenticationInfo subject) throws IOException {
    for (NoteEventListener listener : noteEventListeners) {
      listener.onNoteRemove(note, subject);
    }
  }

  public Boolean isRevisionSupported() {
    if (notebookRepo instanceof NotebookRepoSync) {
      return ((NotebookRepoSync) notebookRepo).isRevisionSupportedInDefaultRepo();
    } else if (notebookRepo instanceof NotebookRepoWithVersionControl) {
      return true;
    } else {
      return false;
    }
  }
}
