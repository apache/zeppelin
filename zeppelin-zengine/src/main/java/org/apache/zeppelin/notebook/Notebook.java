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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepoSync;
import org.apache.zeppelin.resource.ResourcePoolUtils;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.search.SearchService;
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
/**
 * Collection of Notes.
 */
public class Notebook {
  static Logger logger = LoggerFactory.getLogger(Notebook.class);

  @SuppressWarnings("unused") @Deprecated //TODO(bzz): remove unused
  private SchedulerFactory schedulerFactory;

  private InterpreterFactory replFactory;
  /** Keep the order. */
  Map<String, Note> notes = new LinkedHashMap<String, Note>();
  private ZeppelinConfiguration conf;
  private StdSchedulerFactory quertzSchedFact;
  private org.quartz.Scheduler quartzSched;
  private JobListenerFactory jobListenerFactory;
  private NotebookRepo notebookRepo;
  private SearchService notebookIndex;
  private NotebookAuthorization notebookAuthorization;

  /**
   * Main constructor \w manual Dependency Injection
   *
   * @param conf
   * @param notebookRepo
   * @param schedulerFactory
   * @param replFactory
   * @param jobListenerFactory
   * @param notebookIndex - (nullable) for indexing all notebooks on creating.
   * @param notebookAuthorization
   *
   * @throws IOException
   * @throws SchedulerException
   */
  public Notebook(ZeppelinConfiguration conf, NotebookRepo notebookRepo,
      SchedulerFactory schedulerFactory,
      InterpreterFactory replFactory, JobListenerFactory jobListenerFactory,
      SearchService notebookIndex,
      NotebookAuthorization notebookAuthorization) throws IOException, SchedulerException {
    this.conf = conf;
    this.notebookRepo = notebookRepo;
    this.schedulerFactory = schedulerFactory;
    this.replFactory = replFactory;
    this.jobListenerFactory = jobListenerFactory;
    this.notebookIndex = notebookIndex;
    this.notebookAuthorization = notebookAuthorization;
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
   * @return
   * @throws IOException
   */
  public Note createNote() throws IOException {
    Note note;
    if (conf.getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_AUTO_INTERPRETER_BINDING)) {
      note = createNote(replFactory.getDefaultInterpreterSettingList());
    } else {
      note = createNote(null);
    }
    notebookIndex.addIndexDoc(note);
    return note;
  }

  /**
   * Create new note.
   *
   * @return
   * @throws IOException
   */
  public Note createNote(List<String> interpreterIds) throws IOException {
    NoteInterpreterLoader intpLoader = new NoteInterpreterLoader(replFactory);
    Note note = new Note(notebookRepo, intpLoader, jobListenerFactory, notebookIndex);
    intpLoader.setNoteId(note.id());
    synchronized (notes) {
      notes.put(note.id(), note);
    }
    if (interpreterIds != null) {
      bindInterpretersToNote(note.id(), interpreterIds);
    }

    notebookIndex.addIndexDoc(note);
    note.persist();
    return note;
  }
  
  /**
   * Export existing note.
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
   * @param sourceJson - the note JSON to import
   * @param noteName - the name of the new note
   * @return notebook ID
   * @throws IOException
   */
  public Note importNote(String sourceJson, String noteName) throws IOException {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    Gson gson = gsonBuilder.create();
    JsonReader reader = new JsonReader(new StringReader(sourceJson));
    reader.setLenient(true);
    Note newNote;
    try {
      Note oldNote = gson.fromJson(reader, Note.class);
      newNote = createNote();
      if (noteName != null)
        newNote.setName(noteName);
      else
        newNote.setName(oldNote.getName());
      List<Paragraph> paragraphs = oldNote.getParagraphs();
      for (Paragraph p : paragraphs) {
        newNote.addCloneParagraph(p);
      }

      newNote.persist();
    } catch (IOException e) {
      logger.error(e.toString(), e);
      throw e;
    }
    
    return newNote;
  }

  /**
   * Clone existing note.
   * @param sourceNoteID - the note ID to clone
   * @param newNoteName - the name of the new note
   * @return noteId
   * @throws IOException, CloneNotSupportedException, IllegalArgumentException
   */
  public Note cloneNote(String sourceNoteID, String newNoteName) throws
      IOException, CloneNotSupportedException, IllegalArgumentException {

    Note sourceNote = getNote(sourceNoteID);
    if (sourceNote == null) {
      throw new IllegalArgumentException(sourceNoteID + "not found");
    }
    Note newNote = createNote();
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
    newNote.persist();
    return newNote;
  }

  public void bindInterpretersToNote(String id,
      List<String> interpreterSettingIds) throws IOException {
    Note note = getNote(id);
    if (note != null) {
      note.getNoteReplLoader().setInterpreters(interpreterSettingIds);
      // comment out while note.getNoteReplLoader().setInterpreters(...) do the same
      // replFactory.putNoteInterpreterSettingBinding(id, interpreterSettingIds);
    }
  }

  public List<String> getBindedInterpreterSettingsIds(String id) {
    Note note = getNote(id);
    if (note != null) {
      return note.getNoteReplLoader().getInterpreters();
    } else {
      return new LinkedList<String>();
    }
  }

  public List<InterpreterSetting> getBindedInterpreterSettings(String id) {
    Note note = getNote(id);
    if (note != null) {
      return note.getNoteReplLoader().getInterpreterSettings();
    } else {
      return new LinkedList<InterpreterSetting>();
    }
  }

  public Note getNote(String id) {
    synchronized (notes) {
      return notes.get(id);
    }
  }

  public void removeNote(String id) {
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
        }
        // remove notebook scope object
        ((RemoteAngularObjectRegistry) registry).removeAllAndNotifyRemoteProcess(id, null);
      } else {
        // remove paragraph scope object
        for (Paragraph p : note.getParagraphs()) {
          registry.removeAll(id, p.getId());
        }
        // remove notebook scope object
        registry.removeAll(id, null);
      }
    }

    ResourcePoolUtils.removeResourcesBelongsToNote(id);

    try {
      note.unpersist();
    } catch (IOException e) {
      logger.error(e.toString(), e);
    }
  }

  public void checkpointNote(String noteId, String checkpointMessage) throws IOException {
    notebookRepo.checkpoint(noteId, checkpointMessage);
  }

  @SuppressWarnings("rawtypes")
  private Note loadNoteFromRepo(String id) {
    Note note = null;
    try {
      note = notebookRepo.get(id);
    } catch (IOException e) {
      logger.error("Failed to load " + id, e);
    }
    if (note == null) {
      return null;
    }

    //Manually inject ALL dependencies, as DI constructor was NOT used
    note.setIndex(this.notebookIndex);

    NoteInterpreterLoader replLoader = new NoteInterpreterLoader(replFactory);
    note.setReplLoader(replLoader);
    replLoader.setNoteId(note.id());

    note.setJobListenerFactory(jobListenerFactory);
    note.setNotebookRepo(notebookRepo);

    Map<String, SnapshotAngularObject> angularObjectSnapshot = new HashMap<>();

    // restore angular object --------------
    Date lastUpdatedDate = new Date(0);
    for (Paragraph p : note.getParagraphs()) {
      p.setNote(note);
      if (p.getDateFinished() != null &&
          lastUpdatedDate.before(p.getDateFinished())) {
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
    List<NoteInfo> noteInfos = notebookRepo.list();

    for (NoteInfo info : noteInfos) {
      loadNoteFromRepo(info.getId());
    }
  }

  /**
   * Reload all notes from repository after clearing `notes`
   * to reflect the changes of added/deleted/modified notebooks on file system level.
   *
   * @return
   * @throws IOException
   */
  public void reloadAllNotes() throws IOException {
    synchronized (notes) {
      notes.clear();
    }

    if (notebookRepo instanceof NotebookRepoSync) {
      NotebookRepoSync mainRepo = (NotebookRepoSync) notebookRepo;
      if (mainRepo.getRepoCount() > 1) {
        mainRepo.sync();
      }
    }

    List<NoteInfo> noteInfos = notebookRepo.list();
    for (NoteInfo info : noteInfos) {
      loadNoteFromRepo(info.getId());
    }
  }

  @SuppressWarnings("rawtypes")
  class SnapshotAngularObject {
    String intpGroupId;
    AngularObject angularObject;
    Date lastUpdate;

    public SnapshotAngularObject(String intpGroupId,
        AngularObject angularObject, Date lastUpdate) {
      super();
      this.intpGroupId = intpGroupId;
      this.angularObject = angularObject;
      this.lastUpdate = lastUpdate;
    }

    public String getIntpGroupId() {
      return intpGroupId;
    }
    public AngularObject getAngularObject() {
      return angularObject;
    }
    public Date getLastUpdate() {
      return lastUpdate;
    }
  }

  public List<Note> getAllNotes() {
    synchronized (notes) {
      List<Note> noteList = new ArrayList<Note>(notes.values());
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

  public JobListenerFactory getJobListenerFactory() {
    return jobListenerFactory;
  }

  public void setJobListenerFactory(JobListenerFactory jobListenerFactory) {
    this.jobListenerFactory = jobListenerFactory;
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
    
      while (!note.getLastParagraph().isTerminated()) {
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
        for (InterpreterSetting setting : note.getNoteReplLoader().getInterpreterSettings()) {
          notebook.getInterpreterFactory().restart(setting.id());
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
        trigger =
            TriggerBuilder.newTrigger().withIdentity("trigger_" + id, "note")
            .withSchedule(CronScheduleBuilder.cronSchedule(cronExpr)).forJob(id, "note")
            .build();
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

}
