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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.common.JsonSerializable;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.interpreter.ExecutionContext;
import org.apache.zeppelin.interpreter.ExecutionContextBuilder;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterNotFoundException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObject;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.notebook.utility.IdHashes;
import org.apache.zeppelin.scheduler.ExecutorFactory;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.apache.zeppelin.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represent the note of Zeppelin. All the note and its paragraph operations are done
 * via this class.
 */
public class Note implements JsonSerializable {

  private static final Logger LOGGER = LoggerFactory.getLogger(Note.class);
  // serialize Paragraph#runtimeInfos and Note#path to frontend but not to note file
  private static final ExclusionStrategy strategy = new ExclusionStrategy() {
    @Override
    public boolean shouldSkipField(FieldAttributes f) {
      return f.getName().equals("path");
    }

    @Override
    public boolean shouldSkipClass(Class<?> clazz) {
      return false;
    }
  };
  private static final Gson GSON = new GsonBuilder()
      .setPrettyPrinting()
      .setDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
      .registerTypeAdapter(Date.class, new NotebookImportDeserializer())
      .registerTypeAdapterFactory(Input.TypeAdapterFactory)
      .setExclusionStrategies(strategy)
      .create();
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
          DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

  private List<Paragraph> paragraphs = new LinkedList<>();
  private String name = "";
  private String id;
  private String defaultInterpreterGroup;
  private String version;

  private Map<String, Object> noteParams = new LinkedHashMap<>();
  private Map<String, Input> noteForms = new LinkedHashMap<>();
  private Map<String, List<AngularObject>> angularObjects = new HashMap<>();

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

  // The front end needs to judge TRASH_FOLDER according to the path,
  // But it doesn't need to be saved in note json. So we will exclude this when saving
  // note to NotebookRepo.
  private String path;

  /********************************** transient fields ******************************************/
  private transient boolean loaded = false;
  private transient InterpreterFactory interpreterFactory;
  private transient InterpreterSettingManager interpreterSettingManager;
  private transient ParagraphJobListener paragraphJobListener;
  private transient List<NoteEventListener> noteEventListeners = new ArrayList<>();
  private transient Credentials credentials;
  private transient ZeppelinConfiguration zConf = ZeppelinConfiguration.create();

  public Note() {
    generateId();
  }

  public Note(String path, String defaultInterpreterGroup, InterpreterFactory factory,
      InterpreterSettingManager interpreterSettingManager, ParagraphJobListener paragraphJobListener,
      Credentials credentials, List<NoteEventListener> noteEventListener) {
    setPath(path);
    this.defaultInterpreterGroup = defaultInterpreterGroup;
    this.interpreterFactory = factory;
    this.interpreterSettingManager = interpreterSettingManager;
    this.paragraphJobListener = paragraphJobListener;
    this.noteEventListeners = noteEventListener;
    this.credentials = credentials;
    this.version = Util.getVersion();
    generateId();

    setCronSupported(zConf);
  }

  public Note(NoteInfo noteInfo) {
    this.id = noteInfo.getId();
    setPath(noteInfo.getPath());
  }

  public String getPath() {
    return path;
  }

  public String getParentPath() {
    int pos = path.lastIndexOf("/");
    if (pos == 0) {
      return "/";
    } else {
      return path.substring(0, pos);
    }
  }

  private String getName(String path) {
    int pos = path.lastIndexOf("/");
    return path.substring(pos + 1);
  }

  private void generateId() {
    id = IdHashes.generateId();
  }

  public boolean isLoaded() {
    return loaded;
  }

  public void setLoaded(boolean loaded) {
    this.loaded = loaded;
  }

  /**
   * Release note memory
   */
  public void unLoad() {
    this.setLoaded(false);
    this.paragraphs = null;
    this.config = null;
    this.info = null;
    this.noteForms = null;
    this.noteParams = null;
    this.angularObjects = null;
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
    config.put("personalizedMode", valueString);
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

  @VisibleForTesting
  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setPath(String path) {
    if (!path.startsWith("/")) {
      this.path = "/" + path;
    } else {
      this.path = path;
    }
    this.name = getName(path);
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getDefaultInterpreterGroup() {
    if (StringUtils.isBlank(defaultInterpreterGroup)) {
      defaultInterpreterGroup = ZeppelinConfiguration.create()
          .getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_GROUP_DEFAULT);
    }
    return defaultInterpreterGroup;
  }

  public void setDefaultInterpreterGroup(String defaultInterpreterGroup) {
    this.defaultInterpreterGroup = defaultInterpreterGroup;
  }

  public Map<String, Object> getNoteParams() {
    return noteParams;
  }

  public void setNoteParams(Map<String, Object> noteParams) {
    this.noteParams = noteParams;
  }

  public Map<String, Input> getNoteForms() {
    return noteForms;
  }

  public void setNoteForms(Map<String, Input> noteForms) {
    this.noteForms = noteForms;
  }

  public void setName(String name) {
    this.name = name;
    // for the notes before 0.9, get path from name.
    if (this.path == null) {
      if (name.startsWith("/")) {
        this.path = name;
      } else {
        this.path = "/" + name;
      }
    } else {
      int pos = this.path.lastIndexOf("/");
      this.path = this.path.substring(0, pos + 1) + this.name;
    }
  }

  public InterpreterFactory getInterpreterFactory() {
    return interpreterFactory;
  }

  public void setInterpreterFactory(InterpreterFactory interpreterFactory) {
    this.interpreterFactory = interpreterFactory;
  }

  void setInterpreterSettingManager(InterpreterSettingManager interpreterSettingManager) {
    this.interpreterSettingManager = interpreterSettingManager;
  }

  InterpreterSettingManager getInterpreterSettingManager() {
    return this.interpreterSettingManager;
  }

  void setParagraphJobListener(ParagraphJobListener paragraphJobListener) {
    this.paragraphJobListener = paragraphJobListener;
  }

  public boolean isCronSupported(ZeppelinConfiguration config) {
    if (config.isZeppelinNotebookCronEnable()) {
      config.getZeppelinNotebookCronFolders();
      if (StringUtils.isBlank(config.getZeppelinNotebookCronFolders())) {
        return true;
      } else {
        for (String folder : config.getZeppelinNotebookCronFolders().split(",")) {
          if (this.path.startsWith(folder)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public void setCronSupported(ZeppelinConfiguration config) {
    getConfig().put("isZeppelinNotebookCronEnable", isCronSupported(config));
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

  public List<AngularObject> getAngularObjects(String intpGroupId) {
    if (!angularObjects.containsKey(intpGroupId)) {
      return new ArrayList<>();
    }
    return angularObjects.get(intpGroupId);
  }

  /**
   * Add or update the note AngularObject.
   */
  public void addOrUpdateAngularObject(String intpGroupId, AngularObject angularObject) {
    List<AngularObject> angularObjectList;
    if (!angularObjects.containsKey(intpGroupId)) {
      angularObjectList = new ArrayList<>();
      angularObjects.put(intpGroupId, angularObjectList);
    } else {
      angularObjectList = angularObjects.get(intpGroupId);

      // Delete existing AngularObject
      Iterator<AngularObject> iter = angularObjectList.iterator();
      while(iter.hasNext()){
        String noteId = "", paragraphId = "", name = "";
        Object object = iter.next();
        if (object instanceof AngularObject) {
          AngularObject ao = (AngularObject)object;
          noteId = ao.getNoteId();
          paragraphId = ao.getParagraphId();
          name = ao.getName();
        } else if (object instanceof RemoteAngularObject) {
          RemoteAngularObject rao = (RemoteAngularObject)object;
          noteId = rao.getNoteId();
          paragraphId = rao.getParagraphId();
          name = rao.getName();
        } else {
          continue;
        }
        if (StringUtils.equals(noteId, angularObject.getNoteId())
            && StringUtils.equals(paragraphId, angularObject.getParagraphId())
            && StringUtils.equals(name, angularObject.getName())) {
          iter.remove();
        }
      }
    }

    angularObjectList.add(angularObject);
  }

  /**
   * Delete the note AngularObject.
   */
  public void deleteAngularObject(String intpGroupId, String noteId, String paragraphId, String name) {
    List<AngularObject> angularObjectList;
    if (!angularObjects.containsKey(intpGroupId)) {
      return;
    } else {
      angularObjectList = angularObjects.get(intpGroupId);

      // Delete existing AngularObject
      Iterator<AngularObject> iter = angularObjectList.iterator();
      while(iter.hasNext()){
        String noteIdCandidate = "";
        String paragraphIdCandidate = "";
        String nameCandidate = "";
        Object object = iter.next();
        if (object instanceof AngularObject) {
          AngularObject ao = (AngularObject)object;
          noteIdCandidate = ao.getNoteId();
          paragraphIdCandidate = ao.getParagraphId();
          nameCandidate = ao.getName();
        } else if (object instanceof RemoteAngularObject) {
          RemoteAngularObject rao = (RemoteAngularObject) object;
          noteIdCandidate = rao.getNoteId();
          paragraphIdCandidate = rao.getParagraphId();
          nameCandidate = rao.getName();
        } else {
          continue;
        }
        if (StringUtils.equals(noteId, noteIdCandidate)
            && StringUtils.equals(paragraphId, paragraphIdCandidate)
            && StringUtils.equals(name, nameCandidate)) {
          iter.remove();
        }
      }
    }
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
  void addCloneParagraph(Paragraph srcParagraph, AuthenticationInfo subject) {

    // Keep paragraph original ID
    Paragraph newParagraph = new Paragraph(srcParagraph.getId(), this, paragraphJobListener);

    Map<String, Object> config = new HashMap<>(srcParagraph.getConfig());
    Map<String, Object> param = srcParagraph.settings.getParams();
    Map<String, Input> form = srcParagraph.settings.getForms();

    LOGGER.debug("srcParagraph user: " + srcParagraph.getUser());

    newParagraph.setAuthenticationInfo(subject);
    newParagraph.setConfig(config);
    newParagraph.settings.setParams(param);
    newParagraph.settings.setForms(form);
    newParagraph.setText(srcParagraph.getText());
    newParagraph.setTitle(srcParagraph.getTitle());

    LOGGER.debug("newParagraph user: " + newParagraph.getUser());

    try {
      String resultJson = GSON.toJson(srcParagraph.getReturn());
      InterpreterResult result = InterpreterResult.fromJson(resultJson);
      newParagraph.setReturn(result, null);
    } catch (Exception e) {
      // 'result' part of Note consists of exception, instead of actual interpreter results
      LOGGER.warn(
          "Paragraph " + srcParagraph.getId() + " has a result with exception. " + e.getMessage());
    }

    synchronized (paragraphs) {
      paragraphs.add(newParagraph);
    }

    try {
      fireParagraphCreateEvent(newParagraph);
    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  public void fireParagraphCreateEvent(Paragraph p) throws IOException {
    for (NoteEventListener listener : noteEventListeners) {
      listener.onParagraphCreate(p);
    }
  }

  public void fireParagraphRemoveEvent(Paragraph p) throws IOException {
    for (NoteEventListener listener : noteEventListeners) {
      listener.onParagraphRemove(p);
    }
  }


  public void fireParagraphUpdateEvent(Paragraph p) throws IOException {
    for (NoteEventListener listener : noteEventListeners) {
      listener.onParagraphUpdate(p);
    }
  }

  /**
   * Create a new paragraph and insert it to the note in given index.
   *
   * @param index index of paragraphs
   */
  public Paragraph insertNewParagraph(int index, AuthenticationInfo authenticationInfo) {
    Paragraph paragraph = new Paragraph(this, paragraphJobListener);
    if (null != interpreterSettingManager) {
      // Set the default parameter configuration for the paragraph
      // based on `interpreter-setting.json` config
      Map<String, Object> config =
          interpreterSettingManager.getConfigSetting(defaultInterpreterGroup);
      paragraph.setConfig(config);
    }
    paragraph.setAuthenticationInfo(authenticationInfo);
    setParagraphMagic(paragraph, index);
    insertParagraph(paragraph, index);
    return paragraph;
  }

  public void addParagraph(Paragraph paragraph) {
    insertParagraph(paragraph, paragraphs.size());
  }

  private void insertParagraph(Paragraph paragraph, int index) {
    synchronized (paragraphs) {
      paragraphs.add(index, paragraph);
    }
    try {
      fireParagraphCreateEvent(paragraph);
    } catch (IOException e) {
      e.printStackTrace();
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
          i.remove();
          try {
            fireParagraphRemoveEvent(p);
          } catch (IOException e) {
            e.printStackTrace();
          }
          return p;
        }
      }
    }
    return null;
  }

  public void clearParagraphOutputFields(Paragraph p) {
    p.setReturn(null, null);
    p.cleanRuntimeInfos();
    p.cleanOutputBuffer();
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

  public Paragraph getParagraph(int index) {
    return paragraphs.get(index);
  }

  public Paragraph getLastParagraph() {
    synchronized (paragraphs) {
      return paragraphs.get(paragraphs.size() - 1);
    }
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
   * Run all the paragraphs of this note in different kinds of ways:
   * - blocking/non-blocking
   * - isolated/non-isolated
   *
   * @param authInfo
   * @param blocking
   * @param isolated
   * @throws Exception
   */
  public void runAll(AuthenticationInfo authInfo,
                     boolean blocking,
                     boolean isolated,
                     Map<String, Object> params) throws Exception {
    if (isRunning()) {
      throw new Exception("Unable to run note:" + id + " because it is still in RUNNING state.");
    }
    setIsolatedMode(isolated);
    setRunning(true);
    setStartTime(DATE_TIME_FORMATTER.format(LocalDateTime.now()));
    if (blocking) {
      try {
        runAllSync(authInfo, isolated, params);
      } finally {
        setRunning(false);
        setIsolatedMode(false);
        clearStartTime();
      }
    } else {
      ExecutorFactory.singleton().getNoteJobExecutor().submit(() -> {
        try {
          runAllSync(authInfo, isolated, params);
        } catch (Exception e) {
          LOGGER.warn("Fail to run note: " + id, e);
        } finally {
          setRunning(false);
          setIsolatedMode(false);
          clearStartTime();
        }
      });
    }
  }

  /**
   * Run all the paragraphs in sync(blocking) way.
   *
   * @param authInfo
   * @param isolated
   */
  private void runAllSync(AuthenticationInfo authInfo, boolean isolated, Map<String, Object> params) throws Exception {
    try {
      for (Paragraph p : getParagraphs()) {
        if (!p.isEnabled()) {
          continue;
        }
        p.setAuthenticationInfo(authInfo);
        Map<String, Object> originalParams = p.settings.getParams();
        try {
          if (params != null && !params.isEmpty()) {
            p.settings.setParams(params);
          }
          Interpreter interpreter = p.getBindedInterpreter();
          if (interpreter != null) {
            // set interpreter property to execution.mode to be note
            // so that it could use the correct scheduler. see ZEPPELIN-4832
            interpreter.setProperty(".execution.mode", "note");
            interpreter.setProperty(".noteId", id);
          }
          // Must run each paragraph in blocking way.
          if (!run(p.getId(), true)) {
            LOGGER.warn("Skip running the remain notes because paragraph {} fails", p.getId());
            throw new Exception("Fail to run note because paragraph " + p.getId() + " is failed, result: " +
                    p.getReturn());
          }
        } catch (InterpreterNotFoundException e) {
          // ignore, because the following run method will fail if interpreter not found.
        } finally {
          // reset params to the original value
          p.settings.setParams(originalParams);
        }
      }
    } catch (Exception e) {
      throw e;
    } finally {
      if (isolated) {
        LOGGER.info("Releasing interpreters used by this note: {}", id);
        for (InterpreterSetting setting : getUsedInterpreterSettings()) {
          ExecutionContext executionContext = new ExecutionContextBuilder()
                  .setUser(authInfo.getUser())
                  .setNoteId(id)
                  .setDefaultInterpreterGroup(defaultInterpreterGroup)
                  .setInIsolatedMode(isolated)
                  .setStartTime(getStartTime())
                  .createExecutionContext();
          setting.closeInterpreters(executionContext);
        }
      }
    }
  }

  /**
   * Run a single paragraph in non-blocking way.
   *
   * @param paragraphId
   * @return
   */
  public boolean run(String paragraphId) {
    return run(paragraphId, false);
  }

  /**
   * Run a single paragraph.
   *
   * @param paragraphId ID of paragraph
   * @param blocking Whether run this paragraph in blocking way
   */
  public boolean run(String paragraphId, boolean blocking) {
    return run(paragraphId, blocking, null);
  }

  /**
   * Run a single paragraph. Return true only when paragraph run successfully.
   *
   * @param paragraphId
   * @param blocking
   * @param ctxUser
   * @return
   */
  public boolean run(String paragraphId,
                     boolean blocking,
                     String ctxUser) {
    Paragraph p = getParagraph(paragraphId);

    if (isPersonalizedMode() && ctxUser != null)
      p = p.getUserParagraph(ctxUser);

    p.setListener(this.paragraphJobListener);
    return p.execute(blocking);
  }

  /**
   * Return true if there is a running or pending paragraph
   */
  public boolean haveRunningOrPendingParagraphs() {
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
    return this.path.startsWith("/" + NoteManager.TRASH_FOLDER);
  }

  public List<InterpreterCompletion> completion(String paragraphId,
                                                String buffer,
                                                int cursor,
                                                AuthenticationInfo authInfo) {
    Paragraph p = getParagraph(paragraphId);
    p.setListener(this.paragraphJobListener);
    p.setAuthenticationInfo(authInfo);
    return p.completion(buffer, cursor);
  }

  public List<Paragraph> getParagraphs() {
    return new ArrayList<>(this.paragraphs);
  }

  // TODO(zjffdu) how does this used ?
  private void snapshotAngularObjectRegistry(String user) {
    angularObjects = new HashMap<>();

    List<InterpreterSetting> settings = getBindedInterpreterSettings(Lists.newArrayList(user));
    if (settings == null || settings.size() == 0) {
      return;
    }

    for (InterpreterSetting setting : settings) {
      InterpreterGroup intpGroup = setting.getInterpreterGroup(new ExecutionContextBuilder().setUser(user).setNoteId(id).createExecutionContext());
      if (intpGroup != null) {
        AngularObjectRegistry registry = intpGroup.getAngularObjectRegistry();
        angularObjects.put(intpGroup.getId(), registry.getAllWithGlobal(id));
      }
    }
  }

  private void removeAllAngularObjectInParagraph(String user, String paragraphId) {
    angularObjects = new HashMap<>();

    List<InterpreterSetting> settings = getBindedInterpreterSettings(Lists.newArrayList(user));
    if (settings == null || settings.size() == 0) {
      return;
    }

    for (InterpreterSetting setting : settings) {
      if (setting.getInterpreterGroup(new ExecutionContextBuilder().setUser(user).setNoteId(id).createExecutionContext()) == null) {
        continue;
      }
      InterpreterGroup intpGroup = setting.getInterpreterGroup(new ExecutionContextBuilder().setUser(user).setNoteId(id).createExecutionContext());
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

  public List<InterpreterSetting> getBindedInterpreterSettings(List<String> userAndRoles) {
    // use LinkedHashSet because order matters, the first one represent the default interpreter setting.
    Set<InterpreterSetting> settings = new LinkedHashSet<>();
    // add the default interpreter group
    InterpreterSetting defaultIntpSetting =
            interpreterSettingManager.getByName(getDefaultInterpreterGroup());
    if (defaultIntpSetting != null) {
      settings.add(defaultIntpSetting);
    }
    // add the interpreter setting with the same group of default interpreter group
    if (defaultIntpSetting != null) {
      for (InterpreterSetting intpSetting : interpreterSettingManager.get()) {
        if (intpSetting.getGroup().equals(defaultIntpSetting.getGroup())) {
          if (intpSetting.isUserAuthorized(userAndRoles)) {
            settings.add(intpSetting);
          }
        }
      }
    }

    // add interpreter group used by each paragraph
    for (Paragraph p : getParagraphs()) {
      try {
        Interpreter intp = p.getBindedInterpreter();
        InterpreterSetting interpreterSetting = (
                (ManagedInterpreterGroup) intp.getInterpreterGroup()).getInterpreterSetting();
        if (interpreterSetting.isUserAuthorized(userAndRoles)) {
          settings.add(interpreterSetting);
        }
      } catch (InterpreterNotFoundException e) {
        // ignore this
      }
    }

    return new ArrayList<>(settings);
  }

  /**
   * Get InterpreterSetting used by the paragraphs of this note.
   * @return
   */
  public List<InterpreterSetting> getUsedInterpreterSettings() {
    Set<InterpreterSetting> settings = new HashSet<>();
    for (Paragraph p : getParagraphs()) {
      try {
        Interpreter intp = p.getBindedInterpreter();
        settings.add((
                (ManagedInterpreterGroup) intp.getInterpreterGroup()).getInterpreterSetting());
      } catch (InterpreterNotFoundException e) {
        // ignore this
      }
    }
    return new ArrayList<>(settings);
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

  public void setRunning(boolean runStatus) {
    Map<String, Object> infoMap = getInfo();
    boolean oldStatus = (boolean) infoMap.getOrDefault("isRunning", false);
    if (oldStatus != runStatus) {
      infoMap.put("isRunning", runStatus);
      if (paragraphJobListener != null) {
        paragraphJobListener.noteRunningStatusChange(this.id, runStatus);
      }
    }
  }

  public void setIsolatedMode(boolean isolatedMode) {
    info.put("inIsolatedMode", isolatedMode);
  }

  public boolean isIsolatedMode() {
    return Boolean.parseBoolean(
            info.getOrDefault("inIsolatedMode", "false").toString());
  }

  public void setStartTime(String startTime) {
    info.put("startTime", startTime);
  }

  public String getStartTime() {
    return info.getOrDefault("startTime", "").toString();
  }

  public void clearStartTime() {
    info.remove("startTime");
  }

  public boolean isRunning() {
    return (boolean) getInfo().getOrDefault("isRunning", false);
  }

  @Override
  public String toString() {
    if (this.path != null) {
      return this.path;
    } else {
      return "/" + this.name;
    }
  }

  @Override
  public String toJson() {
    return GSON.toJson(this);
  }

  /**
   * Parse note json from note file. Throw IOException if fail to parse note json.
   *
   * @param json
   * @return Note
   * @throws IOException if fail to parse note json (note file may be corrupted)
   */
  public static Note fromJson(String json) throws IOException {
    try {
      Note note = GSON.fromJson(json, Note.class);
      convertOldInput(note);
      note.info.remove("isRunning");
      note.postProcessParagraphs();
      return note;
    } catch (Exception e) {
      LOGGER.error("Fail to parse note json: " + e.toString());
      throw new IOException("Fail to parse note json: " + json, e);
    }
  }

  public void postProcessParagraphs() {
    for (Paragraph p : paragraphs) {
      p.parseText();
      p.setNote(this);
      p.setAuthenticationInfo(AuthenticationInfo.ANONYMOUS);

      if (p.getStatus() == Status.PENDING) {
        p.setStatus(Status.ABORT);
      }
      if (p.getStatus() == Status.RUNNING && !zConf.isRecoveryEnabled()) {
        p.setStatus(Status.ABORT);
      }

      List<ApplicationState> appStates = p.getAllApplicationStates();
      if (appStates != null) {
        for (ApplicationState app : appStates) {
          if (app.getStatus() != ApplicationState.Status.ERROR) {
            app.setStatus(ApplicationState.Status.UNLOADED);
          }
        }
      }
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
    //TODO(zjffdu) exclude path because FolderView.index use Note as key and consider different path
    //as same note
    //    if (path != null ? !path.equals(note.path) : note.path != null) return false;
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
    //    result = 31 * result + (path != null ? path.hashCode() : 0);
    result = 31 * result + (id != null ? id.hashCode() : 0);
    result = 31 * result + (angularObjects != null ? angularObjects.hashCode() : 0);
    result = 31 * result + (config != null ? config.hashCode() : 0);
    result = 31 * result + (info != null ? info.hashCode() : 0);
    return result;
  }

  @VisibleForTesting
  public static Gson getGSON() {
    return GSON;
  }

  public void setNoteEventListeners(List<NoteEventListener> noteEventListeners) {
    this.noteEventListeners = noteEventListeners;
  }
}
