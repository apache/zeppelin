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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
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
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepo.Revision;
import org.apache.zeppelin.notebook.repo.NotebookRepoSync;
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
  private InterpreterSettingManager interpreterSettingManager;
  /**
   * Keep the order.
   */
  private final Map<String, Note> notes = new LinkedHashMap<>();
  private final FolderView folders = new FolderView();
  private ZeppelinConfiguration conf;
  private StdSchedulerFactory quertzSchedFact;
  private org.quartz.Scheduler quartzSched;
  private JobListenerFactory jobListenerFactory;
  private NotebookRepo notebookRepo;
  private SearchService noteSearchService;
  private NotebookAuthorization notebookAuthorization;
  private final List<NotebookEventListener> notebookEventListeners =
      Collections.synchronizedList(new LinkedList<NotebookEventListener>());
  private Credentials credentials;

  /**
   * Main constructor \w manual Dependency Injection
   *
   * @param noteSearchService         - (nullable) for indexing all notebooks on creating.
   * @throws IOException
   * @throws SchedulerException
   */
  public Notebook(ZeppelinConfiguration conf, NotebookRepo notebookRepo,
      SchedulerFactory schedulerFactory, InterpreterFactory replFactory,
      InterpreterSettingManager interpreterSettingManager, JobListenerFactory jobListenerFactory,
      SearchService noteSearchService, NotebookAuthorization notebookAuthorization,
      Credentials credentials) throws IOException, SchedulerException {
    this.conf = conf;
    this.notebookRepo = notebookRepo;
    this.schedulerFactory = schedulerFactory;
    this.replFactory = replFactory;
    this.interpreterSettingManager = interpreterSettingManager;
    this.jobListenerFactory = jobListenerFactory;
    this.noteSearchService = noteSearchService;
    this.notebookAuthorization = notebookAuthorization;
    this.credentials = credentials;
    quertzSchedFact = new org.quartz.impl.StdSchedulerFactory();
    quartzSched = quertzSchedFact.getScheduler();
    quartzSched.start();
    CronJob.notebook = this;

    AuthenticationInfo anonymous = AuthenticationInfo.ANONYMOUS;
    loadAllNotes(anonymous);
    if (this.noteSearchService != null) {
      long start = System.nanoTime();
      logger.info("Notebook indexing started...");
      noteSearchService.addIndexDocs(notes.values());
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
    Preconditions.checkNotNull(subject, "AuthenticationInfo should not be null");
    Note note;
    if (conf.getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_AUTO_INTERPRETER_BINDING)) {
      note = createNote(interpreterSettingManager.getInterpreterSettingIds(), subject);
    } else {
      note = createNote(null, subject);
    }
    noteSearchService.addIndexDoc(note);
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
        new Note(notebookRepo, replFactory, interpreterSettingManager, jobListenerFactory,
                noteSearchService, credentials, this);
    note.setNoteNameListener(folders);

    synchronized (notes) {
      notes.put(note.getId(), note);
    }
    if (interpreterIds != null) {
      bindInterpretersToNote(subject.getUser(), note.getId(), interpreterIds);
    }

    notebookAuthorization.setNewNotePermissions(note.getId(), subject);
    noteSearchService.addIndexDoc(note);
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
    Note note = getNote(noteId);
    if (note == null) {
      throw new IllegalArgumentException(noteId + " not found");
    }
    return note.toJson();
  }

  /**
   * import JSON as a new note.
   *
   * @param sourceJson - the note JSON to import
   * @param noteName   - the name of the new note
   * @return note ID
   * @throws IOException
   */
  public Note importNote(String sourceJson, String noteName, AuthenticationInfo subject)
      throws IOException {
    Note newNote;
    try {
      Note oldNote = Note.fromJson(sourceJson);
      convertFromSingleResultToMultipleResultsFormat(oldNote);
      newNote = createNote(subject);
      if (noteName != null)
        newNote.setName(noteName);
      else
        newNote.setName(oldNote.getName());
      List<Paragraph> paragraphs = oldNote.getParagraphs();
      for (Paragraph p : paragraphs) {
        newNote.addCloneParagraph(p);
      }

      notebookAuthorization.setNewNotePermissions(newNote.getId(), subject);
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
   * @param sourceNoteId - the note ID to clone
   * @param newNoteName  - the name of the new note
   * @return noteId
   * @throws IOException, CloneNotSupportedException, IllegalArgumentException
   */
  public Note cloneNote(String sourceNoteId, String newNoteName, AuthenticationInfo subject)
      throws IOException, CloneNotSupportedException, IllegalArgumentException {

    Note sourceNote = getNote(sourceNoteId);
    if (sourceNote == null) {
      throw new IllegalArgumentException(sourceNoteId + "not found");
    }
    Note newNote = createNote(subject);
    if (newNoteName != null) {
      newNote.setName(newNoteName);
    } else {
      newNote.setName("Note " + newNote.getId());
    }
    // Copy the interpreter bindings
    List<String> boundInterpreterSettingsIds = getBindedInterpreterSettingsIds(sourceNote.getId());
    bindInterpretersToNote(subject.getUser(), newNote.getId(), boundInterpreterSettingsIds);

    List<Paragraph> paragraphs = sourceNote.getParagraphs();
    for (Paragraph p : paragraphs) {
      newNote.addCloneParagraph(p);
    }

    noteSearchService.addIndexDoc(newNote);
    newNote.persist(subject);
    return newNote;
  }

  public void bindInterpretersToNote(String user, String id, List<String> interpreterSettingIds)
      throws IOException {
    Note note = getNote(id);
    if (note != null) {
      List<InterpreterSetting> currentBindings =
          interpreterSettingManager.getInterpreterSettings(id);
      for (InterpreterSetting setting : currentBindings) {
        if (!interpreterSettingIds.contains(setting.getId())) {
          fireUnbindInterpreter(note, setting);
        }
      }

      interpreterSettingManager.setInterpreterBinding(user, note.getId(), interpreterSettingIds);
      // comment out while note.getNoteReplLoader().setInterpreterBinding(...) do the same
      // replFactory.putNoteInterpreterSettingBinding(id, interpreterSettingIds);
    }
  }

  List<String> getBindedInterpreterSettingsIds(String id) {
    Note note = getNote(id);
    if (note != null) {
      return interpreterSettingManager.getInterpreterBinding(note.getId());
    } else {
      return new LinkedList<>();
    }
  }

  public List<InterpreterSetting> getBindedInterpreterSettings(String id) {
    Note note = getNote(id);
    if (note != null) {
      return interpreterSettingManager.getInterpreterSettings(note.getId());
    } else {
      return new LinkedList<>();
    }
  }

  public Note getNote(String id) {
    synchronized (notes) {
      return notes.get(id);
    }
  }

  public Folder getFolder(String folderId) {
    synchronized (folders) {
      return folders.getFolder(folderId);
    }
  }

  public boolean hasFolder(String folderId) {
    synchronized (folders) {
      return folders.hasFolder(folderId);
    }
  }

  public void moveNoteToTrash(String noteId) {
    try {
      interpreterSettingManager.setInterpreterBinding("", noteId, new ArrayList<String>());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void removeNote(String id, AuthenticationInfo subject) {
    Preconditions.checkNotNull(subject, "AuthenticationInfo should not be null");

    Note note;

    synchronized (notes) {
      note = notes.remove(id);
      folders.removeNote(note);
    }
    try {
      interpreterSettingManager.removeNoteInterpreterSettingBinding(subject.getUser(), id);
    } catch (IOException e) {
      logger.error(e.toString(), e);
    }
    noteSearchService.deleteIndexDocs(note);
    notebookAuthorization.removeNote(id);

    // remove from all interpreter instance's angular object registry
    for (InterpreterSetting settings : interpreterSettingManager.get()) {
      InterpreterGroup interpreterGroup = settings.getInterpreterGroup(subject.getUser(), id);
      if (interpreterGroup != null) {
        AngularObjectRegistry registry = interpreterGroup.getAngularObjectRegistry();
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
          // remove note scope object
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
          // remove note scope object
          registry.removeAll(id, null);
        }
      }
    }

    interpreterSettingManager.removeResourcesBelongsToNote(id);

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

  public List<Revision> listRevisionHistory(String noteId,
      AuthenticationInfo subject) {
    return notebookRepo.revisionHistory(noteId, subject);
  }

  public Note setNoteRevision(String noteId, String revisionId, AuthenticationInfo subject)
      throws IOException {
    return notebookRepo.setNoteRevision(noteId, revisionId, subject);
  }
  
  public Note getNoteByRevision(String noteId, String revisionId, AuthenticationInfo subject)
      throws IOException {
    return notebookRepo.get(noteId, revisionId, subject);
  }

  public void convertFromSingleResultToMultipleResultsFormat(Note note) {
    for (Paragraph p : note.paragraphs) {
      Object ret = p.getPreviousResultFormat();
      if (ret != null && p.results != null) {
        continue; // already converted
      }

      try {
        if (ret != null && ret instanceof Map) {
          Map r = ((Map) ret);
          if (r.containsKey("code") &&
              r.containsKey("msg") &&
              r.containsKey("type")) { // all three fields exists in sinle result format

            InterpreterResult.Code code = InterpreterResult.Code.valueOf((String) r.get("code"));
            InterpreterResult.Type type = InterpreterResult.Type.valueOf((String) r.get("type"));
            String msg = (String) r.get("msg");
            InterpreterResult result = new InterpreterResult(code, msg);
            if (result.message().size() == 1) {
              result = new InterpreterResult(code);
              result.add(type, msg);
            }
            p.setResult(result);

            // convert config
            Map<String, Object> config = p.getConfig();
            Object graph = config.remove("graph");
            Object apps = config.remove("apps");
            Object helium = config.remove("helium");

            List<Object> results = new LinkedList<>();
            for (int i = 0; i < result.message().size(); i++) {
              if (i == result.message().size() - 1) {
                HashMap<Object, Object> res = new HashMap<>();
                res.put("graph", graph);
                res.put("apps", apps);
                res.put("helium", helium);
                results.add(res);
              } else {
                results.add(new HashMap<>());
              }
            }
            config.put("results", results);
          }
        } else if (ret == null && p.getConfig() != null) {
          //ZEPPELIN-3063 Notebook loses formatting when importing from 0.6.x
          if (p.getConfig().get("graph") != null && p.getConfig().get("graph") instanceof Map
            && !((Map) p.getConfig().get("graph")).get("mode").equals("table")) {
            Map<String, Object> config = p.getConfig();
            Object graph = config.remove("graph");
            Object apps = config.remove("apps");
            Object helium = config.remove("helium");

            List<Object> results = new LinkedList<>();

            HashMap<Object, Object> res = new HashMap<>();
            res.put("graph", graph);
            res.put("apps", apps);
            res.put("helium", helium);
            results.add(res);

            config.put("results", results);
          }
        }
      } catch (Exception e) {
        logger.error("Conversion failure", e);
      }
    }
  }

  @SuppressWarnings("rawtypes")
  public Note loadNoteFromRepo(String id, AuthenticationInfo subject) {
    Note note = null;
    try {
      note = notebookRepo.get(id, subject);
    } catch (IOException e) {
      logger.error("Failed to load " + id, e);
    }
    if (note == null) {
      return null;
    }

    convertFromSingleResultToMultipleResultsFormat(note);

    //Manually inject ALL dependencies, as DI constructor was NOT used
    note.setIndex(this.noteSearchService);
    note.setCredentials(this.credentials);

    note.setInterpreterFactory(replFactory);
    note.setInterpreterSettingManager(interpreterSettingManager);

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
      p.clearRuntimeInfo(null);
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
    note.setNoteNameListener(folders);

    synchronized (notes) {
      notes.put(note.getId(), note);
      folders.putNote(note);
      refreshCron(note.getId());
    }

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

  void loadAllNotes(AuthenticationInfo subject) throws IOException {
    List<NoteInfo> noteInfos = notebookRepo.list(subject);

    for (NoteInfo info : noteInfos) {
      loadNoteFromRepo(info.getId(), subject);
    }
  }

  /**
   * Reload all notes from repository after clearing `notes` and `folders`
   * to reflect the changes of added/deleted/modified notes on file system level.
   *
   * @throws IOException
   */
  public void reloadAllNotes(AuthenticationInfo subject) throws IOException {
    synchronized (notes) {
      notes.clear();
    }
    synchronized (folders) {
      folders.clear();
    }

    if (notebookRepo instanceof NotebookRepoSync) {
      NotebookRepoSync mainRepo = (NotebookRepoSync) notebookRepo;
      if (mainRepo.getRepoCount() > 1) {
        mainRepo.sync(subject);
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

  public Folder renameFolder(String oldFolderId, String newFolderId) {
    return folders.renameFolder(oldFolderId, newFolderId);
  }

  public List<Note> getNotesUnderFolder(String folderId) {
    return folders.getFolder(folderId).getNotesRecursively();
  }

  public List<Note> getAllNotes() {
    synchronized (notes) {
      List<Note> noteList = new ArrayList<>(notes.values());
      Collections.sort(noteList, new Comparator<Note>() {
        @Override
        public int compare(Note note1, Note note2) {
          String name1 = note1.getId();
          if (note1.getName() != null) {
            name1 = note1.getName();
          }
          String name2 = note2.getId();
          if (note2.getName() != null) {
            name2 = note2.getName();
          }
          return name1.compareTo(name2);
        }
      });
      return noteList;
    }
  }

  public List<Note> getAllNotes(Set<String> userAndRoles) {
    final Set<String> entities = Sets.newHashSet();
    if (userAndRoles != null) {
      entities.addAll(userAndRoles);
    }

    synchronized (notes) {
      return FluentIterable.from(notes.values()).filter(new Predicate<Note>() {
        @Override
        public boolean apply(Note input) {
          return input != null && notebookAuthorization.isReader(input.getId(), entities);
        }
      }).toSortedList(new Comparator<Note>() {
        @Override
        public int compare(Note note1, Note note2) {
          String name1 = note1.getId();
          if (note1.getName() != null) {
            name1 = note1.getName();
          }
          String name2 = note2.getId();
          if (note2.getName() != null) {
            name2 = note2.getName();
          }
          return name1.compareTo(name2);
        }
      });
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

  public List<Map<String, Object>> getJobListByParagraphId(String paragraphId) {
    String gotNoteId = null;
    List<Note> notes = getAllNotes();
    for (Note note : notes) {
      Paragraph p = note.getParagraph(paragraphId);
      if (p != null) {
        gotNoteId = note.getId();
      }
    }
    return getJobListByNoteId(gotNoteId);
  }

  public List<Map<String, Object>> getJobListByNoteId(String noteId) {
    final String CRON_TYPE_NOTE_KEYWORD = "cron";
    long lastRunningUnixTime = 0;
    boolean isNoteRunning = false;
    Note jobNote = getNote(noteId);
    List<Map<String, Object>> notesInfo = new LinkedList<>();
    if (jobNote == null) {
      return notesInfo;
    }

    Map<String, Object> info = new HashMap<>();

    info.put("noteId", jobNote.getId());
    String noteName = jobNote.getName();
    if (noteName != null && !noteName.equals("")) {
      info.put("noteName", jobNote.getName());
    } else {
      info.put("noteName", "Note " + jobNote.getId());
    }
    // set note type ( cron or normal )
    if (jobNote.getConfig().containsKey(CRON_TYPE_NOTE_KEYWORD) && !jobNote.getConfig()
            .get(CRON_TYPE_NOTE_KEYWORD).equals("")) {
      info.put("noteType", "cron");
    } else {
      info.put("noteType", "normal");
    }

    // set paragraphs
    List<Map<String, Object>> paragraphsInfo = new LinkedList<>();
    for (Paragraph paragraph : jobNote.getParagraphs()) {
      // check paragraph's status.
      if (paragraph.getStatus().isRunning()) {
        isNoteRunning = true;
      }

      // get data for the job manager.
      Map<String, Object> paragraphItem = getParagraphForJobManagerItem(paragraph);
      lastRunningUnixTime = getUnixTimeLastRunParagraph(paragraph);

      paragraphsInfo.add(paragraphItem);
    }

    // set interpreter bind type
    String interpreterGroupName = null;
    if (interpreterSettingManager.getInterpreterSettings(jobNote.getId()) != null
            && interpreterSettingManager.getInterpreterSettings(jobNote.getId()).size() >= 1) {
      interpreterGroupName =
          interpreterSettingManager.getInterpreterSettings(jobNote.getId()).get(0).getName();
    }

    // note json object root information.
    info.put("interpreter", interpreterGroupName);
    info.put("isRunningJob", isNoteRunning);
    info.put("unixTimeLastRun", lastRunningUnixTime);
    info.put("paragraphs", paragraphsInfo);
    notesInfo.add(info);

    return notesInfo;
  };

  public List<Map<String, Object>> getJobListByUnixTime(boolean needsReload,
      long lastUpdateServerUnixTime, AuthenticationInfo subject) {
    final String CRON_TYPE_NOTE_KEYWORD = "cron";

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
      boolean isNoteRunning = false;
      boolean isUpdateNote = false;
      long lastRunningUnixTime = 0;
      Map<String, Object> info = new HashMap<>();

      // set note ID
      info.put("noteId", note.getId());

      // set note Name
      String noteName = note.getName();
      if (noteName != null && !noteName.equals("")) {
        info.put("noteName", note.getName());
      } else {
        info.put("noteName", "Note " + note.getId());
      }

      // set note type ( cron or normal )
      if (note.getConfig().containsKey(CRON_TYPE_NOTE_KEYWORD) && !note.getConfig()
          .get(CRON_TYPE_NOTE_KEYWORD).equals("")) {
        info.put("noteType", "cron");
      } else {
        info.put("noteType", "normal");
      }

      // set paragraphs
      List<Map<String, Object>> paragraphsInfo = new LinkedList<>();
      for (Paragraph paragraph : note.getParagraphs()) {
        // check paragraph's status.
        if (paragraph.getStatus().isRunning()) {
          isNoteRunning = true;
          isUpdateNote = true;
        }

        // get data for the job manager.
        Map<String, Object> paragraphItem = getParagraphForJobManagerItem(paragraph);
        lastRunningUnixTime = Math.max(getUnixTimeLastRunParagraph(paragraph), lastRunningUnixTime);

        // is update note for last server update time.
        if (lastRunningUnixTime > lastUpdateServerUnixTime) {
          isUpdateNote = true;
        }
        paragraphsInfo.add(paragraphItem);
      }

      // set interpreter bind type
      String interpreterGroupName = null;
      if (interpreterSettingManager.getInterpreterSettings(note.getId()) != null
          && interpreterSettingManager.getInterpreterSettings(note.getId()).size() >= 1) {
        interpreterGroupName =
            interpreterSettingManager.getInterpreterSettings(note.getId()).get(0).getName();
      }

      // not update and not running -> pass
      if (!isUpdateNote && !isNoteRunning) {
        continue;
      }

      // note json object root information.
      info.put("interpreter", interpreterGroupName);
      info.put("isRunningJob", isNoteRunning);
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

      if (note.isRunningOrPending()) {
        logger.warn("execution of the cron job is skipped because there is a running or pending " +
            "paragraph (note id: {})", noteId);
        return;
      }

      note.runAll();

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
        logger.error(e.getMessage(), e);
      }
      if (releaseResource) {
        for (InterpreterSetting setting : notebook.getInterpreterSettingManager()
            .getInterpreterSettings(note.getId())) {
          try {
            notebook.getInterpreterSettingManager().restart(setting.getId(), noteId,
                    cronExecutingUser != null ? cronExecutingUser : "anonymous");
          } catch (InterpreterException e) {
            logger.error("Fail to restart interpreter: " + setting.getId(), e);
          }
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

  public void removeCron(String id) {
    try {
      quartzSched.deleteJob(new JobKey(id, "note"));
    } catch (SchedulerException e) {
      logger.error("Can't remove quertz " + id, e);
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
