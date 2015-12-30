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
import java.util.*;
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

/**
 * Collection of Notes.
 */
public class Notebook {
  Logger logger = LoggerFactory.getLogger(Notebook.class);

  @SuppressWarnings("unused") @Deprecated //TODO(bzz): remove unused
  private SchedulerFactory schedulerFactory;

  private InterpreterFactory replFactory;
  /** Keep the order. */
  Map<String, Map<String, Note>> notes = new LinkedHashMap<>();
  private ZeppelinConfiguration conf;
  private StdSchedulerFactory quertzSchedFact;
  private org.quartz.Scheduler quartzSched;
  private JobListenerFactory jobListenerFactory;
  private NotebookRepo notebookRepo;
  private SearchService notebookIndex;

  /**
   * Main constructor \w manual Dependency Injection
   *
   * @param conf
   * @param notebookRepo
   * @param schedulerFactory
   * @param replFactory
   * @param jobListenerFactory
   * @param notebookIndex - (nullable) for indexing all notebooks on creating.
   *
   * @throws IOException
   * @throws SchedulerException
   */
  public Notebook(ZeppelinConfiguration conf, NotebookRepo notebookRepo,
      SchedulerFactory schedulerFactory,
      InterpreterFactory replFactory, JobListenerFactory jobListenerFactory,
      SearchService notebookIndex) throws IOException, SchedulerException {
    this.conf = conf;
    this.notebookRepo = notebookRepo;
    this.schedulerFactory = schedulerFactory;
    this.replFactory = replFactory;
    this.jobListenerFactory = jobListenerFactory;
    this.notebookIndex = notebookIndex;
    quertzSchedFact = new org.quartz.impl.StdSchedulerFactory();
    quartzSched = quertzSchedFact.getScheduler();
    quartzSched.start();
    CronJob.notebook = this;

    loadAllNotes();
    if (this.notebookIndex != null) {
      long start = System.nanoTime();
      logger.info("Notebook indexing started...");
      Collection<Note> notesToIndex = new ArrayList<>();
      for (Map<String, Note> userNotes : notes.values()) {
        notesToIndex.addAll(userNotes.values());
      }
      notebookIndex.addIndexDocs(notesToIndex);
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
  public Note createNote(String principal) throws IOException {
    Note note;
    if (conf.getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_AUTO_INTERPRETER_BINDING)) {
      note = createNote(replFactory.getDefaultInterpreterSettingList(), principal);
    } else {
      note = createNote(null, principal);
    }
    notebookIndex.addIndexDoc(note);
    return note;
  }

  private Map<String, Note> getUserNotes(String principal) {
    synchronized (notes) {
      Map<String, Note> userNotes = notes.get(principal);
      if (userNotes == null)
        userNotes = new HashMap<>();
      notes.put(principal, userNotes);
      return userNotes;
    }
  }
  /**
   * Create new note.
   *
   * @return
   * @throws IOException
   */
  public Note createNote(List<String> interpreterIds, String principal) throws IOException {
    NoteInterpreterLoader intpLoader = new NoteInterpreterLoader(replFactory);
    Note note = new Note(notebookRepo, intpLoader, jobListenerFactory, notebookIndex, principal);
    intpLoader.setNoteId(note.id());
    synchronized (notes) {
      getUserNotes(principal).put(note.id(), note);
    }
    if (interpreterIds != null) {
      bindInterpretersToNote(note.id(), interpreterIds, principal);
    }

    notebookIndex.addIndexDoc(note);
    note.persist();
    return note;
  }

  /**
   * Clone existing note.
   * @param sourceNoteID - the note ID to clone
   * @param newNoteName - the name of the new note
   * @return noteId
   * @throws IOException, CloneNotSupportedException, IllegalArgumentException
   */
  public Note cloneNote(String sourceNoteID, String newNoteName, String principal) throws
      IOException, CloneNotSupportedException, IllegalArgumentException {

    Note sourceNote = getNote(sourceNoteID, principal);
    if (sourceNote == null) {
      throw new IllegalArgumentException(sourceNoteID + "not found");
    }
    Note newNote = createNote(principal);
    if (newNoteName != null) {
      newNote.setName(newNoteName);
    }
    // Copy the interpreter bindings
    List<String> boundInterpreterSettingsIds =
        getBindedInterpreterSettingsIds(sourceNote.id(), principal);
    bindInterpretersToNote(newNote.id(), boundInterpreterSettingsIds, principal);

    List<Paragraph> paragraphs = sourceNote.getParagraphs();
    for (Paragraph p : paragraphs) {
      newNote.addCloneParagraph(p);
    }

    notebookIndex.addIndexDoc(newNote);
    newNote.persist();
    return newNote;
  }

  public void bindInterpretersToNote(String id,
      List<String> interpreterSettingIds, String principal) throws IOException {
    Note note = getNote(id, principal);
    if (note != null) {
      note.getNoteReplLoader().setInterpreters(interpreterSettingIds);
      replFactory.putNoteInterpreterSettingBinding(id, interpreterSettingIds);
    }
  }

  public List<String> getBindedInterpreterSettingsIds(String id, String principal) {
    Note note = getNote(id, principal);
    if (note != null) {
      return note.getNoteReplLoader().getInterpreters();
    } else {
      return new LinkedList<String>();
    }
  }

  public List<InterpreterSetting> getBindedInterpreterSettings(String id, String principal) {
    Note note = getNote(id, principal);
    if (note != null) {
      return note.getNoteReplLoader().getInterpreterSettings();
    } else {
      return new LinkedList<InterpreterSetting>();
    }
  }

  public Note getNote(String id, String principal) {
    synchronized (notes) {
      return getUserNotes(principal).get(id);
    }
  }

  public void removeNote(String id, String principal) {
    Note note;

    synchronized (notes) {
      note = getUserNotes(principal).remove(id);
    }
    notebookIndex.deleteIndexDocs(note);

    // remove from all interpreter instance's angular object registry
    for (InterpreterSetting settings : replFactory.get()) {
      AngularObjectRegistry registry = settings.getInterpreterGroup().getAngularObjectRegistry();
      if (registry instanceof RemoteAngularObjectRegistry) {
        ((RemoteAngularObjectRegistry) registry).removeAllAndNotifyRemoteProcess(id);
      } else {
        registry.removeAll(id);
      }
    }

    try {
      note.unpersist();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @SuppressWarnings("rawtypes")
  private Note loadNoteFromRepo(String id, String owner) {
    Note note = null;
    try {
      note = notebookRepo.get(id, owner);
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
      getUserNotes(owner).put(note.id(), note);
      refreshCron(note.id(), owner);
    }

    for (String name : angularObjectSnapshot.keySet()) {
      SnapshotAngularObject snapshot = angularObjectSnapshot.get(name);
      List<InterpreterSetting> settings = replFactory.get();
      for (InterpreterSetting setting : settings) {
        InterpreterGroup intpGroup = setting.getInterpreterGroup();
        if (intpGroup.getId().equals(snapshot.getIntpGroupId())) {
          AngularObjectRegistry registry = intpGroup.getAngularObjectRegistry();
          String noteId = snapshot.getAngularObject().getNoteId();
          // at this point, remote interpreter process is not created.
          // so does not make sense add it to the remote.
          //
          // therefore instead of addAndNotifyRemoteProcess(), need to use add()
          // that results add angularObject only in ZeppelinServer side not remoteProcessSide
          registry.add(name, snapshot.getAngularObject().get(), noteId);
        }
      }
    }
    return note;
  }

  private void loadAllNotes() throws IOException {
    List<NoteInfo> noteInfos = notebookRepo.list();
    for (NoteInfo info : noteInfos) {
      loadNoteFromRepo(info.getId(), info.getOwner());
    }
  }

  /**
   * Reload all notes from repository after clearing `notes`
   * to reflect the changes of added/deleted/modified notebooks on file system level.
   *
   * @return
   * @throws IOException
   */
  private void reloadAllNotes() throws IOException {
    synchronized (notes) {
      notes.clear();
    }
    List<NoteInfo> noteInfos = notebookRepo.list();
    for (NoteInfo info : noteInfos) {
      loadNoteFromRepo(info.getId(), info.getOwner());
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

  public List<Note> getAllNotes(String principal) {
    synchronized (notes) {
      List<Note> noteList = new ArrayList<>(getUserNotes(principal).values());
      Collections.sort(noteList, new Comparator() {
        @Override
        public int compare(Object one, Object two) {
          Note note1 = (Note) one;
          Note note2 = (Note) two;

          String name1 = note1.id();
          if (note1.getName() != null) {
            name1 = note1.getName();
          }
          String name2 = note2.id();
          if (note2.getName() != null) {
            name2 = note2.getName();
          }
          ((Note) one).getName();
          return name1.compareTo(name2);
        }
      });
      return noteList;
    }
  }

  public List<Note> getAllNotes() {
    if (conf.getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_RELOAD_FROM_STORAGE)) {
      try {
        reloadAllNotes();
      } catch (IOException e) {
        logger.error("Cannot reload notes from storage", e);
      }
    }
    synchronized (notes) {
      Collection<Map<String, Note>> usersNotes = notes.values();
      List<Note> noteList = new ArrayList<>();
      for (Map<String, Note> userNotes : usersNotes) {
        noteList.addAll(userNotes.values());
      }
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
      String principal = context.getJobDetail().getJobDataMap().getString("principal");
      String noteId = context.getJobDetail().getJobDataMap().getString("noteId");
      Note note = notebook.getNote(noteId, principal);
      note.runAll();
    
      while (!note.getLastParagraph().isTerminated()) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      
      boolean releaseResource = false;
      try {
        releaseResource = (boolean) note.getConfig().get("releaseresource");
      } catch (java.lang.ClassCastException e) {
        e.printStackTrace();
      }
      if (releaseResource) {
        for (InterpreterSetting setting : note.getNoteReplLoader().getInterpreterSettings()) {
          notebook.getInterpreterFactory().restart(setting.id());
        }
      }      
    }
  }

  public void refreshCron(String id, String principal) {
    removeCron(id);
    synchronized (notes) {

      Note note = getUserNotes(principal).get(id);
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
          JobBuilder.newJob(CronJob.class)
            .withIdentity(id, "note")
            .usingJobData("noteId", id)
            .usingJobData("principal", principal)
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

  public ZeppelinConfiguration getConf() {
    return conf;
  }

  public void close() {
    this.notebookRepo.close();
    this.notebookIndex.close();
  }

}
