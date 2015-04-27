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

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
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
  private SchedulerFactory schedulerFactory;
  private InterpreterFactory replFactory;
  /** Keep the order. */
  Map<String, Map<String, Note>> notes = new LinkedHashMap<>();
  private ZeppelinConfiguration conf;
  private StdSchedulerFactory quertzSchedFact;
  private org.quartz.Scheduler quartzSched;
  private JobListenerFactory jobListenerFactory;

  public Notebook(ZeppelinConfiguration conf, SchedulerFactory schedulerFactory,
      InterpreterFactory replFactory, JobListenerFactory jobListenerFactory) throws IOException,
      SchedulerException {
    this.conf = conf;
    this.schedulerFactory = schedulerFactory;
    this.replFactory = replFactory;
    this.jobListenerFactory = jobListenerFactory;
    quertzSchedFact = new StdSchedulerFactory();
    quartzSched = quertzSchedFact.getScheduler();
    quartzSched.start();
    CronJob.notebook = this;

    loadAllNotes();
  }

  /**
   * Create new note.
   *
   * @return
   * @throws IOException
   */
  public Note createNote(String principal) throws IOException {
    if (conf.getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_AUTO_INTERPRETER_BINDING)) {
      return createNote(replFactory.getDefaultInterpreterSettingList(), principal);
    } else {
      return createNote(null, principal);
    }
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
    Note note = new Note(conf, intpLoader, jobListenerFactory, quartzSched, principal);
    intpLoader.setNoteId(note.id());
    synchronized (notes) {
      getUserNotes(principal).put(note.id(), note);
    }
    if (interpreterIds != null) {
      bindInterpretersToNote(note.id(), interpreterIds, principal);
    }

    return note;
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
    try {
      note.unpersist();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void loadAllNotes() throws IOException {
    File notebookDir = new File(conf.getNotebookDir());
    logger.info("Notebook Directory " + notebookDir.getAbsolutePath());
    File[] userdirs = notebookDir.listFiles();
    if (userdirs == null) {
      return;
    }

    Map<String, SnapshotAngularObject> angularObjectSnapshot =
        new HashMap<String, SnapshotAngularObject>();

    for (File fuser : userdirs) {
      String principal = fuser.getName();
      boolean isHidden = principal.startsWith(".");
      if (fuser.isDirectory() && !isHidden) {
        File[] dirs = fuser.listFiles();
        if (dirs != null) {
          for (File f : dirs) {
            boolean isHiddenFile = f.getName().startsWith(".");
            if (f.isDirectory() && !isHiddenFile) {
              Scheduler scheduler =
                  schedulerFactory.createOrGetFIFOScheduler("note_" + System.currentTimeMillis());
              logger.info("Loading note from " + f.getName());
              NoteInterpreterLoader noteInterpreterLoader = new NoteInterpreterLoader(replFactory);
              Note note = Note.load(f.getName(),
                  conf,
                  noteInterpreterLoader,
                  scheduler,
                  jobListenerFactory, quartzSched, fuser.getName());
              logger.info("Loading note from " + f.getName());
              noteInterpreterLoader.setNoteId(note.id());

              // restore angular object --------------
              Date lastUpdatedDate = new Date(0);
              for (Paragraph p : note.getParagraphs()) {
                if (p.getDateFinished() != null &&
                    lastUpdatedDate.before(p.getDateFinished())) {
                  lastUpdatedDate = p.getDateFinished();
                }
              }

              Map<String, List<AngularObject>> savedObjects = note.getAngularObjects();

              if (savedObjects != null) {
                for (String intpGroupName : savedObjects.keySet()) {
                  List<AngularObject> objectList = savedObjects.get(intpGroupName);

                  for (AngularObject savedObject : objectList) {
                    SnapshotAngularObject snapshot =
                            angularObjectSnapshot.get(savedObject.getName());
                    if (snapshot == null || snapshot.getLastUpdate().before(lastUpdatedDate)) {
                      angularObjectSnapshot.put(
                          savedObject.getName(),
                          new SnapshotAngularObject(
                              intpGroupName,
                              savedObject,
                              lastUpdatedDate));
                    }
                  }
                }
              }

              synchronized (notes) {
                getUserNotes(principal).put(note.id(), note);
                refreshCron(note.id(), principal);
              }
            }
          }
        }
      }
    }

    for (String name : angularObjectSnapshot.keySet()) {
      SnapshotAngularObject snapshot = angularObjectSnapshot.get(name);
      List<InterpreterSetting> settings = replFactory.get();
      for (InterpreterSetting setting : settings) {
        InterpreterGroup intpGroup = setting.getInterpreterGroup();
        if (intpGroup.getId().equals(snapshot.getIntpGroupId())) {
          AngularObjectRegistry registry = intpGroup.getAngularObjectRegistry();
          if (registry.get(name) == null) {
            registry.add(name, snapshot.getAngularObject().get(), false);
          }
        }
      }
    }
  }

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
    synchronized (notes) {
      Collection<Map<String, Note>> usersNotes = notes.values();
      List<Note> noteList = new ArrayList<>();
      for (Map<String, Note> userNotes : usersNotes) {
        noteList.addAll(userNotes.values());
      }
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

  public JobListenerFactory getJobListenerFactory() {
    return jobListenerFactory;
  }

  public void setJobListenerFactory(JobListenerFactory jobListenerFactory) {
    this.jobListenerFactory = jobListenerFactory;
  }

  /**
   * Cron task for the note.
   *
   * @author Leemoonsoo
   *
   */
  public static class CronJob implements org.quartz.Job {
    public static Notebook notebook;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
      String principal = context.getJobDetail().getJobDataMap().getString("principal");
      String noteId = context.getJobDetail().getJobDataMap().getString("noteId");
      Note note = notebook.getNote(noteId, principal);
      note.runAll();
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


}
