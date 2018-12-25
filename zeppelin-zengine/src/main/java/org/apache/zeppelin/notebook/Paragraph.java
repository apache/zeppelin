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
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.common.JsonSerializable;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.helium.HeliumPackage;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.Interpreter.FormType;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterNotFoundException;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
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
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

/**
 * Paragraph is a representation of an execution unit.
 */
public class Paragraph extends JobWithProgressPoller<InterpreterResult> implements Cloneable,
    JsonSerializable {

  private static Logger LOGGER = LoggerFactory.getLogger(Paragraph.class);
  private static Pattern REPL_PATTERN =
      Pattern.compile("(\\s*)%([\\w\\.]+)(\\(.*?\\))?.*", Pattern.DOTALL);

  private String title;
  // text is composed of intpText and scriptText.
  private String text;
  private String user;
  private Date dateUpdated;
  // paragraph configs like isOpen, colWidth, etc
  private Map<String, Object> config = new HashMap<>();
  // form and parameter settings
  public GUI settings = new GUI();
  private InterpreterResult results;
  // Application states in this paragraph
  private final List<ApplicationState> apps = new LinkedList<>();

  /************** Transient fields which are not serializabled  into note json **************/
  private transient String intpText;
  private transient String scriptText;
  private transient Interpreter interpreter;
  private transient Note note;
  private transient AuthenticationInfo subject;
  // personalized
  private transient Map<String, Paragraph> userParagraphMap = new HashMap<>();
  private transient Map<String, String> localProperties = new HashMap<>();
  private transient Map<String, ParagraphRuntimeInfo> runtimeInfos = new HashMap<>();

  private static String  PARAGRAPH_CONFIG_FONTSIZE = "fontSize";
  private static int     PARAGRAPH_CONFIG_FONTSIZE_DEFAULT = 9;
  private static String  PARAGRAPH_CONFIG_COLWIDTH = "colWidth";
  private static int     PARAGRAPH_CONFIG_COLWIDTH_DEFAULT = 12;
  private static String  PARAGRAPH_CONFIG_RUNONSELECTIONCHANGE = "runOnSelectionChange";
  private static boolean PARAGRAPH_CONFIG_RUNONSELECTIONCHANGE_DEFAULT = true;
  private static String  PARAGRAPH_CONFIG_TITLE = "title";
  private static boolean PARAGRAPH_CONFIG_TITLE_DEFAULT = false;

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
    this.settings.setParams(Maps.newHashMap(p2.settings.getParams()));
    this.settings.setForms(Maps.newLinkedHashMap(p2.settings.getForms()));
    this.setConfig(Maps.newHashMap(p2.config));
    this.setAuthenticationInfo(p2.getAuthenticationInfo());
    this.title = p2.title;
    this.text = p2.text;
    this.results = p2.results;
    setStatus(p2.getStatus());
  }

  private static String generateId() {
    return "paragraph_" + System.currentTimeMillis() + "_" + new SecureRandom().nextInt();
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
      this.localProperties.clear();
      Matcher matcher = REPL_PATTERN.matcher(this.text);
      if (matcher.matches()) {
        String headingSpace = matcher.group(1);
        this.intpText = matcher.group(2);

        if (matcher.groupCount() == 3 && matcher.group(3) != null) {
          String localPropertiesText = matcher.group(3);
          String[] splits = localPropertiesText.substring(1, localPropertiesText.length() -1)
              .split(",");
          for (String split : splits) {
            String[] kv = split.split("=");
            if (StringUtils.isBlank(split) || kv.length == 0) {
              continue;
            }
            if (kv.length > 2) {
              throw new RuntimeException("Invalid paragraph properties format: " + split);
            }
            if (kv.length == 1) {
              localProperties.put(kv[0].trim(), kv[0].trim());
            } else {
              localProperties.put(kv[0].trim(), kv[1].trim());
            }
          }
          this.scriptText = this.text.substring(headingSpace.length() + intpText.length() +
              localPropertiesText.length() + 1).trim();
        } else {
          this.scriptText = this.text.substring(headingSpace.length() + intpText.length() + 1).trim();
        }
      } else {
        this.intpText = "";
        this.scriptText = this.text.trim();
      }
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
    return this.note.getInterpreterFactory().getInterpreter(user, note.getId(), intpText,
        note.getDefaultInterpreterGroup());
  }

  public void setInterpreter(Interpreter interpreter) {
    this.interpreter = interpreter;
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
    InterpreterContext interpreterContext = getInterpreterContext(null);

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
    int countCharactersBeforeScript = buffer.indexOf(this.scriptText);
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
        return this.interpreter.getProgress(getInterpreterContext(null));
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

  public boolean isBlankParagraph() {
    return Strings.isNullOrEmpty(scriptText);
  }

  public boolean execute(boolean blocking) {
    if (isBlankParagraph()) {
      LOGGER.info("Skip to run blank paragraph. {}", getId());
      setStatus(Job.Status.FINISHED);
      return true;
    }

    try {
      this.interpreter = getBindedInterpreter();
      setStatus(Status.READY);

      if (getConfig().get("enabled") == null || (Boolean) getConfig().get("enabled")) {
        setAuthenticationInfo(getAuthenticationInfo());
        interpreter.getScheduler().submit(this);
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
          new InterpreterResult(InterpreterResult.Code.ERROR);
      setReturn(intpResult, e);
      setStatus(Job.Status.ERROR);
      throw new RuntimeException(e);
    }
  }

  @Override
  protected InterpreterResult jobRun() throws Throwable {
    this.runtimeInfos.clear();
    this.interpreter = getBindedInterpreter();
    if (this.interpreter == null) {
      LOGGER.error("Can not find interpreter name " + intpText);
      throw new RuntimeException("Can not find interpreter for " + intpText);
    }
    LOGGER.info("Run paragraph [paragraph_id: {}, interpreter: {}, note_id: {}, user: {}]",
        getId(), this.interpreter.getClassName(), note.getId(), subject.getUser());
    InterpreterSetting interpreterSetting = ((ManagedInterpreterGroup)
        interpreter.getInterpreterGroup()).getInterpreterSetting();
    if (interpreterSetting != null) {
      interpreterSetting.waitForReady();
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
    if (interpreter.getFormType() == FormType.NATIVE) {
      settings.clear();
    } else if (interpreter.getFormType() == FormType.SIMPLE) {
      // inputs will be built from script body
      LinkedHashMap<String, Input> inputs = Input.extractSimpleQueryForm(script, false);
      LinkedHashMap<String, Input> noteInputs = Input.extractSimpleQueryForm(script, true);
      final AngularObjectRegistry angularRegistry =
          interpreter.getInterpreterGroup().getAngularObjectRegistry();
      String scriptBody = extractVariablesFromAngularRegistry(script, inputs, angularRegistry);

      settings.setForms(inputs);
      if (!noteInputs.isEmpty()) {
        if (!note.getNoteForms().isEmpty()) {
          Map<String, Input> currentNoteForms =  note.getNoteForms();
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
    }
    LOGGER.debug("RUN : " + script);
    try {
      InterpreterContext context = getInterpreterContext();
      InterpreterContext.set(context);
      InterpreterResult ret = interpreter.interpret(script, context);

      if (interpreter.getFormType() == FormType.NATIVE) {
        note.setNoteParams(context.getNoteGui().getParams());
        note.setNoteForms(context.getNoteGui().getForms());
      }

      if (Code.KEEP_PREVIOUS_RESULT == ret.code()) {
        return getReturn();
      }

      context.out.flush();
      List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
      resultMessages.addAll(ret.message());
      InterpreterResult res = new InterpreterResult(ret.code(), resultMessages);
      Paragraph p = getUserParagraph(getUser());
      if (null != p) {
        p.setResult(res);
        p.settings.setParams(settings.getParams());
      }

      // After the paragraph is executed,
      // need to apply the paragraph to the configuration in the
      // `interpreter-setting.json` config
      InterpreterSettingManager intpSettingManager
          = this.note.getInterpreterSettingManager();
      if (null != intpSettingManager) {
        InterpreterGroup intpGroup = interpreter.getInterpreterGroup();
        if (intpGroup instanceof ManagedInterpreterGroup) {
          String name = ((ManagedInterpreterGroup) intpGroup).getInterpreterSetting().getName();
          Map<String, Object> config
              = intpSettingManager.getConfigSetting(name);
          applyConfigSetting(config);
        }
      }

      return res;
    } finally {
      InterpreterContext.remove();
    }
  }

  @Override
  protected boolean jobAbort() {
    if (interpreter == null) {
      return true;
    }
    try {
      interpreter.cancel(getInterpreterContext(null));
    } catch (InterpreterException e) {
      throw new RuntimeException(e);
    }

    return true;
  }

  private InterpreterContext getInterpreterContext() {
    final Paragraph self = this;

    return getInterpreterContext(
        new InterpreterOutput(
            new InterpreterOutputListener() {
              ParagraphJobListener paragraphJobListener = (ParagraphJobListener) getListener();

              @Override
              public void onAppend(int index, InterpreterResultMessageOutput out, byte[] line) {
                if (null != paragraphJobListener) {
                  paragraphJobListener.onOutputAppend(self, index, new String(line));
                }
              }

              @Override
              public void onUpdate(int index, InterpreterResultMessageOutput out) {
                try {
                  if (null != paragraphJobListener) {
                    paragraphJobListener.onOutputUpdate(
                        self, index, out.toInterpreterResultMessage());
                  }
                } catch (IOException e) {
                  LOGGER.error(e.getMessage(), e);
                }
              }

              @Override
              public void onUpdateAll(InterpreterOutput out) {
                try {
                  List<InterpreterResultMessage> messages = out.toInterpreterResultMessage();
                  if (null != paragraphJobListener) {
                    paragraphJobListener.onOutputUpdateAll(self, messages);
                  }
                  updateParagraphResult(messages);
                } catch (IOException e) {
                  LOGGER.error(e.getMessage(), e);
                }
              }

      private void updateParagraphResult(List<InterpreterResultMessage> msgs) {
        // update paragraph results
        InterpreterResult result = new InterpreterResult(Code.SUCCESS, msgs);
        setReturn(result, null);
      }
    }));
  }

  private InterpreterContext getInterpreterContext(InterpreterOutput output) {
    AngularObjectRegistry registry = null;
    ResourcePool resourcePool = null;

    if (this.interpreter != null) {
      registry = this.interpreter.getInterpreterGroup().getAngularObjectRegistry();
      resourcePool = this.interpreter.getInterpreterGroup().getResourcePool();
    }

    Credentials credentials = note.getCredentials();
    if (subject != null) {
      UserCredentials userCredentials =
          credentials.getUserCredentials(subject.getUser());
      subject.setUserCredentials(userCredentials);
    }

    InterpreterContext interpreterContext =
        InterpreterContext.builder()
            .setNoteId(note.getId())
            .setNoteName(note.getName())
            .setParagraphId(getId())
            .setReplName(intpText)
            .setParagraphTitle(title)
            .setParagraphText(text)
            .setAuthenticationInfo(subject)
            .setLocalProperties(localProperties)
            .setConfig(config)
            .setGUI(settings)
            .setNoteGUI(getNoteGui())
            .setAngularObjectRegistry(registry)
            .setResourcePool(resourcePool)
            .setInterpreterOut(output)
            .build();
    return interpreterContext;
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

  public void setConfig(Map<String, Object> config) {
    this.config = config;
  }

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
  public void applyConfigSetting(Map<String, Object> newConfig) {
    if (null == newConfig || 0 == newConfig.size()) {
      newConfig = getDefaultConfigSetting();
    }

    List<String> keysToRemove = Arrays.asList(PARAGRAPH_CONFIG_FONTSIZE,
        PARAGRAPH_CONFIG_COLWIDTH, PARAGRAPH_CONFIG_RUNONSELECTIONCHANGE,
        PARAGRAPH_CONFIG_TITLE);
    for (String removeKey : keysToRemove) {
      if ((false == newConfig.containsKey(removeKey))
          && (true == config.containsKey(removeKey))) {
        this.config.remove(removeKey);
      }
    }

    this.config.putAll(newConfig);
  }

  // default parameters of the interpreter
  private Map<String, Object> getDefaultConfigSetting() {
    Map<String, Object> config = new HashMap<>();
    config.put(PARAGRAPH_CONFIG_FONTSIZE, PARAGRAPH_CONFIG_FONTSIZE_DEFAULT);
    config.put(PARAGRAPH_CONFIG_COLWIDTH, PARAGRAPH_CONFIG_COLWIDTH_DEFAULT);
    config.put(PARAGRAPH_CONFIG_RUNONSELECTIONCHANGE, PARAGRAPH_CONFIG_RUNONSELECTIONCHANGE_DEFAULT);
    config.put(PARAGRAPH_CONFIG_TITLE, PARAGRAPH_CONFIG_TITLE_DEFAULT);

    return config;
  }

  public void setReturn(InterpreterResult value, Throwable t) {
    setResult(value);
    setException(t);
  }

  private String getApplicationId(HeliumPackage pkg) {
    return "app_" + getNote().getId() + "-" + getId() + pkg.getName().replaceAll("\\.", "_");
  }

  public ApplicationState createOrGetApplicationState(HeliumPackage pkg) {
    synchronized (apps) {
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
  }


  public ApplicationState getApplicationState(String appId) {
    synchronized (apps) {
      for (ApplicationState as : apps) {
        if (as.getId().equals(appId)) {
          return as;
        }
      }
    }

    return null;
  }

  public List<ApplicationState> getAllApplicationStates() {
    synchronized (apps) {
      return new LinkedList<>(apps);
    }
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
      return note.getInterpreterFactory().getInterpreter(user, note.getId(), replName,
          note.getDefaultInterpreterGroup()) != null;
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
        info.addValue(infos.get(key));
      }
    }
  }

  public Map<String, ParagraphRuntimeInfo> getRuntimeInfos() {
    return runtimeInfos;
  }

  public void cleanRuntimeInfos() {
    this.runtimeInfos.clear();
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
    return Note.getGson().toJson(this);
  }

  public static Paragraph fromJson(String json) {
    return Note.getGson().fromJson(json, Paragraph.class);
  }

}
