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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.common.JsonSerializable;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.notebook.utility.IdHashes;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Represent the note of Zeppelin. All the note and its paragraph operations are done
 * via this class.
 */
public class Note implements JsonSerializable {
  private static final Logger logger = LoggerFactory.getLogger(Note.class);
  private static Gson gson = new GsonBuilder()
      .setPrettyPrinting()
      .setDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
      .registerTypeAdapter(Date.class, new NotebookImportDeserializer())
      .registerTypeAdapterFactory(Input.TypeAdapterFactory)
      .create();

  private List<Paragraph> paragraphs = new LinkedList<>();

  private String name = "";
  private String id;
  private String defaultInterpreterGroup;
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


  /********************************** transient fields ******************************************/
  private transient boolean loaded = false;
  private transient String path;
  private transient InterpreterFactory interpreterFactory;
  private transient InterpreterSettingManager interpreterSettingManager;
  private transient ParagraphJobListener paragraphJobListener;
  private transient List<NoteEventListener> noteEventListeners = new ArrayList<>();
  private transient Credentials credentials;


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
    generateId();

    setCronSupported(ZeppelinConfiguration.create());
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

  public String getDefaultInterpreterGroup() {
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
    if (this.path == null) {
      if (name.startsWith("/")) {
        this.path = name;
      } else {
        this.path = "/" + name;
      }
    } else {
      int pos = this.path.indexOf("/");
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

  void setParagraphJobListener(ParagraphJobListener paragraphJobListener) {
    this.paragraphJobListener = paragraphJobListener;
  }

  public Boolean isCronSupported(ZeppelinConfiguration config) {
    if (config.isZeppelinNotebookCronEnable()) {
      config.getZeppelinNotebookCronFolders();
      if (config.getZeppelinNotebookCronFolders() == null) {
        return true;
      } else {
        for (String folder : config.getZeppelinNotebookCronFolders().split(",")) {
          folder = folder.replaceAll("\\*", "\\.*").replaceAll("\\?", "\\.");
          if (getName().matches(folder)) {
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

    logger.debug("srcParagraph user: " + srcParagraph.getUser());
    
    newParagraph.setAuthenticationInfo(subject);
    newParagraph.setConfig(config);
    newParagraph.settings.setParams(param);
    newParagraph.settings.setForms(form);
    newParagraph.setText(srcParagraph.getText());
    newParagraph.setTitle(srcParagraph.getTitle());
    
    logger.debug("newParagraph user: " + newParagraph.getUser());

    try {
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
   * Run all paragraphs sequentially. Only used for CronJob
   */
  public synchronized void runAll() {
    String cronExecutingUser = (String) getConfig().get("cronExecutingUser");
    String cronExecutingRoles = (String) getConfig().get("cronExecutingRoles");
    if (null == cronExecutingUser) {
      cronExecutingUser = "anonymous";
    }
    AuthenticationInfo authenticationInfo = new AuthenticationInfo(
        cronExecutingUser,
        StringUtils.isEmpty(cronExecutingRoles) ? null : cronExecutingRoles,
        null);
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
    p.setListener(this.paragraphJobListener);
    return p.execute(blocking);
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
    return this.path.startsWith("/" + NoteManager.TRASH_FOLDER);
  }

  public List<InterpreterCompletion> completion(String paragraphId, String buffer, int cursor) {
    Paragraph p = getParagraph(paragraphId);
    p.setListener(this.paragraphJobListener);

    return p.completion(buffer, cursor);
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
      p.cleanRuntimeInfos();
      p.parseText();

      if (p.getStatus() == Status.PENDING || p.getStatus() == Status.RUNNING) {
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
  public static Gson getGson() {
    return gson;
  }

  public void setNoteEventListeners(List<NoteEventListener> noteEventListeners) {
    this.noteEventListeners = noteEventListeners;
  }
}
