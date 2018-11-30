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

import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterNotFoundException;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepoSync;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithVersionControl;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithVersionControl.Revision;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * High level api of Notebook related operations, such as create, move & delete note/folder.
 * It will also do other thing which is caused by these operation, such as update index,
 * refresh cron and update InterpreterSetting, these are done through NoteEventListener.
 *
 */
public class Notebook {
  private static final Logger LOGGER = LoggerFactory.getLogger(Notebook.class);

  private NoteManager noteManager;

  private InterpreterFactory replFactory;
  private InterpreterSettingManager interpreterSettingManager;
  private ZeppelinConfiguration conf;
  private StdSchedulerFactory quertzSchedFact;
  org.quartz.Scheduler quartzSched;
  private ParagraphJobListener paragraphJobListener;
  private NotebookRepo notebookRepo;
  private SearchService noteSearchService;
  private NotebookAuthorization notebookAuthorization;
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
      NotebookRepo notebookRepo,
      InterpreterFactory replFactory,
      InterpreterSettingManager interpreterSettingManager,
      SearchService noteSearchService,
      NotebookAuthorization notebookAuthorization,
      Credentials credentials) throws IOException, SchedulerException {
    this.noteManager = new NoteManager(notebookRepo);
    this.conf = conf;
    this.notebookRepo = notebookRepo;
    this.replFactory = replFactory;
    this.interpreterSettingManager = interpreterSettingManager;
    this.noteSearchService = noteSearchService;
    this.notebookAuthorization = notebookAuthorization;
    this.credentials = credentials;
    quertzSchedFact = new org.quartz.impl.StdSchedulerFactory();
    quartzSched = quertzSchedFact.getScheduler();
    quartzSched.start();
    CronJob.notebook = this;

    this.noteEventListeners.add(this.noteSearchService);
    this.noteEventListeners.add(this.notebookAuthorization);
    this.noteEventListeners.add(this.interpreterSettingManager);
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
    Note note =
        new Note(notePath, defaultInterpreterGroup, replFactory, interpreterSettingManager,
            paragraphJobListener, credentials, noteEventListeners);
    saveNote(note, subject);
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
    Note note = getNote(noteId);
    if (note == null) {
      throw new IOException(noteId + " not found");
    }
    return note.toJson();
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
    Note oldNote = Note.fromJson(sourceJson);
    if (notePath == null) {
      notePath = oldNote.getName();
    }
    Note newNote = createNote(notePath, subject);
    List<Paragraph> paragraphs = oldNote.getParagraphs();
    for (Paragraph p : paragraphs) {
      newNote.addCloneParagraph(p, subject);
    }
    return newNote;
  }

  /**
   * Clone existing note.
   *
   * @param sourceNoteId - the note ID to clone
   * @param newNotePath  - the path of the new note
   * @return noteId
   * @throws IOException, CloneNotSupportedException, IllegalArgumentException
   */
  public Note cloneNote(String sourceNoteId, String newNotePath, AuthenticationInfo subject)
      throws IOException {
    Note sourceNote = getNote(sourceNoteId);
    if (sourceNote == null) {
      throw new IOException("Source note: " + sourceNoteId + " not found");
    }
    Note newNote = createNote(newNotePath, subject);
    List<Paragraph> paragraphs = sourceNote.getParagraphs();
    for (Paragraph p : paragraphs) {
      newNote.addCloneParagraph(p, subject);
    }
    return newNote;
  }

  public void removeNote(String noteId, AuthenticationInfo subject) throws IOException {
    LOGGER.info("Remove note " + noteId);
    Note note = getNote(noteId);
    noteManager.removeNote(noteId, subject);
    fireNoteRemoveEvent(note, subject);
  }

  public Note getNote(String id) {
    try {
      Note note = noteManager.getNote(id);
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
    } catch (IOException e) {
      LOGGER.warn("Fail to get note: " + id, e);
      return null;
    }
  }

  public void saveNote(Note note, AuthenticationInfo subject) throws IOException {
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
    LOGGER.info("Move note " + noteId + " to " + newNotePath);
    noteManager.moveNote(noteId, newNotePath, subject);
  }

  public void moveFolder(String folderPath, String newFolderPath, AuthenticationInfo subject) throws IOException {
    LOGGER.info("Move folder from " + folderPath + " to " + newFolderPath);
    noteManager.moveFolder(folderPath, newFolderPath, subject);
  }

  public void removeFolder(String folderPath, AuthenticationInfo subject) throws IOException {
    LOGGER.info("Remove folder " + folderPath);
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
    for (NoteManager.NoteNode noteNode : trash.getNotes().values()) {
      moveNote(noteNode.getNoteId(), noteNode.getNotePath().replace("/~Trash", ""), subject);
    }
    // restore folders under trash folder
    for (NoteManager.Folder folder : trash.getFolders().values()) {
      moveFolder(folder.getPath(), folder.getPath().replace("/~Trash", ""), subject);
    }
  }

  public Revision checkpointNote(String noteId, String noteName, String checkpointMessage,
      AuthenticationInfo subject) throws IOException {
    if (((NotebookRepoSync) notebookRepo).isRevisionSupportedInDefaultRepo()) {
      return ((NotebookRepoWithVersionControl) notebookRepo)
          .checkpoint(noteId, noteName, checkpointMessage, subject);
    } else {
      return null;
    }
  }

  public List<Revision> listRevisionHistory(String noteId,
                                            String noteName,
                                            AuthenticationInfo subject) throws IOException {
    if (((NotebookRepoSync) notebookRepo).isRevisionSupportedInDefaultRepo()) {
      return ((NotebookRepoWithVersionControl) notebookRepo)
          .revisionHistory(noteId, noteName, subject);
    } else {
      return null;
    }
  }

  public Note setNoteRevision(String noteId, String noteName, String revisionId, AuthenticationInfo subject)
      throws IOException {
    if (((NotebookRepoSync) notebookRepo).isRevisionSupportedInDefaultRepo()) {
      return ((NotebookRepoWithVersionControl) notebookRepo)
          .setNoteRevision(noteId, noteName, revisionId, subject);
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
      LOGGER.error("Failed to load " + id, e);
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
      for (String intpGroupName : savedObjects.keySet()) {
        List<AngularObject> objectList = savedObjects.get(intpGroupName);

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

    for (String name : angularObjectSnapshot.keySet()) {
      SnapshotAngularObject snapshot = angularObjectSnapshot.get(name);
      List<InterpreterSetting> settings = interpreterSettingManager.get();
      for (InterpreterSetting setting : settings) {
        InterpreterGroup intpGroup = setting.getInterpreterGroup(subject.getUser(), note.getId());
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
    this.noteManager.reloadNotes();

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

  public List<Note> getAllNotes() {
    List<Note> noteList = noteManager.getAllNotes();
    Collections.sort(noteList, Comparator.comparing(Note::getPath));
    return noteList;
  }

  public List<Note> getAllNotes(Set<String> userAndRoles) {
    final Set<String> entities = Sets.newHashSet();
    if (userAndRoles != null) {
      entities.addAll(userAndRoles);
    }
    return getAllNotes().stream()
        .filter(note -> notebookAuthorization.isReader(note.getId(), entities))
        .collect(Collectors.toList());
  }

  public List<NoteInfo> getNotesInfo(Set<String> userAndRoles) {
    final Set<String> entities = Sets.newHashSet();
    if (userAndRoles != null) {
      entities.addAll(userAndRoles);
    }
    String homescreenNoteId = conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN);
    boolean hideHomeScreenNotebookFromList =
        conf.getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN_HIDE);

    synchronized (noteManager.getNotesInfo()) {
      List<NoteInfo> notesInfo = noteManager.getNotesInfo().entrySet().stream().filter(entry ->
          notebookAuthorization.isReader(entry.getKey(), entities) &&
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


  public List<InterpreterSetting> getBindedInterpreterSettings(String noteId) {
    Note note = getNote(noteId);
    if (note != null) {
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
    } else {
      return new LinkedList<>();
    }
  }


  /**
   * Cron task for the note.
   */
  public static class CronJob implements org.quartz.Job {
    public static Notebook notebook;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

      String noteId = context.getJobDetail().getJobDataMap().getString("noteId");
      Note note = notebook.getNote(noteId);
      if (note.haveRunningOrPendingParagraphs()) {
        LOGGER.warn("execution of the cron job is skipped because there is a running or pending " +
            "paragraph (note id: {})", noteId);
        return;
      }

      if (!note.isCronSupported(notebook.getConf())) {
        LOGGER.warn("execution of the cron job is skipped cron is not enabled from Zeppelin server");
        return;
      }

      runAll(note);

      boolean releaseResource = false;
      String cronExecutingUser = null;
      try {
        Map<String, Object> config = note.getConfig();
        if (config != null) {
          if (config.containsKey("releaseresource")) {
            releaseResource = (boolean) config.get("releaseresource");
          }
          cronExecutingUser = (String) config.get("cronExecutingUser");
        }
      } catch (ClassCastException e) {
        LOGGER.error(e.getMessage(), e);
      }
      if (releaseResource) {
        for (InterpreterSetting setting : notebook.getInterpreterSettingManager()
            .getInterpreterSettings(note.getId())) {
          try {
            notebook.getInterpreterSettingManager().restart(setting.getId(), noteId,
                    cronExecutingUser != null ? cronExecutingUser : "anonymous");
          } catch (InterpreterException e) {
            LOGGER.error("Fail to restart interpreter: " + setting.getId(), e);
          }
        }
      }
    }

    void runAll(Note note) {
      String cronExecutingUser = (String) note.getConfig().get("cronExecutingUser");
      String cronExecutingRoles = (String) note.getConfig().get("cronExecutingRoles");
      if (null == cronExecutingUser) {
        cronExecutingUser = "anonymous";
      }
      AuthenticationInfo authenticationInfo = new AuthenticationInfo(
          cronExecutingUser,
          StringUtils.isEmpty(cronExecutingRoles) ? null : cronExecutingRoles,
          null);
      note.runAll(authenticationInfo, true);
    }
  }

  public void refreshCron(String id) {
    removeCron(id);
    Note note = getNote(id);
    if (note == null || note.isTrash()) {
      return;
    }
    Map<String, Object> config = note.getConfig();
    if (config == null) {
      return;
    }

    if (!note.isCronSupported(getConf())) {
      LOGGER.warn("execution of the cron job is skipped cron is not enabled from Zeppelin server");
      return;
    }

    String cronExpr = (String) note.getConfig().get("cron");
    if (cronExpr == null || cronExpr.trim().length() == 0) {
      return;
    }


    JobDetail newJob =
        JobBuilder.newJob(CronJob.class).withIdentity(id, "note").usingJobData("noteId", id)
            .build();

    Map<String, Object> info = note.getInfo();
    info.put("cron", null);

    CronTrigger trigger = null;
    try {
      trigger = TriggerBuilder.newTrigger().withIdentity("trigger_" + id, "note")
          .withSchedule(CronScheduleBuilder.cronSchedule(cronExpr)).forJob(id, "note").build();
    } catch (Exception e) {
      LOGGER.error("Error", e);
      info.put("cron", e.getMessage());
    }


    try {
      if (trigger != null) {
        quartzSched.scheduleJob(newJob, trigger);
      }
    } catch (SchedulerException e) {
      LOGGER.error("Error", e);
      info.put("cron", "Scheduler Exception");
    }

  }

  public void removeCron(String id) {
    try {
      quartzSched.deleteJob(new JobKey(id, "note"));
    } catch (SchedulerException e) {
      LOGGER.error("Can't remove quertz " + id, e);
    }
  }

  public InterpreterFactory getInterpreterFactory() {
    return replFactory;
  }

  public InterpreterSettingManager getInterpreterSettingManager() {
    return interpreterSettingManager;
  }

  public NotebookAuthorization getNotebookAuthorization() {
    return notebookAuthorization;
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
