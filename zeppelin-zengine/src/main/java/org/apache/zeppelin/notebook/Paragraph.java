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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.zeppelin.common.JsonSerializable;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.helium.HeliumPackage;
import org.apache.zeppelin.interpreter.Constants;
import org.apache.zeppelin.interpreter.ExecutionContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.Interpreter.FormType;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterNotFoundException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.JobListener;
import org.apache.zeppelin.scheduler.JobWithProgressPoller;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.apache.zeppelin.user.UserCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * Paragraph is a representation of an execution unit.
 */
public class Paragraph extends JobWithProgressPoller<InterpreterResult> implements Cloneable,
    JsonSerializable {

  private static final Logger LOGGER = LoggerFactory.getLogger(Paragraph.class);

  private String title;
  // text is composed of intpText and scriptText.
  private String text;
  private String user;
  private Date dateUpdated;
  private int progress;
  // paragraph configs like isOpen, colWidth, etc
  // Use ConcurrentHashMap to make Note thread-safe which is required by Note serialization
  // (saving note to NotebookRepo or broadcast to frontend), see ZEPPELIN-5530.
  private Map<String, Object> config = new ConcurrentHashMap<>();
  // form and parameter settings
  public GUI settings = new GUI();
  private InterpreterResult results;
  // Application states in this paragraph
  private final Queue<ApplicationState> apps = new ConcurrentLinkedQueue<>();
  // runtimeInfo, e.g. spark job url
  // Use ConcurrentHashMap to make Note/Paragraph thread-safe which is required by Note/Paragraph serialization
  // (saving note to NotebookRepo or broadcast to frontend), see ZEPPELIN-5530.
  private Map<String, ParagraphRuntimeInfo> runtimeInfos = new ConcurrentHashMap<>();

  /************** Transient fields which are not serializabled  into note json **************/
  private transient String intpText;
  private transient String scriptText;
  private transient Interpreter interpreter;
  private transient String interpreterGroupId;
  private transient Note note;
  private transient AuthenticationInfo subject;
  // personalized
  private transient Map<String, Paragraph> userParagraphMap = new HashMap<>();
  private transient Map<String, String> localProperties = new HashMap<>();

  private transient List<InterpreterResultMessage> outputBuffer = new ArrayList<>();


  @VisibleForTesting
  Paragraph() {
    super(generateId(), null);
  }

  public Paragraph(String paragraphId, Note note, JobListener listener) {
    super(paragraphId, generateId(), listener);
    this.note = note;
  }

  public Paragraph(Note note, JobListener listener) {
    super(generateId(), listener);
    this.note = note;
  }

  // used for clone paragraph
  public Paragraph(Paragraph p2) {
    super(p2.getId(), null);
    this.note = p2.note;
    this.settings.setParams(new HashMap<>(p2.settings.getParams()));
    this.settings.setForms(new LinkedHashMap<>(p2.settings.getForms()));
    this.setConfig(new HashMap<>(p2.getConfig()));
    this.setAuthenticationInfo(p2.getAuthenticationInfo());
    this.title = p2.title;
    this.text = p2.text;
    this.results = p2.results;
    setStatus(p2.getStatus());
  }

  private static String generateId() {
    return "paragraph_" + System.currentTimeMillis() + "_" + Math.abs(new SecureRandom().nextInt());
  }

  public Map<String, Paragraph> getUserParagraphMap() {
    return userParagraphMap;
  }

  public Paragraph getUserParagraph(String user) {
    if (!userParagraphMap.containsKey(user)) {
      cloneParagraphForUser(user);
    }
    return userParagraphMap.get(user);
  }

  @Override
  public void setResult(InterpreterResult result) {
    this.results = result;
  }

  public Paragraph cloneParagraphForUser(String user) {
    Paragraph p = new Paragraph(this);
    // reset status to READY when clone Paragraph for personalization.
    p.status = Status.READY;
    addUser(p, user);
    return p;
  }

  private void setIntpText(String newIntptext) {
    this.intpText = newIntptext;
  }

  public void clearUserParagraphs() {
    userParagraphMap.clear();
  }

  public void addUser(Paragraph p, String user) {
    userParagraphMap.put(user, p);
  }

  public String getUser() {
    return user;
  }

  public String getText() {
    return text;
  }

  public void setText(String newText) {
    this.text = newText;
    this.dateUpdated = new Date();
    parseText();
  }

  public void parseText() {
    // parse text to get interpreter component
    if (this.text != null) {
      // clean localProperties, otherwise previous localProperties will be used for the next run
      ParagraphTextParser.ParseResult result = ParagraphTextParser.parse(this.text);
      localProperties = result.getLocalProperties();
      setIntpText(result.getIntpText());
      this.scriptText = result.getScriptText();
    }
  }

  public AuthenticationInfo getAuthenticationInfo() {
    return subject;
  }

  public void setAuthenticationInfo(AuthenticationInfo subject) {
    this.subject = subject;
    if (subject != null) {
      this.user = subject.getUser();
    }
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getIntpText() {
    return intpText;
  }

  public String getScriptText() {
    return scriptText;
  }

  public void setNote(Note note) {
    this.note = note;
  }

  public Note getNote() {
    return note;
  }

  public Map<String, String> getLocalProperties() {
    return localProperties;
  }

  public boolean isEnabled() {
    Boolean enabled = (Boolean) config.get("enabled");
    return enabled == null || enabled.booleanValue();
  }

  public Interpreter getBindedInterpreter() throws InterpreterNotFoundException {
    ExecutionContext executionContext = note.getExecutionContext();
    executionContext.setUser(user);
    executionContext.setInterpreterGroupId(interpreterGroupId);
    return this.note.getInterpreterFactory().getInterpreter(intpText, executionContext);
  }

  @VisibleForTesting
  public void setInterpreter(Interpreter interpreter) {
    this.interpreter = interpreter;
  }

  public Interpreter getInterpreter() {
    return interpreter;
  }

  public List<InterpreterCompletion> completion(String buffer, int cursor) {
    setText(buffer);
    try {
      this.interpreter = getBindedInterpreter();
    } catch (InterpreterNotFoundException e) {
      LOGGER.debug("Unable to get completion because there's no interpreter bind to it", e);
      return new ArrayList<>();
    }
    cursor = calculateCursorPosition(buffer, cursor);
    InterpreterContext interpreterContext = getInterpreterContext();

    try {
      return this.interpreter.completion(this.scriptText, cursor, interpreterContext);
    } catch (InterpreterException e) {
      LOGGER.warn("Fail to get completion", e);
      return new ArrayList<>();
    }
  }

  public int calculateCursorPosition(String buffer, int cursor) {
    if (this.scriptText.isEmpty()) {
      return 0;
    }
    // Try to find the right cursor from this startPos, otherwise you may get the wrong cursor.
    // e.g.  %spark.pyspark  spark.
    int startPos = this.intpText == null ? 0 : this.intpText.length();
    int countCharactersBeforeScript = buffer.indexOf(this.scriptText, startPos);
    if (countCharactersBeforeScript > 0) {
      cursor -= countCharactersBeforeScript;
    }

    return cursor;
  }

  @Override
  public InterpreterResult getReturn() {
    return results;
  }

  @Override
  public int progress() {
    try {
      if (this.interpreter != null) {
        this.progress = this.interpreter.getProgress(getInterpreterContext());
        return this.progress;
      } else {
        return 0;
      }
    } catch (InterpreterException e) {
      throw new RuntimeException("Fail to get progress", e);
    }
  }

  @Override
  public Map<String, Object> info() {
    return null;
  }

  public boolean shouldSkipRunParagraph() {
    boolean checkEmptyConfig =
            (Boolean) config.getOrDefault(InterpreterSetting.PARAGRAPH_CONFIG_CHECK_EMTPY, true);
    // don't skip paragraph when local properties is not empty.
    // local properties can customize the behavior of interpreter. e.g. %r.shiny(type=run)
    return checkEmptyConfig && StringUtils.isEmpty(scriptText) && localProperties.isEmpty();
  }

  public boolean execute(boolean blocking) {
    return execute(null, blocking);
  }

  /**
   * Return true only when paragraph run successfully with state of FINISHED.
   * @param blocking
   * @return
   */
  public boolean execute(String interpreterGroupId, boolean blocking) {
    try {
      this.interpreterGroupId = interpreterGroupId;
      this.interpreter = getBindedInterpreter();
      InterpreterSetting interpreterSetting = ((ManagedInterpreterGroup)
              interpreter.getInterpreterGroup()).getInterpreterSetting();
      Map<String, Object> config
              = interpreterSetting.getConfig(interpreter.getClassName());
      mergeConfig(config);

      // clear output
      setResult(null);
      cleanOutputBuffer();
      cleanRuntimeInfos();

      setStatus(Status.PENDING);

      if (shouldSkipRunParagraph()) {
        LOGGER.info("Skip to run blank paragraph. {}", getId());
        setStatus(Job.Status.FINISHED);
        return true;
      }

      if (isEnabled()) {
        setAuthenticationInfo(getAuthenticationInfo());
        interpreter.getScheduler().submit(this);
       } else {
        LOGGER.info("Skip disabled paragraph. {}", getId());
        setStatus(Job.Status.FINISHED);
        return true;
      }


      if (blocking) {
        while (!getStatus().isCompleted()) {
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
        return getStatus() == Status.FINISHED;
      } else {
        return true;
      }
    } catch (InterpreterNotFoundException e) {
      InterpreterResult intpResult =
          new InterpreterResult(InterpreterResult.Code.ERROR,
                  String.format("Interpreter %s not found", this.intpText));
      setReturn(intpResult, e);
      setStatus(Job.Status.ERROR);
      return false;
    } catch (Throwable e) {
      InterpreterResult intpResult =
              new InterpreterResult(InterpreterResult.Code.ERROR,
                      "Unexpected exception: " + ExceptionUtils.getStackTrace(e));
      setReturn(intpResult, e);
      setStatus(Job.Status.ERROR);
      return false;
    }
  }

  @Override
  public void setStatus(Status status) {
    super.setStatus(status);
    // reset interpreterGroupId when paragraph is completed.
    if (status.isCompleted()) {
      this.interpreterGroupId = null;
    }
  }

  @Override
  protected InterpreterResult jobRun() throws Throwable {
    try {
      if (localProperties.getOrDefault("isRecover", "false").equals("false")) {
        this.runtimeInfos.clear();
      }
      this.interpreter = getBindedInterpreter();
      if (this.interpreter == null) {
        LOGGER.error("Can not find interpreter name " + intpText);
        throw new RuntimeException("Can not find interpreter for " + intpText);
      }
      LOGGER.info("Run paragraph [paragraph_id: {}, interpreter: {}, note_id: {}, user: {}]",
              getId(), this.interpreter.getClassName(), note.getId(), subject.getUser());
      InterpreterSetting interpreterSetting = ((ManagedInterpreterGroup)
              interpreter.getInterpreterGroup()).getInterpreterSetting();
      if (interpreterSetting.getStatus() != InterpreterSetting.Status.READY) {
        String message = String.format("Interpreter Setting '%s' is not ready, its status is %s",
                interpreterSetting.getName(), interpreterSetting.getStatus());
        LOGGER.error(message);
        throw new RuntimeException(message);
      }
      if (this.user != null) {
        if (subject != null && !interpreterSetting.isUserAuthorized(subject.getUsersAndRoles())) {
          String msg = String.format("%s has no permission for %s", subject.getUser(), intpText);
          LOGGER.error(msg);
          return new InterpreterResult(Code.ERROR, msg);
        }
      }

      for (Paragraph p : userParagraphMap.values()) {
        p.setText(getText());
      }

      // inject form
      String script = this.scriptText;
      String form = localProperties.getOrDefault("form", interpreter.getFormType().name());
      if (form.equalsIgnoreCase("simple")) {
        // inputs will be built from script body
        LinkedHashMap<String, Input> inputs = Input.extractSimpleQueryForm(script, false);
        LinkedHashMap<String, Input> noteInputs = Input.extractSimpleQueryForm(script, true);
        final AngularObjectRegistry angularRegistry =
                interpreter.getInterpreterGroup().getAngularObjectRegistry();
        String scriptBody = extractVariablesFromAngularRegistry(script, inputs, angularRegistry);

        settings.setForms(inputs);
        if (!noteInputs.isEmpty()) {
          if (!note.getNoteForms().isEmpty()) {
            Map<String, Input> currentNoteForms = note.getNoteForms();
            for (String s : noteInputs.keySet()) {
              if (!currentNoteForms.containsKey(s)) {
                currentNoteForms.put(s, noteInputs.get(s));
              }
            }
          } else {
            note.setNoteForms(noteInputs);
          }
        }
        script = Input.getSimpleQuery(note.getNoteParams(), scriptBody, true);
        script = Input.getSimpleQuery(settings.getParams(), script, false);
      } else {
        settings.clear();
      }

      LOGGER.debug("RUN : " + script);
      try {
        InterpreterContext context = getInterpreterContext();
        InterpreterContext.set(context);

        // Inject credentials
        String injectPropStr = interpreter.getProperty(Constants.INJECT_CREDENTIALS, "false");
        injectPropStr = context.getStringLocalProperty(Constants.INJECT_CREDENTIALS, injectPropStr);
        boolean shouldInjectCredentials = Boolean.parseBoolean(injectPropStr);

        InterpreterResult ret = null;
        if (shouldInjectCredentials) {
          UserCredentials creds = context.getAuthenticationInfo().getUserCredentials();
          CredentialInjector credinjector = new CredentialInjector(creds);
          String code = credinjector.replaceCredentials(script);
          ret = interpreter.interpret(code, context);
          ret = credinjector.hidePasswords(ret);
        } else {
          ret = interpreter.interpret(script, context);
        }

        if (interpreter.getFormType() == FormType.NATIVE) {
          note.setNoteParams(context.getNoteGui().getParams());
          note.setNoteForms(context.getNoteGui().getForms());
        }

        if (Code.KEEP_PREVIOUS_RESULT == ret.code()) {
          return getReturn();
        }

        Paragraph p = getUserParagraph(getUser());
        if (null != p) {
          p.setResult(ret);
          p.settings.setParams(settings.getParams());
        }

        return ret;
      } finally {
        InterpreterContext.remove();
      }
    } catch (Exception e) {
      return new InterpreterResult(Code.ERROR, ExceptionUtils.getStackTrace(e));
    } finally {
      localProperties.remove("isRecover");
    }
  }

  @Override
  protected boolean jobAbort() {
    if (interpreter == null) {
      return true;
    }
    try {
      interpreter.cancel(getInterpreterContext());
    } catch (InterpreterException e) {
      throw new RuntimeException(e);
    }

    return true;
  }

  private InterpreterContext getInterpreterContext() {
    AngularObjectRegistry registry = null;
    ResourcePool resourcePool = null;
    String replName = null;
    if (this.interpreter != null) {
      registry = this.interpreter.getInterpreterGroup().getAngularObjectRegistry();
      resourcePool = this.interpreter.getInterpreterGroup().getResourcePool();
      InterpreterSetting interpreterSetting = ((ManagedInterpreterGroup)
              interpreter.getInterpreterGroup()).getInterpreterSetting();
      replName = interpreterSetting.getName();
    }

    Credentials credentials = note.getCredentials();
    if (subject != null) {
      UserCredentials userCredentials;
      try {
        userCredentials = credentials.getUserCredentials(subject.getUser());
      } catch (IOException e) {
        LOGGER.warn("Unable to get Usercredentials. Working with empty UserCredentials", e);
        userCredentials = new UserCredentials();
      }
      subject.setUserCredentials(userCredentials);
    }

    return InterpreterContext.builder()
            .setNoteId(note.getId())
            .setNoteName(note.getName())
            .setParagraphId(getId())
            .setReplName(replName)
            .setParagraphTitle(title)
            .setParagraphText(text)
            .setAuthenticationInfo(subject)
            .setLocalProperties(localProperties)
            .setConfig(config)
            .setGUI(settings)
            .setNoteGUI(getNoteGui())
            .setAngularObjectRegistry(registry)
            .setResourcePool(resourcePool)
            .build();
  }

  public void setStatusToUserParagraph(Status status) {
    String user = getUser();
    if (null != user) {
      getUserParagraph(getUser()).setStatus(status);
    }
  }

  public Map<String, Object> getConfig() {
    return config;
  }

  // NOTE: function setConfig(...) will overwrite all configuration
  // Merge configuration, you need to use function mergeConfig(...)
  public void setConfig(Map<String, Object> config) {
    this.config = new HashMap<>(config);
  }

  // [ZEPPELIN-3919] Paragraph config default value can be customized
  // apply the `interpreter-setting.json` config
  // When creating a paragraph, it will update some of the configuration
  // parameters of the paragraph from the web side.
  // Need to deal with 2 situations
  // 1. The interpreter does not have a config configuration set,
  //    so newConfig is equal to null, Need to be processed using the
  //    default parameters of the interpreter
  // 2. The user manually modified the  interpreter types of this paragraph.
  //    Need to delete the existing configuration of this paragraph,
  //    update with the specified interpreter configuration
  public void mergeConfig(Map<String, Object> newConfig) {
    this.config.putAll(newConfig);
  }

  public void updateConfig(Map<String, String> newConfig) {
    this.config.putAll(newConfig);
  }

  public void setReturn(InterpreterResult value, Throwable t) {
    setResult(value);
    setException(t);
  }

  private String getApplicationId(HeliumPackage pkg) {
    return "app_" + getNote().getId() + "-" + getId() + pkg.getName().replaceAll("\\.", "_");
  }

  public ApplicationState createOrGetApplicationState(HeliumPackage pkg) {
    for (ApplicationState as : apps) {
      if (as.equals(pkg)) {
        return as;
      }
    }

    String appId = getApplicationId(pkg);
    ApplicationState appState = new ApplicationState(appId, pkg);
    apps.add(appState);
    return appState;
  }


  public ApplicationState getApplicationState(String appId) {
    for (ApplicationState as : apps) {
      if (as.getId().equals(appId)) {
        return as;
      }
    }

    return null;
  }

  public List<ApplicationState> getAllApplicationStates() {
    return new LinkedList<>(apps);
  }

  String extractVariablesFromAngularRegistry(String scriptBody, Map<String, Input> inputs,
      AngularObjectRegistry angularRegistry) {

    final String noteId = this.getNote().getId();
    final String paragraphId = this.getId();

    final Set<String> keys = new HashSet<>(inputs.keySet());

    for (String varName : keys) {
      final AngularObject paragraphScoped = angularRegistry.get(varName, noteId, paragraphId);
      final AngularObject noteScoped = angularRegistry.get(varName, noteId, null);
      final AngularObject angularObject = paragraphScoped != null ? paragraphScoped : noteScoped;
      if (angularObject != null) {
        inputs.remove(varName);
        final String pattern = "[$][{]\\s*" + varName + "\\s*(?:=[^}]+)?[}]";
        scriptBody = scriptBody.replaceAll(pattern, angularObject.get().toString());
      }
    }
    return scriptBody;
  }

  public boolean isValidInterpreter(String replName) {
    try {
      ExecutionContext executionContext = note.getExecutionContext();
      executionContext.setUser(user);
      executionContext.setInterpreterGroupId(interpreterGroupId);
      return note.getInterpreterFactory().getInterpreter(replName, executionContext) != null;
    } catch (InterpreterNotFoundException e) {
      return false;
    }
  }

  public void updateRuntimeInfos(String label, String tooltip, Map<String, String> infos,
      String group, String intpSettingId) {
    if (this.runtimeInfos == null) {
      this.runtimeInfos = new HashMap<>();
    }

    if (infos != null) {
      for (String key : infos.keySet()) {
        ParagraphRuntimeInfo info = this.runtimeInfos.get(key);
        if (info == null) {
          info = new ParagraphRuntimeInfo(key, label, tooltip, group, intpSettingId);
          this.runtimeInfos.put(key, info);
        }
        info.addValue(infos);
      }
    }
  }

  public Map<String, ParagraphRuntimeInfo> getRuntimeInfos() {
    return runtimeInfos;
  }

  public void cleanRuntimeInfos() {
    this.runtimeInfos.clear();
  }

  public void cleanOutputBuffer() {
    this.outputBuffer.clear();
  }

  /**
   * Save the buffered output to InterpreterResults. So that open another tab or refresh
   * note you can see the latest checkpoint's output.
   */
  public void checkpointOutput() {
    LOGGER.info("Checkpoint Paragraph output for paragraph: " + getId());
    this.results = new InterpreterResult(Code.SUCCESS);
    for (InterpreterResultMessage buffer : outputBuffer) {
      results.add(buffer);
    }
  }

  @VisibleForTesting
  public void waitUntilFinished() throws InterruptedException {
    while(!isTerminated()) {
      LOGGER.debug("Wait for paragraph to be finished");
      Thread.sleep(1000);
    }
  }

  @VisibleForTesting
  public void waitUntilRunning() throws InterruptedException {
    while(!isRunning()) {
      LOGGER.debug("Wait for paragraph to be running");
      Thread.sleep(1000);
    }
  }

  private GUI getNoteGui() {
    GUI gui = new GUI();
    gui.setParams(this.note.getNoteParams());
    gui.setForms(this.note.getNoteForms());
    return gui;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    Paragraph paragraph = (Paragraph) o;

    if (title != null ? !title.equals(paragraph.title) : paragraph.title != null) {
      return false;
    }
    if (text != null ? !text.equals(paragraph.text) : paragraph.text != null) {
      return false;
    }
    if (user != null ? !user.equals(paragraph.user) : paragraph.user != null) {
      return false;
    }
    if (dateUpdated != null ?
        !dateUpdated.equals(paragraph.dateUpdated) : paragraph.dateUpdated != null) {
      return false;
    }
    if (config != null ? !config.equals(paragraph.config) : paragraph.config != null) {
      return false;
    }
    if (settings != null ? !settings.equals(paragraph.settings) : paragraph.settings != null) {
      return false;
    }

    return results != null ?
        results.equals(paragraph.results) : paragraph.results == null;

  }

  @Override
  public int hashCode() {
    int result1 = super.hashCode();
    result1 = 31 * result1 + (title != null ? title.hashCode() : 0);
    result1 = 31 * result1 + (text != null ? text.hashCode() : 0);
    result1 = 31 * result1 + (user != null ? user.hashCode() : 0);
    result1 = 31 * result1 + (dateUpdated != null ? dateUpdated.hashCode() : 0);
    result1 = 31 * result1 + (config != null ? config.hashCode() : 0);
    result1 = 31 * result1 + (settings != null ? settings.hashCode() : 0);
    result1 = 31 * result1 + (results != null ? results.hashCode() : 0);
    return result1;
  }

  @Override
  public String toJson() {
    return Note.getGSON().toJson(this);
  }

  public static Paragraph fromJson(String json) {
    return Note.getGSON().fromJson(json, Paragraph.class);
  }

  public void updateOutputBuffer(int index, InterpreterResult.Type type, String output) {
    InterpreterResultMessage interpreterResultMessage = new InterpreterResultMessage(type, output);;
    if (outputBuffer.size() == index) {
      outputBuffer.add(interpreterResultMessage);
    } else if (outputBuffer.size() > index) {
      outputBuffer.set(index, interpreterResultMessage);
    } else {
      LOGGER.warn("Get output of index: {}, but there's only {} output in outputBuffer", index, outputBuffer.size());
    }
  }

  public void recover() {
    try {
      LOGGER.info("Recovering paragraph: {}", getId());

      this.interpreter = getBindedInterpreter();
      InterpreterSetting interpreterSetting = ((ManagedInterpreterGroup)
              interpreter.getInterpreterGroup()).getInterpreterSetting();
      Map<String, Object> config
              = interpreterSetting.getConfig(interpreter.getClassName());
      mergeConfig(config);

      if (shouldSkipRunParagraph()) {
        LOGGER.info("Skip to run blank paragraph. {}", getId());
        setStatus(Job.Status.FINISHED);
        return ;
      }
      setStatus(Status.READY);
      localProperties.put("isRecover", "true");
      for (List<Interpreter> sessions : this.interpreter.getInterpreterGroup().values()) {
        for (Interpreter intp : sessions) {
          // exclude ConfInterpreter
          if (intp instanceof RemoteInterpreter) {
            ((RemoteInterpreter) intp).setOpened(true);
          }
        }
      }

      if (getConfig().get("enabled") == null || (Boolean) getConfig().get("enabled")) {
        setAuthenticationInfo(getAuthenticationInfo());
        interpreter.getScheduler().submit(this);
      }

    } catch (InterpreterNotFoundException e) {
      InterpreterResult intpResult =
              new InterpreterResult(InterpreterResult.Code.ERROR,
                      String.format("Interpreter %s not found", this.intpText));
      setReturn(intpResult, e);
      setStatus(Job.Status.ERROR);
    } catch (Throwable e) {
      InterpreterResult intpResult =
              new InterpreterResult(InterpreterResult.Code.ERROR,
                      "Unexpected exception: " + ExceptionUtils.getStackTrace(e));
      setReturn(intpResult, e);
      setStatus(Job.Status.ERROR);
    }
  }
}
