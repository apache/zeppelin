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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepo.Revision;
import org.apache.zeppelin.notebook.repo.NotebookRepoSync;
import org.apache.zeppelin.resource.ResourcePoolUtils;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;

/**
 * Collection of Notes.
 */
public class Notebook implements NoteEventListener {
  private static final Logger logger = LoggerFactory.getLogger(Notebook.class);

  @SuppressWarnings("unused") @Deprecated //TODO(bzz): remove unused
  private SchedulerFactory schedulerFactory;

  private InterpreterFactory replFactory;
  /**
   * Keep the order.
   */
  private final Map<String, Note> notes = new LinkedHashMap<>();
  private ZeppelinConfiguration conf;
  private StdSchedulerFactory quertzSchedFact;
  private org.quartz.Scheduler quartzSched;
  private JobListenerFactory jobListenerFactory;
  private NotebookRepo notebookRepo;
  private SearchService notebookIndex;
  private NotebookAuthorization notebookAuthorization;
  private final List<NotebookEventListener> notebookEventListeners =
      Collections.synchronizedList(new LinkedList<NotebookEventListener>());
  private Credentials credentials;

  /**
   * Main constructor \w manual Dependency Injection
   *
   * @param notebookIndex         - (nullable) for indexing all notebooks on creating.
   * @throws IOException
   * @throws SchedulerException
   */
  public Notebook(ZeppelinConfiguration conf, NotebookRepo notebookRepo,
      SchedulerFactory schedulerFactory, InterpreterFactory replFactory,
      JobListenerFactory jobListenerFactory, SearchService notebookIndex,
      NotebookAuthorization notebookAuthorization, Credentials credentials)
      throws IOException, SchedulerException {
    this.conf = conf;
    this.notebookRepo = notebookRepo;
    this.schedulerFactory = schedulerFactory;
    this.replFactory = replFactory;
    this.jobListenerFactory = jobListenerFactory;
    this.notebookIndex = notebookIndex;
    this.notebookAuthorization = notebookAuthorization;
    this.credentials = credentials;
    quertzSchedFact = new org.quartz.impl.StdSchedulerFactory();
    quartzSched = quertzSchedFact.getScheduler();
    quartzSched.start();
    CronJob.notebook = this;

    loadAllNotes();
    if (this.notebookIndex != null) {
      long start = System.nanoTime();
      logger.info("Notebook indexing started...");
      notebookIndex.addIndexDocs(notes.values());
      logger.info("Notebook indexing finished: {} indexed in {}s", notes.size(),
          TimeUnit.NANOSECONDS.toSeconds(start - System.nanoTime()));
    }

  }

  /**
   * Create new note.
   *
   * @throws IOException
   */
  public Note createNote(AuthenticationInfo subject) throws IOException {
    Note note;
    if (conf.getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_AUTO_INTERPRETER_BINDING)) {
      note = createNote(replFactory.getDefaultInterpreterSettingList(), subject);
    } else {
      note = createNote(null, subject);
    }
    notebookIndex.addIndexDoc(note);
    return note;
  }

  /**
   * Create new note.
   *
   * @throws IOException
   */
  public Note createNote(List<String> interpreterIds, AuthenticationInfo subject)
      throws IOException {
    Note note =
        new Note(notebookRepo, replFactory, jobListenerFactory, notebookIndex, credentials, this);
    synchronized (notes) {
      notes.put(note.id(), note);
    }
    if (interpreterIds != null) {
      bindInterpretersToNote(note.id(), interpreterIds);
      note.putDefaultReplName();
    }

    notebookIndex.addIndexDoc(note);
    note.persist(subject);
    fireNoteCreateEvent(note);
    return note;
  }

  /**
   * Export existing note.
   *
   * @param noteId - the note ID to clone
   * @return Note JSON
   * @throws IOException, IllegalArgumentException
   */
  public String exportNote(String noteId) throws IOException, IllegalArgumentException {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    Gson gson = gsonBuilder.create();
    Note note = getNote(noteId);
    if (note == null) {
      throw new IllegalArgumentException(noteId + " not found");
    }
    return gson.toJson(note);
  }

  /**
   * import JSON as a new note.
   *
   * @param sourceJson - the note JSON to import
   * @param noteName   - the name of the new note
   * @return notebook ID
   * @throws IOException
   */
  public Note importNote(String sourceJson, String noteName, AuthenticationInfo subject)
      throws IOException {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();

    Gson gson =
        gsonBuilder.registerTypeAdapter(Date.class, new NotebookImportDeserializer()).create();
    JsonReader reader = new JsonReader(new StringReader(sourceJson));
    reader.setLenient(true);
    Note newNote;
    try {
      Note oldNote = gson.fromJson(reader, Note.class);
      newNote = createNote(subject);
      if (noteName != null)
        newNote.setName(noteName);
      else
        newNote.setName(oldNote.getName());
      List<Paragraph> paragraphs = oldNote.getParagraphs();
      for (Paragraph p : paragraphs) {
        newNote.addCloneParagraph(p);
      }

      newNote.persist(subject);
    } catch (IOException e) {
      logger.error(e.toString(), e);
      throw e;
    }

    return newNote;
  }

  /**
   * Clone existing note.
   *
   * @param sourceNoteID - the note ID to clone
   * @param newNoteName  - the name of the new note
   * @return noteId
   * @throws IOException, CloneNotSupportedException, IllegalArgumentException
   */
  public Note cloneNote(String sourceNoteID, String newNoteName, AuthenticationInfo subject)
      throws IOException, CloneNotSupportedException, IllegalArgumentException {

    Note sourceNote = getNote(sourceNoteID);
    if (sourceNote == null) {
      throw new IllegalArgumentException(sourceNoteID + "not found");
    }
    Note newNote = createNote(subject);
    if (newNoteName != null) {
      newNote.setName(newNoteName);
    }
    // Copy the interpreter bindings
    List<String> boundInterpreterSettingsIds = getBindedInterpreterSettingsIds(sourceNote.id());
    bindInterpretersToNote(newNote.id(), boundInterpreterSettingsIds);

    List<Paragraph> paragraphs = sourceNote.getParagraphs();
    for (Paragraph p : paragraphs) {
      newNote.addCloneParagraph(p);
    }

    notebookIndex.addIndexDoc(newNote);
    newNote.persist(subject);
    return newNote;
  }

  public void bindInterpretersToNote(String id, List<String> interpreterSettingIds)
      throws IOException {
    Note note = getNote(id);
    if (note != null) {
      List<InterpreterSetting> currentBindings = replFactory.getInterpreterSettings(id);
      for (InterpreterSetting setting : currentBindings) {
        if (!interpreterSettingIds.contains(setting.getId())) {
          fireUnbindInterpreter(note, setting);
        }
      }

      replFactory.setInterpreters(note.getId(), interpreterSettingIds);
      // comment out while note.getNoteReplLoader().setInterpreters(...) do the same
      // replFactory.putNoteInterpreterSettingBinding(id, interpreterSettingIds);
    }
  }

  List<String> getBindedInterpreterSettingsIds(String id) {
    Note note = getNote(id);
    if (note != null) {
      return getInterpreterFactory().getInterpreters(note.getId());
    } else {
      return new LinkedList<>();
    }
  }

  public List<InterpreterSetting> getBindedInterpreterSettings(String id) {
    Note note = getNote(id);
    if (note != null) {
      return replFactory.getInterpreterSettings(note.getId());
    } else {
      return new LinkedList<>();
    }
  }

  public Note getNote(String id) {
    synchronized (notes) {
      return notes.get(id);
    }
  }

  public void removeNote(String id, AuthenticationInfo subject) {
    Note note;

    synchronized (notes) {
      note = notes.remove(id);
    }
    replFactory.removeNoteInterpreterSettingBinding(id);
    notebookIndex.deleteIndexDocs(note);
    notebookAuthorization.removeNote(id);

    // remove from all interpreter instance's angular object registry
    for (InterpreterSetting settings : replFactory.get()) {
      AngularObjectRegistry registry = settings.getInterpreterGroup(id).getAngularObjectRegistry();
      if (registry instanceof RemoteAngularObjectRegistry) {
        // remove paragraph scope object
        for (Paragraph p : note.getParagraphs()) {
          ((RemoteAngularObjectRegistry) registry).removeAllAndNotifyRemoteProcess(id, p.getId());

          // remove app scope object
          List<ApplicationState> appStates = p.getAllApplicationStates();
          if (appStates != null) {
            for (ApplicationState app : appStates) {
              ((RemoteAngularObjectRegistry) registry)
                  .removeAllAndNotifyRemoteProcess(id, app.getId());
            }
          }
        }
        // remove notebook scope object
        ((RemoteAngularObjectRegistry) registry).removeAllAndNotifyRemoteProcess(id, null);
      } else {
        // remove paragraph scope object
        for (Paragraph p : note.getParagraphs()) {
          registry.removeAll(id, p.getId());

          // remove app scope object
          List<ApplicationState> appStates = p.getAllApplicationStates();
          if (appStates != null) {
            for (ApplicationState app : appStates) {
              registry.removeAll(id, app.getId());
            }
          }
        }
        // remove notebook scope object
        registry.removeAll(id, null);
      }
    }

    ResourcePoolUtils.removeResourcesBelongsToNote(id);

    fireNoteRemoveEvent(note);

    try {
      note.unpersist(subject);
    } catch (IOException e) {
      logger.error(e.toString(), e);
    }
  }

  public Revision checkpointNote(String noteId, String checkpointMessage,
      AuthenticationInfo subject) throws IOException {
    return notebookRepo.checkpoint(noteId, checkpointMessage, subject);
  }

  public List<NotebookRepo.Revision> listRevisionHistory(String noteId,
      AuthenticationInfo subject) {
    return notebookRepo.revisionHistory(noteId, subject);
  }

  public Note getNoteRevision(String noteId, Revision revision, AuthenticationInfo subject)
      throws IOException {
    return notebookRepo.get(noteId, revision, subject);
  }

  @SuppressWarnings("rawtypes")
  private Note loadNoteFromRepo(String id, AuthenticationInfo subject) {
    Note note = null;
    try {
      note = notebookRepo.get(id, subject);
    } catch (IOException e) {
      logger.error("Failed to load " + id, e);
    }
    if (note == null) {
      return null;
    }

    //Manually inject ALL dependencies, as DI constructor was NOT used
    note.setIndex(this.notebookIndex);
    note.setCredentials(this.credentials);

    note.setInterpreterFactory(replFactory);

    note.setJobListenerFactory(jobListenerFactory);
    note.setNotebookRepo(notebookRepo);

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

    note.setNoteEventListener(this);

    synchronized (notes) {
      notes.put(note.id(), note);
      refreshCron(note.id());
    }

    for (String name : angularObjectSnapshot.keySet()) {
      SnapshotAngularObject snapshot = angularObjectSnapshot.get(name);
      List<InterpreterSetting> settings = replFactory.get();
      for (InterpreterSetting setting : settings) {
        InterpreterGroup intpGroup = setting.getInterpreterGroup(note.id());
        if (intpGroup.getId().equals(snapshot.getIntpGroupId())) {
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

  private void loadAllNotes() throws IOException {
    List<NoteInfo> noteInfos = notebookRepo.list(null);

    for (NoteInfo info : noteInfos) {
      loadNoteFromRepo(info.getId(), null);
    }
  }

  /**
   * Reload all notes from repository after clearing `notes`
   * to reflect the changes of added/deleted/modified notebooks on file system level.
   *
   * @throws IOException
   */
  public void reloadAllNotes(AuthenticationInfo subject) throws IOException {
    synchronized (notes) {
      notes.clear();
    }

    if (notebookRepo instanceof NotebookRepoSync) {
      NotebookRepoSync mainRepo = (NotebookRepoSync) notebookRepo;
      if (mainRepo.getRepoCount() > 1) {
        mainRepo.sync();
      }
    }

    List<NoteInfo> noteInfos = notebookRepo.list(subject);
    for (NoteInfo info : noteInfos) {
      loadNoteFromRepo(info.getId(), subject);
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

  public List<Note> getAllNotes() {
    synchronized (notes) {
      List<Note> noteList = new ArrayList<>(notes.values());
      Collections.sort(noteList, new Comparator<Note>() {
        @Override
        public int compare(Note note1, Note note2) {
          String name1 = note1.id();
          if (note1.getName() != null) {
            name1 = note1.getName();
          }
          String name2 = note2.id();
          if (note2.getName() != null) {
            name2 = note2.getName();
          }
          return name1.compareTo(name2);
        }
      });
      return noteList;
    }
  }

  private Map<String, Object> getParagraphForJobManagerItem(Paragraph paragraph) {
    Map<String, Object> paragraphItem = new HashMap<>();

    // set paragraph id
    paragraphItem.put("id", paragraph.getId());

    // set paragraph name
    String paragraphName = paragraph.getTitle();
    if (paragraphName != null) {
      paragraphItem.put("name", paragraphName);
    } else {
      paragraphItem.put("name", paragraph.getId());
    }

    // set status for paragraph.
    paragraphItem.put("status", paragraph.getStatus().toString());

    return paragraphItem;
  }

  private long getUnixTimeLastRunParagraph(Paragraph paragraph) {

    Date lastRunningDate;
    long lastRunningUnixTime;

    Date paragaraphDate = paragraph.getDateStarted();
    // diff started time <-> finishied time
    if (paragaraphDate == null) {
      paragaraphDate = paragraph.getDateFinished();
    } else {
      if (paragraph.getDateFinished() != null && paragraph.getDateFinished()
          .after(paragaraphDate)) {
        paragaraphDate = paragraph.getDateFinished();
      }
    }

    // finished time and started time is not exists.
    if (paragaraphDate == null) {
      paragaraphDate = paragraph.getDateCreated();
    }

    // set last update unixtime(ms).
    lastRunningDate = paragaraphDate;

    lastRunningUnixTime = lastRunningDate.getTime();

    return lastRunningUnixTime;
  }

  public List<Map<String, Object>> getJobListforNotebook(boolean needsReload,
      long lastUpdateServerUnixTime, AuthenticationInfo subject) {
    final String CRON_TYPE_NOTEBOOK_KEYWORD = "cron";

    if (needsReload) {
      try {
        reloadAllNotes(subject);
      } catch (IOException e) {
        logger.error("Fail to reload notes from repository");
      }
    }

    List<Note> notes = getAllNotes();
    List<Map<String, Object>> notesInfo = new LinkedList<>();
    for (Note note : notes) {
      boolean isNotebookRunning = false;
      boolean isUpdateNotebook = false;
      long lastRunningUnixTime = 0;
      Map<String, Object> info = new HashMap<>();

      // set notebook ID
      info.put("notebookId", note.id());

      // set notebook Name
      String notebookName = note.getName();
      if (notebookName != null && !notebookName.equals("")) {
        info.put("notebookName", note.getName());
      } else {
        info.put("notebookName", "Note " + note.id());
      }

      // set notebook type ( cron or normal )
      if (note.getConfig().containsKey(CRON_TYPE_NOTEBOOK_KEYWORD) && !note.getConfig()
          .get(CRON_TYPE_NOTEBOOK_KEYWORD).equals("")) {
        info.put("notebookType", "cron");
      } else {
        info.put("notebookType", "normal");
      }

      // set paragraphs
      List<Map<String, Object>> paragraphsInfo = new LinkedList<>();
      for (Paragraph paragraph : note.getParagraphs()) {
        // check paragraph's status.
        if (paragraph.getStatus().isRunning()) {
          isNotebookRunning = true;
          isUpdateNotebook = true;
        }

        // get data for the job manager.
        Map<String, Object> paragraphItem = getParagraphForJobManagerItem(paragraph);
        lastRunningUnixTime = getUnixTimeLastRunParagraph(paragraph);

        // is update notebook for last server update time.
        if (lastRunningUnixTime > lastUpdateServerUnixTime) {
          isUpdateNotebook = true;
        }
        paragraphsInfo.add(paragraphItem);
      }

      // set interpreter bind type
      String interpreterGroupName = null;
      if (replFactory.getInterpreterSettings(note.getId()) != null
          && replFactory.getInterpreterSettings(note.getId()).size() >= 1) {
        interpreterGroupName = replFactory.getInterpreterSettings(note.getId()).get(0).getName();
      }

      // not update and not running -> pass
      if (!isUpdateNotebook && !isNotebookRunning) {
        continue;
      }

      // notebook json object root information.
      info.put("interpreter", interpreterGroupName);
      info.put("isRunningJob", isNotebookRunning);
      info.put("unixTimeLastRun", lastRunningUnixTime);
      info.put("paragraphs", paragraphsInfo);
      notesInfo.add(info);
    }

    return notesInfo;
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
      note.runAll();

      while (!note.isTerminated()) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          logger.error(e.toString(), e);
        }
      }

      boolean releaseResource = false;
      try {
        Map<String, Object> config = note.getConfig();
        if (config != null && config.containsKey("releaseresource")) {
          releaseResource = (boolean) note.getConfig().get("releaseresource");
        }
      } catch (ClassCastException e) {
        logger.error(e.getMessage(), e);
      }
      if (releaseResource) {
        for (InterpreterSetting setting : notebook.getInterpreterFactory()
            .getInterpreterSettings(note.getId())) {
          notebook.getInterpreterFactory().restart(setting.getId());
        }
      }
    }
  }

  public void refreshCron(String id) {
    removeCron(id);
    synchronized (notes) {

      Note note = notes.get(id);
      if (note == null) {
        return;
      }
      Map<String, Object> config = note.getConfig();
      if (config == null) {
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
        logger.error("Error", e);
        info.put("cron", e.getMessage());
      }


      try {
        if (trigger != null) {
          quartzSched.scheduleJob(newJob, trigger);
        }
      } catch (SchedulerException e) {
        logger.error("Error", e);
        info.put("cron", "Scheduler Exception");
      }
    }
  }

  private void removeCron(String id) {
    try {
      quartzSched.deleteJob(new JobKey(id, "note"));
    } catch (SchedulerException e) {
      logger.error("Can't remove quertz " + id, e);
    }
  }

  public InterpreterFactory getInterpreterFactory() {
    return replFactory;
  }

  public NotebookAuthorization getNotebookAuthorization() {
    return notebookAuthorization;
  }

  public ZeppelinConfiguration getConf() {
    return conf;
  }

  public void close() {
    this.notebookRepo.close();
    this.notebookIndex.close();
  }

  public void addNotebookEventListener(NotebookEventListener listener) {
    notebookEventListeners.add(listener);
  }

  private void fireNoteCreateEvent(Note note) {
    for (NotebookEventListener listener : notebookEventListeners) {
      listener.onNoteCreate(note);
    }
  }

  private void fireNoteRemoveEvent(Note note) {
    for (NotebookEventListener listener : notebookEventListeners) {
      listener.onNoteRemove(note);
    }
  }

  private void fireUnbindInterpreter(Note note, InterpreterSetting setting) {
    for (NotebookEventListener listener : notebookEventListeners) {
      listener.onUnbindInterpreter(note, setting);
    }
  }

  @Override
  public void onParagraphRemove(Paragraph p) {
    for (NotebookEventListener listener : notebookEventListeners) {
      listener.onParagraphRemove(p);
    }
  }

  @Override
  public void onParagraphCreate(Paragraph p) {
    for (NotebookEventListener listener : notebookEventListeners) {
      listener.onParagraphCreate(p);
    }
  }

  @Override
  public void onParagraphStatusChange(Paragraph p, Job.Status status) {
    for (NotebookEventListener listener : notebookEventListeners) {
      listener.onParagraphStatusChange(p, status);
    }
  }
}
