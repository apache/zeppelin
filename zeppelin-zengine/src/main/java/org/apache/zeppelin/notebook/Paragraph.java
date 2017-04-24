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

import com.google.common.collect.Maps;
import com.google.common.base.Strings;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.completer.CompletionType;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.helium.HeliumPackage;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.apache.zeppelin.user.UserCredentials;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.Interpreter.FormType;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.JobListener;
import org.apache.zeppelin.scheduler.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import com.google.common.annotations.VisibleForTesting;

/**
 * Paragraph is a representation of an execution unit.
 */
public class Paragraph extends Job implements Serializable, Cloneable {

  private static final long serialVersionUID = -6328572073497992016L;

  private static Logger logger = LoggerFactory.getLogger(Paragraph.class);
  private transient InterpreterFactory factory;
  private transient InterpreterSettingManager interpreterSettingManager;
  private transient Note note;
  private transient AuthenticationInfo authenticationInfo;
  private transient Map<String, Paragraph> userParagraphMap = Maps.newHashMap(); // personalized

  String title;
  String text;
  String user;
  Date dateUpdated;
  private Map<String, Object> config; // paragraph configs like isOpen, colWidth, etc
  public GUI settings;          // form and parameter settings

  // since zeppelin-0.7.0, zeppelin stores multiple results of the paragraph
  // see ZEPPELIN-212
  Object results;

  // For backward compatibility of note.json format after ZEPPELIN-212
  Object result;
  private Map<String, ParagraphRuntimeInfo> runtimeInfos;

  /**
   * Application states in this paragraph
   */
  private final List<ApplicationState> apps = new LinkedList<>();

  @VisibleForTesting
  Paragraph() {
    super(generateId(), null);
    config = new HashMap<>();
    settings = new GUI();
  }

  public Paragraph(String paragraphId, Note note, JobListener listener,
      InterpreterFactory factory, InterpreterSettingManager interpreterSettingManager) {
    super(paragraphId, generateId(), listener);
    this.note = note;
    this.factory = factory;
    this.interpreterSettingManager = interpreterSettingManager;
    title = null;
    text = null;
    authenticationInfo = null;
    user = null;
    dateUpdated = null;
    settings = new GUI();
    config = new HashMap<>();
  }

  public Paragraph(Note note, JobListener listener, InterpreterFactory factory,
      InterpreterSettingManager interpreterSettingManager) {
    super(generateId(), listener);
    this.note = note;
    this.factory = factory;
    this.interpreterSettingManager = interpreterSettingManager;
    title = null;
    text = null;
    authenticationInfo = null;
    dateUpdated = null;
    settings = new GUI();
    config = new HashMap<>();
  }

  private static String generateId() {
    return "paragraph_" + System.currentTimeMillis() + "_" + new Random(System.currentTimeMillis())
        .nextInt();
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
  public void setResult(Object results) {
    this.results = results;
  }

  public Paragraph cloneParagraphForUser(String user) {
    Paragraph p = new Paragraph();
    p.settings.setParams(Maps.newHashMap(settings.getParams()));
    p.settings.setForms(Maps.newLinkedHashMap(settings.getForms()));
    p.setConfig(Maps.newHashMap(config));
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
    this.text = newText;
    this.dateUpdated = new Date();
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

  public String getRequiredReplName() {
    return getRequiredReplName(text);
  }

  public static String getRequiredReplName(String text) {
    if (text == null) {
      return null;
    }

    String trimmed = text.trim();
    if (!trimmed.startsWith("%")) {
      return null;
    }

    // get script head
    int scriptHeadIndex = 0;
    for (int i = 0; i < trimmed.length(); i++) {
      char ch = trimmed.charAt(i);
      if (Character.isWhitespace(ch) || ch == '(' || ch == '\n') {
        break;
      }
      scriptHeadIndex = i;
    }
    if (scriptHeadIndex < 1) {
      return null;
    }
    String head = text.substring(1, scriptHeadIndex + 1);
    return head;
  }

  public String getScriptBody() {
    return getScriptBody(text);
  }

  public static String getScriptBody(String text) {
    if (text == null) {
      return null;
    }

    String magic = getRequiredReplName(text);
    if (magic == null) {
      return text;
    }

    String trimmed = text.trim();
    if (magic.length() + 1 >= trimmed.length()) {
      return "";
    }
    return trimmed.substring(magic.length() + 1).trim();
  }

  public Interpreter getRepl(String name) {
    return factory.getInterpreter(user, note.getId(), name);
  }

  public Interpreter getCurrentRepl() {
    return getRepl(getRequiredReplName());
  }

  public List<InterpreterCompletion> getInterpreterCompletion() {
    List<InterpreterCompletion> completion = new LinkedList();
    for (InterpreterSetting intp : interpreterSettingManager.getInterpreterSettings(note.getId())) {
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

  public List<InterpreterCompletion> completion(String buffer, int cursor) {
    String lines[] = buffer.split(System.getProperty("line.separator"));
    if (lines.length > 0 && lines[0].startsWith("%") && cursor <= lines[0].trim().length()) {

      int idx = lines[0].indexOf(' ');
      if (idx < 0 || (idx > 0 && cursor <= idx)) {
        return getInterpreterCompletion();
      }
    }

    String replName = getRequiredReplName(buffer);
    if (replName != null && cursor > replName.length()) {
      cursor -= replName.length() + 1;
    }

    String body = getScriptBody(buffer);
    Interpreter repl = getRepl(replName);
    if (repl == null) {
      return null;
    }

    InterpreterContext interpreterContext = getInterpreterContextWithoutRunner(null);

    List completion = repl.completion(body, cursor, interpreterContext);
    return completion;
  }

  public void setInterpreterFactory(InterpreterFactory factory) {
    this.factory = factory;
  }

  public void setInterpreterSettingManager(InterpreterSettingManager interpreterSettingManager) {
    this.interpreterSettingManager = interpreterSettingManager;
  }

  public InterpreterResult getResult() {
    return (InterpreterResult) getReturn();
  }

  @Override
  public Object getReturn() {
    return results;
  }

  public Object getPreviousResultFormat() {
    return result;
  }

  @Override
  public int progress() {
    String replName = getRequiredReplName();
    Interpreter repl = getRepl(replName);
    if (repl != null) {
      return repl.getProgress(getInterpreterContext(null));
    } else {
      return 0;
    }
  }

  @Override
  public Map<String, Object> info() {
    return null;
  }

  private boolean hasPermission(String user, List<String> intpUsers) {
    if (1 > intpUsers.size()) {
      return true;
    }

    for (String u : intpUsers) {
      if (user.trim().equals(u.trim())) {
        return true;
      }
    }
    return false;
  }

  public boolean isBlankParagraph() {
    return Strings.isNullOrEmpty(getText()) || getText().trim().equals(getMagic());
  }


  @Override
  protected Object jobRun() throws Throwable {
    String replName = getRequiredReplName();
    Interpreter repl = getRepl(replName);
    logger.info("run paragraph {} using {} " + repl, getId(), replName);
    if (repl == null) {
      logger.error("Can not find interpreter name " + repl);
      throw new RuntimeException("Can not find interpreter for " + getRequiredReplName());
    }
    InterpreterSetting intp = getInterpreterSettingById(repl.getInterpreterGroup().getId());
    while (intp.getStatus().equals(
        org.apache.zeppelin.interpreter.InterpreterSetting.Status.DOWNLOADING_DEPENDENCIES)) {
      Thread.sleep(200);
    }
    if (this.noteHasUser() && this.noteHasInterpreters()) {
      if (intp != null && interpreterHasUser(intp)
          && isUserAuthorizedToAccessInterpreter(intp.getOption()) == false) {
        logger.error("{} has no permission for {} ", authenticationInfo.getUser(), repl);
        return new InterpreterResult(Code.ERROR,
            authenticationInfo.getUser() + " has no permission for " + getRequiredReplName());
      }
    }

    for (Paragraph p : userParagraphMap.values()) {
      p.setText(getText());
    }

    String script = getScriptBody();
    // inject form
    if (repl.getFormType() == FormType.NATIVE) {
      settings.clear();
    } else if (repl.getFormType() == FormType.SIMPLE) {
      String scriptBody = getScriptBody();
      // inputs will be built from script body
      LinkedHashMap<String, Input> inputs = Input.extractSimpleQueryForm(scriptBody);

      final AngularObjectRegistry angularRegistry =
          repl.getInterpreterGroup().getAngularObjectRegistry();

      scriptBody = extractVariablesFromAngularRegistry(scriptBody, inputs, angularRegistry);

      settings.setForms(inputs);
      script = Input.getSimpleQuery(settings.getParams(), scriptBody);
    }
    logger.debug("RUN : " + script);
    try {
      InterpreterContext context = getInterpreterContext();
      InterpreterContext.set(context);
      InterpreterResult ret = repl.interpret(script, context);

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

  private boolean noteHasUser() {
    return this.user != null;
  }

  private boolean noteHasInterpreters() {
    return !interpreterSettingManager.getInterpreterSettings(note.getId()).isEmpty();
  }

  private boolean interpreterHasUser(InterpreterSetting intp) {
    return intp.getOption().permissionIsSet() && intp.getOption().getUsers() != null;
  }

  private boolean isUserAuthorizedToAccessInterpreter(InterpreterOption intpOpt) {
    return intpOpt.permissionIsSet() && hasPermission(authenticationInfo.getUser(),
        intpOpt.getUsers());
  }

  private InterpreterSetting getInterpreterSettingById(String id) {
    InterpreterSetting setting = null;
    for (InterpreterSetting i : interpreterSettingManager.getInterpreterSettings(note.getId())) {
      if (id.startsWith(i.getId())) {
        setting = i;
        break;
      }
    }
    return setting;
  }

  @Override
  protected boolean jobAbort() {
    Interpreter repl = getRepl(getRequiredReplName());
    if (repl == null) {
      // when interpreters are already destroyed
      return true;
    }

    Scheduler scheduler = repl.getScheduler();
    if (scheduler == null) {
      return true;
    }

    Job job = scheduler.removeFromWaitingQueue(getId());
    if (job != null) {
      job.setStatus(Status.ABORT);
    } else {
      repl.cancel(getInterpreterContextWithoutRunner(null));
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

    if (!interpreterSettingManager.getInterpreterSettings(note.getId()).isEmpty()) {
      InterpreterSetting intpGroup =
          interpreterSettingManager.getInterpreterSettings(note.getId()).get(0);
      registry = intpGroup.getInterpreterGroup(getUser(), note.getId()).getAngularObjectRegistry();
      resourcePool = intpGroup.getInterpreterGroup(getUser(), note.getId()).getResourcePool();
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
        new InterpreterContext(note.getId(), getId(), getRequiredReplName(), this.getTitle(),
            this.getText(), this.getAuthenticationInfo(), this.getConfig(), this.settings, registry,
            resourcePool, runners, output);
    return interpreterContext;
  }

  private InterpreterContext getInterpreterContext(InterpreterOutput output) {
    AngularObjectRegistry registry = null;
    ResourcePool resourcePool = null;

    if (!interpreterSettingManager.getInterpreterSettings(note.getId()).isEmpty()) {
      InterpreterSetting intpGroup =
          interpreterSettingManager.getInterpreterSettings(note.getId()).get(0);
      registry = intpGroup.getInterpreterGroup(getUser(), note.getId()).getAngularObjectRegistry();
      resourcePool = intpGroup.getInterpreterGroup(getUser(), note.getId()).getResourcePool();
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
        new InterpreterContext(note.getId(), getId(), getRequiredReplName(), this.getTitle(),
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
      note.run(getParagraphId());
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

  public String getMagic() {
    String magic = StringUtils.EMPTY;
    String text = getText();
    if (text != null && text.startsWith("%")) {
      magic = text.split("\\s+")[0];
      if (isValidInterpreter(magic.substring(1))) {
        return magic;
      } else {
        return StringUtils.EMPTY;
      }
    }
    return magic;
  }

  private boolean isValidInterpreter(String replName) {
    try {
      return factory.getInterpreter(user, note.getId(), replName) != null;
    } catch (InterpreterException e) {
      // ignore this exception, it would be recaught when running paragraph.
      return false;
    }
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

  public Map<String, ParagraphRuntimeInfo> getRuntimeInfos() {
    return runtimeInfos;
  }
}
