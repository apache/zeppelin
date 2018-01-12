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

import static java.lang.String.format;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.common.JsonSerializable;
import org.apache.zeppelin.completer.CompletionType;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterInfo;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.utility.IdHashes;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Binded interpreters for a note
 */
public class Note implements ParagraphJobListener, JsonSerializable {
  private static final Logger logger = LoggerFactory.getLogger(Note.class);
  private static final long serialVersionUID = 7920699076577612429L;
  private static Gson gson = new GsonBuilder()
      .setPrettyPrinting()
      .setDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
      .registerTypeAdapter(Date.class, new NotebookImportDeserializer())
      .registerTypeAdapterFactory(Input.TypeAdapterFactory)
      .create();

  // threadpool for delayed persist of note
  private static final ScheduledThreadPoolExecutor delayedPersistThreadPool =
      new ScheduledThreadPoolExecutor(0);

  static {
    delayedPersistThreadPool.setRemoveOnCancelPolicy(true);
  }

  final List<Paragraph> paragraphs = new LinkedList<>();

  private String name = "";
  private String id;
  private Map<String, Object> noteParams = new HashMap<>();
  private LinkedHashMap<String, Input> noteForms = new LinkedHashMap<>();


  private transient ZeppelinConfiguration conf = ZeppelinConfiguration.create();

  private Map<String, List<AngularObject>> angularObjects = new HashMap<>();

  private transient InterpreterFactory factory;
  private transient InterpreterSettingManager interpreterSettingManager;
  private transient JobListenerFactory jobListenerFactory;
  private transient NotebookRepo repo;
  private transient SearchService index;
  private transient ScheduledFuture delayedPersist;
  private transient NoteEventListener noteEventListener;
  private transient Credentials credentials;
  private transient NoteNameListener noteNameListener;

  /*
   * note configurations.
   * - looknfeel - cron
   */
  private Map<String, Object> config = new HashMap<>();

  /*
   * note information.
   * - cron : cron expression validity.
   */
  private Map<String, Object> info = new HashMap<>();


  public Note() {
    generateId();
  }

  public Note(NotebookRepo repo, InterpreterFactory factory,
      InterpreterSettingManager interpreterSettingManager, JobListenerFactory jlFactory,
      SearchService noteIndex, Credentials credentials, NoteEventListener noteEventListener) {
    this.repo = repo;
    this.factory = factory;
    this.interpreterSettingManager = interpreterSettingManager;
    this.jobListenerFactory = jlFactory;
    this.index = noteIndex;
    this.noteEventListener = noteEventListener;
    this.credentials = credentials;
    generateId();
  }

  private void generateId() {
    id = IdHashes.generateId();
  }

  public boolean isPersonalizedMode() {
    Object v = getConfig().get("personalizedMode");
    return null != v && "true".equals(v);
  }

  public void setPersonalizedMode(Boolean value) {
    String valueString = StringUtils.EMPTY;
    if (value) {
      valueString = "true";
    } else {
      valueString = "false";
    }
    getConfig().put("personalizedMode", valueString);
    clearUserParagraphs(value);
  }

  private void clearUserParagraphs(boolean isPersonalized) {
    if (!isPersonalized) {
      for (Paragraph p : paragraphs) {
        p.clearUserParagraphs();
      }
    }
  }

  public String getId() {
    return id;
  }

  public String getName() {
    if (isNameEmpty()) {
      name = getId();
    }
    return name;
  }

  public Map<String, Object> getNoteParams() {
    return noteParams;
  }

  public void setNoteParams(Map<String, Object> noteParams) {
    this.noteParams = noteParams;
  }

  public LinkedHashMap<String, Input> getNoteForms() {
    return noteForms;
  }

  public void setNoteForms(LinkedHashMap<String, Input> noteForms) {
    this.noteForms = noteForms;
  }

  public String getNameWithoutPath() {
    String notePath = getName();

    int lastSlashIndex = notePath.lastIndexOf("/");
    // The note is in the root folder
    if (lastSlashIndex < 0) {
      return notePath;
    }

    return notePath.substring(lastSlashIndex + 1);
  }

  /**
   * @return normalized folder path, which is folderId
   */
  public String getFolderId() {
    String notePath = getName();

    // Ignore first '/'
    if (notePath.charAt(0) == '/')
      notePath = notePath.substring(1);

    int lastSlashIndex = notePath.lastIndexOf("/");
    // The root folder
    if (lastSlashIndex < 0) {
      return Folder.ROOT_FOLDER_ID;
    }

    String folderId = notePath.substring(0, lastSlashIndex);

    return folderId;
  }

  public boolean isNameEmpty() {
    return this.name.trim().isEmpty();
  }

  private String normalizeNoteName(String name) {
    name = name.trim();
    name = name.replace("\\", "/");
    while (name.contains("///")) {
      name = name.replaceAll("///", "/");
    }
    name = name.replaceAll("//", "/");
    if (name.length() == 0) {
      name = "/";
    }
    return name;
  }

  public void setName(String name) {
    String oldName = this.name;

    if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
      name = normalizeNoteName(name);
    }
    this.name = name;

    if (this.noteNameListener != null && !oldName.equals(name)) {
      noteNameListener.onNoteNameChanged(this, oldName);
    }
  }

  public void setNoteNameListener(NoteNameListener listener) {
    this.noteNameListener = listener;
  }

  public void setInterpreterFactory(InterpreterFactory factory) {
    this.factory = factory;
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        p.setInterpreterFactory(factory);
      }
    }
  }

  void setInterpreterSettingManager(InterpreterSettingManager interpreterSettingManager) {
    this.interpreterSettingManager = interpreterSettingManager;
  }

  public void initializeJobListenerForParagraph(Paragraph paragraph) {
    final Note paragraphNote = paragraph.getNote();
    if (!paragraphNote.getId().equals(this.getId())) {
      throw new IllegalArgumentException(
          format("The paragraph %s from note %s " + "does not belong to note %s", paragraph.getId(),
              paragraphNote.getId(), this.getId()));
    }

    boolean foundParagraph = false;
    for (Paragraph ownParagraph : paragraphs) {
      if (paragraph.getId().equals(ownParagraph.getId())) {
        paragraph.setListener(this.jobListenerFactory.getParagraphJobListener(this));
        foundParagraph = true;
      }
    }

    if (!foundParagraph) {
      throw new IllegalArgumentException(
          format("Cannot find paragraph %s " + "from note %s", paragraph.getId(),
              paragraphNote.getId()));
    }
  }

  void setJobListenerFactory(JobListenerFactory jobListenerFactory) {
    this.jobListenerFactory = jobListenerFactory;
  }

  void setNotebookRepo(NotebookRepo repo) {
    this.repo = repo;
  }

  public void setIndex(SearchService index) {
    this.index = index;
  }

  public Credentials getCredentials() {
    return credentials;
  }

  public void setCredentials(Credentials credentials) {
    this.credentials = credentials;
  }


  Map<String, List<AngularObject>> getAngularObjects() {
    return angularObjects;
  }

  /**
   * Create a new paragraph and add it to the end of the note.
   */
  public Paragraph addNewParagraph(AuthenticationInfo authenticationInfo) {
    return insertNewParagraph(paragraphs.size(), authenticationInfo);
  }

  /**
   * Clone paragraph and add it to note.
   *
   * @param srcParagraph source paragraph
   */
  void addCloneParagraph(Paragraph srcParagraph) {

    // Keep paragraph original ID
    final Paragraph newParagraph = new Paragraph(srcParagraph.getId(), this, this, factory);

    Map<String, Object> config = new HashMap<>(srcParagraph.getConfig());
    Map<String, Object> param = srcParagraph.settings.getParams();
    LinkedHashMap<String, Input> form = srcParagraph.settings.getForms();

    newParagraph.setConfig(config);
    newParagraph.settings.setParams(param);
    newParagraph.settings.setForms(form);
    newParagraph.setText(srcParagraph.getText());
    newParagraph.setTitle(srcParagraph.getTitle());

    try {
      Gson gson = new Gson();
      String resultJson = gson.toJson(srcParagraph.getReturn());
      InterpreterResult result = InterpreterResult.fromJson(resultJson);
      newParagraph.setReturn(result, null);
    } catch (Exception e) {
      // 'result' part of Note consists of exception, instead of actual interpreter results
      logger.warn(
          "Paragraph " + srcParagraph.getId() + " has a result with exception. " + e.getMessage());
    }

    synchronized (paragraphs) {
      paragraphs.add(newParagraph);
    }
    if (noteEventListener != null) {
      noteEventListener.onParagraphCreate(newParagraph);
    }
  }

  /**
   * Create a new paragraph and insert it to the note in given index.
   *
   * @param index index of paragraphs
   */
  public Paragraph insertNewParagraph(int index, AuthenticationInfo authenticationInfo) {
    Paragraph paragraph = createParagraph(index, authenticationInfo);
    insertParagraph(paragraph, index);
    return paragraph;
  }

  private Paragraph createParagraph(int index, AuthenticationInfo authenticationInfo) {
    Paragraph p = new Paragraph(this, this, factory);
    p.setAuthenticationInfo(authenticationInfo);
    setParagraphMagic(p, index);
    return p;
  }

  public void addParagraph(Paragraph paragraph) {
    insertParagraph(paragraph, paragraphs.size());
  }

  public void insertParagraph(Paragraph paragraph, int index) {
    synchronized (paragraphs) {
      paragraphs.add(index, paragraph);
    }
    if (noteEventListener != null) {
      noteEventListener.onParagraphCreate(paragraph);
    }
  }

  /**
   * Remove paragraph by id.
   *
   * @param paragraphId ID of paragraph
   * @return a paragraph that was deleted, or <code>null</code> otherwise
   */
  public Paragraph removeParagraph(String user, String paragraphId) {
    removeAllAngularObjectInParagraph(user, paragraphId);
    interpreterSettingManager.removeResourcesBelongsToParagraph(getId(), paragraphId);
    synchronized (paragraphs) {
      Iterator<Paragraph> i = paragraphs.iterator();
      while (i.hasNext()) {
        Paragraph p = i.next();
        if (p.getId().equals(paragraphId)) {
          index.deleteIndexDoc(this, p);
          i.remove();

          if (noteEventListener != null) {
            noteEventListener.onParagraphRemove(p);
          }
          return p;
        }
      }
    }
    return null;
  }

  public void clearParagraphOutputFields(Paragraph p) {
    p.setReturn(null, null);
    p.clearRuntimeInfo(null);
  }

  public Paragraph clearPersonalizedParagraphOutput(String paragraphId, String user) {
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        if (!p.getId().equals(paragraphId)) {
          continue;
        }

        p = p.getUserParagraphMap().get(user);
        clearParagraphOutputFields(p);
        return p;
      }
    }
    return null;
  }

  /**
   * Clear paragraph output by id.
   *
   * @param paragraphId ID of paragraph
   * @return Paragraph
   */
  public Paragraph clearParagraphOutput(String paragraphId) {
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        if (!p.getId().equals(paragraphId)) {
          continue;
        }

        clearParagraphOutputFields(p);
        return p;
      }
    }
    return null;
  }

  /**
   * Clear all paragraph output of note
   */
  public void clearAllParagraphOutput() {
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        p.setReturn(null, null);
      }
    }
  }

  /**
   * Move paragraph into the new index (order from 0 ~ n-1).
   *
   * @param paragraphId ID of paragraph
   * @param index       new index
   */
  public void moveParagraph(String paragraphId, int index) {
    moveParagraph(paragraphId, index, false);
  }

  /**
   * Move paragraph into the new index (order from 0 ~ n-1).
   *
   * @param paragraphId                ID of paragraph
   * @param index                      new index
   * @param throwWhenIndexIsOutOfBound whether throw IndexOutOfBoundException
   *                                   when index is out of bound
   */
  public void moveParagraph(String paragraphId, int index, boolean throwWhenIndexIsOutOfBound) {
    synchronized (paragraphs) {
      int oldIndex;
      Paragraph p = null;

      if (index < 0 || index >= paragraphs.size()) {
        if (throwWhenIndexIsOutOfBound) {
          throw new IndexOutOfBoundsException(
              "paragraph size is " + paragraphs.size() + " , index is " + index);
        } else {
          return;
        }
      }

      for (int i = 0; i < paragraphs.size(); i++) {
        if (paragraphs.get(i).getId().equals(paragraphId)) {
          oldIndex = i;
          if (oldIndex == index) {
            return;
          }
          p = paragraphs.remove(i);
        }
      }

      if (p != null) {
        paragraphs.add(index, p);
      }
    }
  }

  public boolean isLastParagraph(String paragraphId) {
    if (!paragraphs.isEmpty()) {
      synchronized (paragraphs) {
        if (paragraphId.equals(paragraphs.get(paragraphs.size() - 1).getId())) {
          return true;
        }
      }
      return false;
    }
    /** because empty list, cannot remove nothing right? */
    return true;
  }

  public int getParagraphCount() {
    return paragraphs.size();
  }

  public Paragraph getParagraph(String paragraphId) {
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        if (p.getId().equals(paragraphId)) {
          return p;
        }
      }
    }
    return null;
  }

  public Paragraph getLastParagraph() {
    synchronized (paragraphs) {
      return paragraphs.get(paragraphs.size() - 1);
    }
  }

  public List<Map<String, String>> generateParagraphsInfo() {
    List<Map<String, String>> paragraphsInfo = new LinkedList<>();
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        Map<String, String> info = populateParagraphInfo(p);
        paragraphsInfo.add(info);
      }
    }
    return paragraphsInfo;
  }

  public Map<String, String> generateSingleParagraphInfo(String paragraphId) {
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        if (p.getId().equals(paragraphId)) {
          return populateParagraphInfo(p);
        }
      }
      return new HashMap<>();
    }
  }

  private Map<String, String> populateParagraphInfo(Paragraph p) {
    Map<String, String> info = new HashMap<>();
    info.put("id", p.getId());
    info.put("status", p.getStatus().toString());
    if (p.getDateStarted() != null) {
      info.put("started", p.getDateStarted().toString());
    }
    if (p.getDateFinished() != null) {
      info.put("finished", p.getDateFinished().toString());
    }
    if (p.getStatus().isRunning()) {
      info.put("progress", String.valueOf(p.progress()));
    } else {
      info.put("progress", String.valueOf(100));
    }
    return info;
  }

  private void setParagraphMagic(Paragraph p, int index) {
    if (paragraphs.size() > 0) {
      String replName;
      if (index == 0) {
        replName = paragraphs.get(0).getIntpText();
      } else {
        replName = paragraphs.get(index - 1).getIntpText();
      }
      if (p.isValidInterpreter(replName) && StringUtils.isNotEmpty(replName)) {
        p.setText("%" + replName + "\n");
      }
    }
  }

  /**
   * Run all paragraphs sequentially.
   */
  public synchronized void runAll() {
    String cronExecutingUser = (String) getConfig().get("cronExecutingUser");
    if (null == cronExecutingUser) {
      cronExecutingUser = "anonymous";
    }
    AuthenticationInfo authenticationInfo = new AuthenticationInfo();
    authenticationInfo.setUser(cronExecutingUser);
    runAll(authenticationInfo, true);
  }

  public void runAll(AuthenticationInfo authenticationInfo, boolean blocking) {
    for (Paragraph p : getParagraphs()) {
      if (!p.isEnabled()) {
        continue;
      }
      p.setAuthenticationInfo(authenticationInfo);
      if (!run(p.getId(), blocking)) {
        logger.warn("Skip running the remain notes because paragraph {} fails", p.getId());
        break;
      }
    }
  }

  public boolean run(String paragraphId) {
    return run(paragraphId, false);
  }

  /**
   * Run a single paragraph.
   *
   * @param paragraphId ID of paragraph
   */
  public boolean run(String paragraphId, boolean blocking) {
    Paragraph p = getParagraph(paragraphId);
    p.setListener(jobListenerFactory.getParagraphJobListener(this));
    return p.execute(blocking);
  }

  /**
   * Check whether all paragraphs belongs to this note has terminated
   */
  boolean isTerminated() {
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        if (!p.isTerminated()) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Return true if there is a running or pending paragraph
   */
  boolean isRunningOrPending() {
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        Status status = p.getStatus();
        if (status.isRunning() || status.isPending()) {
          return true;
        }
      }
    }

    return false;
  }

  public boolean isTrash() {
    String path = getName();
    if (path.charAt(0) == '/') {
      path = path.substring(1);
    }
    return path.split("/")[0].equals(Folder.TRASH_FOLDER_ID);
  }

  public List<InterpreterCompletion> completion(String paragraphId, String buffer, int cursor) {
    Paragraph p = getParagraph(paragraphId);
    p.setListener(jobListenerFactory.getParagraphJobListener(this));

    return p.completion(buffer, cursor);
  }

  public List<InterpreterCompletion> getInterpreterCompletion() {
    List<InterpreterCompletion> completion = new LinkedList();
    for (InterpreterSetting intp : interpreterSettingManager.getInterpreterSettings(getId())) {
      List<InterpreterInfo> intInfo = intp.getInterpreterInfos();
      if (intInfo.size() > 1) {
        for (InterpreterInfo info : intInfo) {
          String name = intp.getName() + "." + info.getName();
          completion.add(new InterpreterCompletion(name, name, CompletionType.setting.name()));
        }
      } else {
        completion.add(new InterpreterCompletion(intp.getName(), intp.getName(),
            CompletionType.setting.name()));
      }
    }
    return completion;
  }

  public List<Paragraph> getParagraphs() {
    synchronized (paragraphs) {
      return new LinkedList<>(paragraphs);
    }
  }

  private void snapshotAngularObjectRegistry(String user) {
    angularObjects = new HashMap<>();

    List<InterpreterSetting> settings = interpreterSettingManager.getInterpreterSettings(getId());
    if (settings == null || settings.size() == 0) {
      return;
    }

    for (InterpreterSetting setting : settings) {
      InterpreterGroup intpGroup = setting.getInterpreterGroup(user, id);
      if (intpGroup != null) {
        AngularObjectRegistry registry = intpGroup.getAngularObjectRegistry();
        angularObjects.put(intpGroup.getId(), registry.getAllWithGlobal(id));
      }
    }
  }

  private void removeAllAngularObjectInParagraph(String user, String paragraphId) {
    angularObjects = new HashMap<>();

    List<InterpreterSetting> settings = interpreterSettingManager.getInterpreterSettings(getId());
    if (settings == null || settings.size() == 0) {
      return;
    }

    for (InterpreterSetting setting : settings) {
      if (setting.getInterpreterGroup(user, id) == null) {
        continue;
      }
      InterpreterGroup intpGroup = setting.getInterpreterGroup(user, id);
      AngularObjectRegistry registry = intpGroup.getAngularObjectRegistry();

      if (registry instanceof RemoteAngularObjectRegistry) {
        // remove paragraph scope object
        ((RemoteAngularObjectRegistry) registry).removeAllAndNotifyRemoteProcess(id, paragraphId);

        // remove app scope object
        List<ApplicationState> appStates = getParagraph(paragraphId).getAllApplicationStates();
        if (appStates != null) {
          for (ApplicationState app : appStates) {
            ((RemoteAngularObjectRegistry) registry)
                .removeAllAndNotifyRemoteProcess(id, app.getId());
          }
        }
      } else {
        registry.removeAll(id, paragraphId);

        // remove app scope object
        List<ApplicationState> appStates = getParagraph(paragraphId).getAllApplicationStates();
        if (appStates != null) {
          for (ApplicationState app : appStates) {
            registry.removeAll(id, app.getId());
          }
        }
      }
    }
  }

  public void persist(AuthenticationInfo subject) throws IOException {
    Preconditions.checkNotNull(subject, "AuthenticationInfo should not be null");
    stopDelayedPersistTimer();
    snapshotAngularObjectRegistry(subject.getUser());
    index.updateIndexDoc(this);
    repo.save(this, subject);
  }

  /**
   * Persist this note with maximum delay.
   */
  public void persist(int maxDelaySec, AuthenticationInfo subject) {
    startDelayedPersistTimer(maxDelaySec, subject);
  }

  void unpersist(AuthenticationInfo subject) throws IOException {
    repo.remove(getId(), subject);
  }


  /**
   * Return new note for specific user. this inserts and replaces user paragraph which doesn't
   * exists in original paragraph
   *
   * @param user specific user
   * @return new Note for the user
   */
  public Note getUserNote(String user) {
    Note newNote = new Note();
    newNote.name = getName();
    newNote.id = getId();
    newNote.config = getConfig();
    newNote.angularObjects = getAngularObjects();

    Paragraph newParagraph;
    for (Paragraph p : paragraphs) {
      newParagraph = p.getUserParagraph(user);
      if (null == newParagraph) {
        newParagraph = p.cloneParagraphForUser(user);
      }
      newNote.paragraphs.add(newParagraph);
    }

    return newNote;
  }

  private void startDelayedPersistTimer(int maxDelaySec, final AuthenticationInfo subject) {
    synchronized (this) {
      if (delayedPersist != null) {
        return;
      }

      delayedPersist = delayedPersistThreadPool.schedule(new Runnable() {

        @Override
        public void run() {
          try {
            persist(subject);
          } catch (IOException e) {
            logger.error(e.getMessage(), e);
          }
        }
      }, maxDelaySec, TimeUnit.SECONDS);
    }
  }

  private void stopDelayedPersistTimer() {
    synchronized (this) {
      if (delayedPersist == null) {
        return;
      }

      delayedPersist.cancel(false);
    }
  }

  public Map<String, Object> getConfig() {
    if (config == null) {
      config = new HashMap<>();
    }
    return config;
  }

  public void setConfig(Map<String, Object> config) {
    this.config = config;
  }

  public Map<String, Object> getInfo() {
    if (info == null) {
      info = new HashMap<>();
    }
    return info;
  }

  public void setInfo(Map<String, Object> info) {
    this.info = info;
  }

  @Override
  public void beforeStatusChange(Job job, Status before, Status after) {
    if (jobListenerFactory != null) {
      ParagraphJobListener listener = jobListenerFactory.getParagraphJobListener(this);
      if (listener != null) {
        listener.beforeStatusChange(job, before, after);
      }
    }
  }

  @Override
  public void afterStatusChange(Job job, Status before, Status after) {
    if (jobListenerFactory != null) {
      ParagraphJobListener listener = jobListenerFactory.getParagraphJobListener(this);
      if (listener != null) {
        listener.afterStatusChange(job, before, after);
      }
    }

    if (noteEventListener != null) {
      noteEventListener.onParagraphStatusChange((Paragraph) job, after);
    }
  }

  @Override
  public void onProgressUpdate(Job job, int progress) {
    if (jobListenerFactory != null) {
      ParagraphJobListener listener = jobListenerFactory.getParagraphJobListener(this);
      if (listener != null) {
        listener.onProgressUpdate(job, progress);
      }
    }
  }

  @Override
  public void onOutputAppend(Paragraph paragraph, int idx, String output) {
    if (jobListenerFactory != null) {
      ParagraphJobListener listener = jobListenerFactory.getParagraphJobListener(this);
      if (listener != null) {
        listener.onOutputAppend(paragraph, idx, output);
      }
    }
  }

  @Override
  public void onOutputUpdate(Paragraph paragraph, int idx, InterpreterResultMessage msg) {
    if (jobListenerFactory != null) {
      ParagraphJobListener listener = jobListenerFactory.getParagraphJobListener(this);
      if (listener != null) {
        listener.onOutputUpdate(paragraph, idx, msg);
      }
    }
  }

  @Override
  public void onOutputUpdateAll(Paragraph paragraph, List<InterpreterResultMessage> msgs) {
    if (jobListenerFactory != null) {
      ParagraphJobListener listener = jobListenerFactory.getParagraphJobListener(this);
      if (listener != null) {
        listener.onOutputUpdateAll(paragraph, msgs);
      }
    }
  }

  void setNoteEventListener(NoteEventListener noteEventListener) {
    this.noteEventListener = noteEventListener;
  }

  boolean hasInterpreterBinded() {
    return !interpreterSettingManager.getInterpreterSettings(getId()).isEmpty();
  }

  @Override
  public String toJson() {
    return gson.toJson(this);
  }

  public static Note fromJson(String json) {
    Note note = gson.fromJson(json, Note.class);
    convertOldInput(note);
    note.postProcessParagraphs();
    return note;
  }

  public void postProcessParagraphs() {
    for (Paragraph p : paragraphs) {
      p.clearRuntimeInfos();
      p.parseText();
    }
  }

  private static void convertOldInput(Note note) {
    for (Paragraph p : note.paragraphs) {
      p.settings.convertOldInput();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Note note = (Note) o;

    if (paragraphs != null ? !paragraphs.equals(note.paragraphs) : note.paragraphs != null) {
      return false;
    }
    //TODO(zjffdu) exclude name because FolderView.index use Note as key and consider different name
    //as same note
    //    if (name != null ? !name.equals(note.name) : note.name != null) return false;
    if (id != null ? !id.equals(note.id) : note.id != null) {
      return false;
    }
    if (angularObjects != null ?
        !angularObjects.equals(note.angularObjects) : note.angularObjects != null) {
      return false;
    }
    if (config != null ? !config.equals(note.config) : note.config != null) {
      return false;
    }
    return info != null ? info.equals(note.info) : note.info == null;

  }

  @Override
  public int hashCode() {
    int result = paragraphs != null ? paragraphs.hashCode() : 0;
    //    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (id != null ? id.hashCode() : 0);
    result = 31 * result + (angularObjects != null ? angularObjects.hashCode() : 0);
    result = 31 * result + (config != null ? config.hashCode() : 0);
    result = 31 * result + (info != null ? info.hashCode() : 0);
    return result;
  }

  @VisibleForTesting
  public static Gson getGson() {
    return gson;
  }
}
