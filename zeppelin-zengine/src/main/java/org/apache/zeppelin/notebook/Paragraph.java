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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.security.SecureRandom;
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
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.JobListener;
import org.apache.zeppelin.scheduler.Scheduler;
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
public class Paragraph extends Job implements Cloneable, JsonSerializable {

  private static Logger logger = LoggerFactory.getLogger(Paragraph.class);
  private static Pattern REPL_PATTERN = Pattern.compile("(\\s*)%([\\w\\.]+).*", Pattern.DOTALL);

  private transient InterpreterFactory interpreterFactory;
  private transient Interpreter interpreter;
  private transient Note note;
  private transient AuthenticationInfo authenticationInfo;
  private transient Map<String, Paragraph> userParagraphMap = Maps.newHashMap(); // personalized

  private String title;
  private String text;  // text is composed of intpText and scriptText.
  private transient String intpText;
  private transient String scriptText;
  private String user;
  private Date dateUpdated;
  // paragraph configs like isOpen, colWidth, etc
  private Map<String, Object> config = new HashMap<>();
  public GUI settings = new GUI();          // form and parameter settings

  // since zeppelin-0.7.0, zeppelin stores multiple results of the paragraph
  // see ZEPPELIN-212
  volatile Object results;

  // For backward compatibility of note.json format after ZEPPELIN-212
  volatile Object result;
  private Map<String, ParagraphRuntimeInfo> runtimeInfos;

  /**
   * Application states in this paragraph
   */
  private final List<ApplicationState> apps = new LinkedList<>();

  @VisibleForTesting
  Paragraph() {
    super(generateId(), null);
  }

  public Paragraph(String paragraphId, Note note, JobListener listener,
      InterpreterFactory interpreterFactory) {
    super(paragraphId, generateId(), listener);
    this.note = note;
    this.interpreterFactory = interpreterFactory;
  }

  public Paragraph(Note note, JobListener listener, InterpreterFactory interpreterFactory) {
    super(generateId(), listener);
    this.note = note;
    this.interpreterFactory = interpreterFactory;
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
  public synchronized void setResult(Object results) {
    this.results = results;
  }

  public Paragraph cloneParagraphForUser(String user) {
    Paragraph p = new Paragraph();
    p.interpreterFactory = interpreterFactory;
    p.note = note;
    p.settings.setParams(Maps.newHashMap(settings.getParams()));
    p.settings.setForms(Maps.newLinkedHashMap(settings.getForms()));
    p.setConfig(Maps.newHashMap(config));
    if (getAuthenticationInfo() != null) {
      p.setAuthenticationInfo(getAuthenticationInfo());
    }
    p.setTitle(getTitle());
    p.setText(getText());
    p.setResult(getReturn());
    p.setStatus(Status.READY);
    p.setId(getId());
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
    // strip white space from the beginning
    this.text = newText;
    this.dateUpdated = new Date();
    // parse text to get interpreter component
    if (this.text != null) {
      Matcher matcher = REPL_PATTERN.matcher(this.text);
      if (matcher.matches()) {
        String headingSpace = matcher.group(1);
        this.intpText = matcher.group(2);
        this.scriptText = this.text.substring(headingSpace.length() + intpText.length() + 1).trim();
      } else {
        this.intpText = "";
        this.scriptText = this.text;
      }
    }
  }

  public AuthenticationInfo getAuthenticationInfo() {
    return authenticationInfo;
  }

  public void setAuthenticationInfo(AuthenticationInfo authenticationInfo) {
    this.authenticationInfo = authenticationInfo;
    this.user = authenticationInfo.getUser();
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

  public boolean isEnabled() {
    Boolean enabled = (Boolean) config.get("enabled");
    return enabled == null || enabled.booleanValue();
  }

  public Interpreter getBindedInterpreter() {
    return this.interpreterFactory.getInterpreter(user, note.getId(), intpText);
  }

  public void setInterpreter(Interpreter interpreter) {
    this.interpreter = interpreter;
  }

  public List<InterpreterCompletion> completion(String buffer, int cursor) {
    String lines[] = buffer.split(System.getProperty("line.separator"));
    if (lines.length > 0 && lines[0].startsWith("%") && cursor <= lines[0].trim().length()) {
      int idx = lines[0].indexOf(' ');
      if (idx < 0 || (idx > 0 && cursor <= idx)) {
        return note.getInterpreterCompletion();
      }
    }
    String trimmedBuffer = buffer != null ? buffer.trim() : null;
    cursor = calculateCursorPosition(buffer, trimmedBuffer, cursor);

    InterpreterContext interpreterContext = getInterpreterContextWithoutRunner(null);

    try {
      if (this.interpreter != null) {
        return this.interpreter.completion(scriptText, cursor, interpreterContext);
      } else {
        return null;
      }
    } catch (InterpreterException e) {
      throw new RuntimeException("Fail to get completion", e);
    }
  }

  public int calculateCursorPosition(String buffer, String trimmedBuffer, int cursor) {
    int countWhitespacesAtStart = buffer.indexOf(trimmedBuffer);
    if (countWhitespacesAtStart > 0) {
      cursor -= countWhitespacesAtStart;
    }

    // parse text to get interpreter component
    String repl = null;
    if (trimmedBuffer != null) {
      Matcher matcher = REPL_PATTERN.matcher(trimmedBuffer);
      if (matcher.matches()) {
        repl = matcher.group(2);
      }
    }

    if (repl != null && cursor > repl.length()) {
      String body = trimmedBuffer.substring(repl.length() + 1);
      cursor -= repl.length() + 1 + body.indexOf(body.trim());
    }

    return cursor;
  }

  public void setInterpreterFactory(InterpreterFactory factory) {
    this.interpreterFactory = factory;
  }

  public InterpreterResult getResult() {
    return (InterpreterResult) getReturn();
  }

  @Override
  public synchronized Object getReturn() {
    return results;
  }

  public Object getPreviousResultFormat() {
    return result;
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

  private boolean hasPermission(List<String> userAndRoles, List<String> intpUsersAndRoles) {
    if (1 > intpUsersAndRoles.size()) {
      return true;
    }
    Set<String> intersection = new HashSet<>(intpUsersAndRoles);
    intersection.retainAll(userAndRoles);
    return (intpUsersAndRoles.isEmpty() || (intersection.size() > 0));
  }

  public boolean isBlankParagraph() {
    return Strings.isNullOrEmpty(scriptText);
  }

  public boolean execute(boolean blocking) {
    if (isBlankParagraph()) {
      logger.info("skip to run blank paragraph. {}", getId());
      setStatus(Job.Status.FINISHED);
      return true;
    }

    clearRuntimeInfo(null);
    this.interpreter = getBindedInterpreter();

    if (interpreter == null) {
      String intpExceptionMsg =
          getJobName() + "'s Interpreter " + getIntpText() + " not found";
      RuntimeException intpException = new RuntimeException(intpExceptionMsg);
      InterpreterResult intpResult =
          new InterpreterResult(InterpreterResult.Code.ERROR, intpException.getMessage());
      setReturn(intpResult, intpException);
      setStatus(Job.Status.ERROR);
      throw intpException;
    }
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
  }

  @Override
  protected Object jobRun() throws Throwable {
    logger.info("Run paragraph {} using {} ", getId(), intpText);
    this.interpreter = getBindedInterpreter();
    if (this.interpreter == null) {
      logger.error("Can not find interpreter name " + intpText);
      throw new RuntimeException("Can not find interpreter for " + intpText);
    }
    InterpreterSetting interpreterSetting = ((ManagedInterpreterGroup)
        interpreter.getInterpreterGroup()).getInterpreterSetting();
    if (interpreterSetting != null) {
      interpreterSetting.waitForReady();
    }
    if (this.hasUser() && this.note.hasInterpreterBinded()) {
      if (interpreterSetting != null && interpreterHasUser(interpreterSetting)
          && isUserAuthorizedToAccessInterpreter(interpreterSetting.getOption()) == false) {
        logger.error("{} has no permission for {} ", authenticationInfo.getUser(), intpText);
        return new InterpreterResult(Code.ERROR,
            authenticationInfo.getUser() + " has no permission for " + intpText);
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
      // inputs will be built from script scriptText
      LinkedHashMap<String, Input> inputs = Input.extractSimpleQueryForm(this.scriptText);
      final AngularObjectRegistry angularRegistry =
          interpreter.getInterpreterGroup().getAngularObjectRegistry();
      String scriptBody = extractVariablesFromAngularRegistry(this.scriptText, inputs,
          angularRegistry);
      settings.setForms(inputs);
      script = Input.getSimpleQuery(settings.getParams(), scriptBody);
    }
    logger.debug("RUN : " + script);
    try {
      InterpreterContext context = getInterpreterContext();
      InterpreterContext.set(context);
      InterpreterResult ret = interpreter.interpret(script, context);

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

      return res;
    } finally {
      InterpreterContext.remove();
    }
  }

  private boolean hasUser() {
    return this.user != null;
  }

  private boolean interpreterHasUser(InterpreterSetting interpreterSetting) {
    return interpreterSetting.getOption().permissionIsSet() &&
        interpreterSetting.getOption().getOwners() != null;
  }

  private boolean isUserAuthorizedToAccessInterpreter(InterpreterOption intpOpt) {
    return intpOpt.permissionIsSet() && hasPermission(authenticationInfo.getUsersAndRoles(),
        intpOpt.getOwners());
  }

  @Override
  protected boolean jobAbort() {
    if (interpreter == null) {
      return true;
    }
    Scheduler scheduler = interpreter.getScheduler();
    if (scheduler == null) {
      return true;
    }

    Job job = scheduler.removeFromWaitingQueue(getId());
    if (job != null) {
      job.setStatus(Status.ABORT);
    } else {
      try {
        interpreter.cancel(getInterpreterContextWithoutRunner(null));
      } catch (InterpreterException e) {
        throw new RuntimeException(e);
      }
    }
    return true;
  }

  private InterpreterContext getInterpreterContext() {
    final Paragraph self = this;

    return getInterpreterContext(new InterpreterOutput(new InterpreterOutputListener() {
      @Override
      public void onAppend(int index, InterpreterResultMessageOutput out, byte[] line) {
        ((ParagraphJobListener) getListener()).onOutputAppend(self, index, new String(line));
      }

      @Override
      public void onUpdate(int index, InterpreterResultMessageOutput out) {
        try {
          ((ParagraphJobListener) getListener())
              .onOutputUpdate(self, index, out.toInterpreterResultMessage());
        } catch (IOException e) {
          logger.error(e.getMessage(), e);
        }
      }

      @Override
      public void onUpdateAll(InterpreterOutput out) {
        try {
          List<InterpreterResultMessage> messages = out.toInterpreterResultMessage();
          ((ParagraphJobListener) getListener()).onOutputUpdateAll(self, messages);
          updateParagraphResult(messages);
        } catch (IOException e) {
          logger.error(e.getMessage(), e);
        }

      }

      private void updateParagraphResult(List<InterpreterResultMessage> msgs) {
        // update paragraph result
        InterpreterResult result = new InterpreterResult(Code.SUCCESS, msgs);
        setReturn(result, null);
      }
    }));
  }

  private InterpreterContext getInterpreterContextWithoutRunner(InterpreterOutput output) {
    AngularObjectRegistry registry = null;
    ResourcePool resourcePool = null;

    if (this.interpreter != null) {
      registry = this.interpreter.getInterpreterGroup().getAngularObjectRegistry();
      resourcePool = this.interpreter.getInterpreterGroup().getResourcePool();
    }

    List<InterpreterContextRunner> runners = new LinkedList<>();

    final Paragraph self = this;

    Credentials credentials = note.getCredentials();
    setAuthenticationInfo(new AuthenticationInfo(getUser()));

    if (authenticationInfo.getUser() != null) {
      UserCredentials userCredentials =
          credentials.getUserCredentials(authenticationInfo.getUser());
      authenticationInfo.setUserCredentials(userCredentials);
    }

    InterpreterContext interpreterContext =
        new InterpreterContext(note.getId(), getId(), intpText, this.getTitle(),
            this.getText(), this.getAuthenticationInfo(), this.getConfig(), this.settings, registry,
            resourcePool, runners, output);
    return interpreterContext;
  }

  private InterpreterContext getInterpreterContext(InterpreterOutput output) {
    AngularObjectRegistry registry = null;
    ResourcePool resourcePool = null;

    if (this.interpreter != null) {
      registry = this.interpreter.getInterpreterGroup().getAngularObjectRegistry();
      resourcePool = this.interpreter.getInterpreterGroup().getResourcePool();
    }

    List<InterpreterContextRunner> runners = new LinkedList<>();
    for (Paragraph p : note.getParagraphs()) {
      runners.add(new ParagraphRunner(note, note.getId(), p.getId()));
    }

    final Paragraph self = this;

    Credentials credentials = note.getCredentials();
    if (authenticationInfo != null) {
      UserCredentials userCredentials =
          credentials.getUserCredentials(authenticationInfo.getUser());
      authenticationInfo.setUserCredentials(userCredentials);
    }

    InterpreterContext interpreterContext =
        new InterpreterContext(note.getId(), getId(), intpText, this.getTitle(),
            this.getText(), this.getAuthenticationInfo(), this.getConfig(), this.settings, registry,
            resourcePool, runners, output);
    return interpreterContext;
  }

  public InterpreterContextRunner getInterpreterContextRunner() {

    return new ParagraphRunner(note, note.getId(), getId());
  }

  public void setStatusToUserParagraph(Status status) {
    String user = getUser();
    if (null != user) {
      getUserParagraph(getUser()).setStatus(status);
    }
  }

  static class ParagraphRunner extends InterpreterContextRunner {

    private transient Note note;

    public ParagraphRunner(Note note, String noteId, String paragraphId) {
      super(noteId, paragraphId);
      this.note = note;
    }

    @Override
    public void run() {
      note.run(getParagraphId(), false);
    }
  }

  public Map<String, Object> getConfig() {
    return config;
  }

  public void setConfig(Map<String, Object> config) {
    this.config = config;
  }

  public void setReturn(InterpreterResult value, Throwable t) {
    setResult(value);
    setException(t);
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    Paragraph paraClone = (Paragraph) this.clone();
    return paraClone;
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
    return interpreterFactory.getInterpreter(user, note.getId(), replName) != null;
  }

  public void updateRuntimeInfos(String label, String tooltip, Map<String, String> infos,
      String group, String intpSettingId) {
    if (this.runtimeInfos == null) {
      this.runtimeInfos = new HashMap<String, ParagraphRuntimeInfo>();
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

  /**
   * Remove runtimeinfo taht were got from the setting with id settingId
   * @param settingId
   */
  public void clearRuntimeInfo(String settingId) {
    if (settingId != null) {
      Set<String> keys = runtimeInfos.keySet();
      if (keys.size() > 0) {
        List<String> infosToRemove = new ArrayList<>();
        for (String key : keys) {
          ParagraphRuntimeInfo paragraphRuntimeInfo = runtimeInfos.get(key);
          if (paragraphRuntimeInfo.getInterpreterSettingId().equals(settingId)) {
            infosToRemove.add(key);
          }
        }
        if (infosToRemove.size() > 0) {
          for (String info : infosToRemove) {
            runtimeInfos.remove(info);
          }
        }
      }
    } else {
      this.runtimeInfos = null;
    }
  }

  public void clearRuntimeInfos() {
    if (this.runtimeInfos != null) {
      this.runtimeInfos.clear();
    }
  }

  public Map<String, ParagraphRuntimeInfo> getRuntimeInfos() {
    return runtimeInfos;
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
    if (results != null ? !results.equals(paragraph.results) : paragraph.results != null) {
      return false;
    }
    if (result != null ? !result.equals(paragraph.result) : paragraph.result != null) {
      return false;
    }
    return runtimeInfos != null ?
        runtimeInfos.equals(paragraph.runtimeInfos) : paragraph.runtimeInfos == null;

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
    result1 = 31 * result1 + (result != null ? result.hashCode() : 0);
    result1 = 31 * result1 + (runtimeInfos != null ? runtimeInfos.hashCode() : 0);
    return result1;
  }

  public String toJson() {
    return Note.getGson().toJson(this);
  }

  public static Paragraph fromJson(String json) {
    return Note.getGson().fromJson(json, Paragraph.class);
  }

}
