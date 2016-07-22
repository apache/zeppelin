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
 *
 */
public class Paragraph extends Job implements Serializable, Cloneable {
  private static final long serialVersionUID = -6328572073497992016L;

  private static Logger logger = LoggerFactory.getLogger(Paragraph.class);
  private transient InterpreterFactory factory;
  private transient Note note;
  private transient AuthenticationInfo authenticationInfo;
  private transient String effectiveText;

  String title;
  String text;
  String user;
  Date dateUpdated;
  private Map<String, Object> config; // paragraph configs like isOpen, colWidth, etc
  public final GUI settings;          // form and parameter settings

  /**
   * Applicaiton states in this paragraph
   */
  private final List<ApplicationState> apps =  new LinkedList<ApplicationState>();

  @VisibleForTesting
  Paragraph() {
    super(generateId(), null);
    config = new HashMap<>();
    settings = new GUI();
  }

  public Paragraph(String paragraphId, Note note, JobListener listener,
                   InterpreterFactory factory) {
    super(paragraphId, generateId(), listener);
    this.note = note;
    this.factory = factory;
    title = null;
    text = null;
    authenticationInfo = null;
    user = null;
    dateUpdated = null;
    settings = new GUI();
    config = new HashMap<String, Object>();
  }

  public Paragraph(Note note, JobListener listener, InterpreterFactory factory) {
    super(generateId(), listener);
    this.note = note;
    this.factory = factory;
    title = null;
    text = null;
    authenticationInfo = null;
    dateUpdated = null;
    settings = new GUI();
    config = new HashMap<String, Object>();
  }

  private static String generateId() {
    return "paragraph_" + System.currentTimeMillis() + "_"
           + new Random(System.currentTimeMillis()).nextInt();
  }

  public String getText() {
    return text;
  }

  public void setText(String newText) {
    this.text = newText;
    this.dateUpdated = new Date();
  }

  public void setEffectiveText(String effectiveText) {
    this.effectiveText = effectiveText;
  }

  public String getEffectiveText() {
    return effectiveText;
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
    return getRequiredReplName(null != effectiveText ? effectiveText : text);
  }

  public static String getRequiredReplName(String text) {
    if (text == null) {
      return null;
    }

    // get script head
    int scriptHeadIndex = 0;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (Character.isWhitespace(ch) || ch == '(') {
        scriptHeadIndex = i;
        break;
      }
    }
    if (scriptHeadIndex == 0) {
      return null;
    }
    String head = text.substring(0, scriptHeadIndex);
    if (head.startsWith("%")) {
      return head.substring(1);
    } else {
      return null;
    }
  }

  public String getScriptBody() {
    return getScriptBody(null != effectiveText ? effectiveText : text);
  }

  public static String getScriptBody(String text) {
    if (text == null) {
      return null;
    }

    String magic = getRequiredReplName(text);
    if (magic == null) {
      return text;
    }
    if (magic.length() + 1 >= text.length()) {
      return "";
    }
    return text.substring(magic.length() + 1).trim();
  }

  public Interpreter getRepl(String name) {
    return factory.getInterpreter(note.getId(), name);
  }

  public Interpreter getCurrentRepl() {
    return getRepl(getRequiredReplName());
  }

  public List<InterpreterCompletion> getInterpreterCompletion() {
    List<InterpreterCompletion> completion = new LinkedList();
    for (InterpreterSetting intp: factory.getInterpreterSettings(note.getId())){
      List<InterpreterInfo> intInfo = intp.getInterpreterInfos();
      if (intInfo.size() > 1) {
        for (InterpreterInfo info : intInfo){
          String name = intp.getName() + "." + info.getName();
          completion.add(new InterpreterCompletion(name, name));
        }
      } else {
        completion.add(new InterpreterCompletion(intp.getName(), intp.getName()));
      }
    }
    return completion;
  }

  public List<InterpreterCompletion> completion(String buffer, int cursor) {
    String lines[] = buffer.split(System.getProperty("line.separator"));
    if (lines.length > 0
      && lines[0].startsWith("%")
      && cursor <= lines[0].trim().length()) {

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

    List completion = repl.completion(body, cursor);
    return completion;
  }

  public void setInterpreterFactory(InterpreterFactory factory) {
    this.factory = factory;
  }

  public InterpreterResult getResult() {
    return (InterpreterResult) getReturn();
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

  @Override
  protected Object jobRun() throws Throwable {
    String replName = getRequiredReplName();
    Interpreter repl = getRepl(replName);
    logger.info("run paragraph {} using {} " + repl, getId(), replName);
    if (repl == null) {
      logger.error("Can not find interpreter name " + repl);
      throw new RuntimeException("Can not find interpreter for " + getRequiredReplName());
    }

    String script = getScriptBody();
    // inject form
    if (repl.getFormType() == FormType.NATIVE) {
      settings.clear();
    } else if (repl.getFormType() == FormType.SIMPLE) {
      String scriptBody = getScriptBody();
      Map<String, Input> inputs = Input.extractSimpleQueryParam(scriptBody); // inputs will be built
                                                                             // from script body

      final AngularObjectRegistry angularRegistry = repl.getInterpreterGroup()
              .getAngularObjectRegistry();

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

      String message = "";

      context.out.flush();
      InterpreterResult.Type outputType = context.out.getType();
      byte[] interpreterOutput = context.out.toByteArray();

      if (interpreterOutput != null && interpreterOutput.length > 0) {
        message = new String(interpreterOutput);
      }

      if (message.isEmpty()) {
        return ret;
      } else {
        String interpreterResultMessage = ret.message();
        if (interpreterResultMessage != null && !interpreterResultMessage.isEmpty()) {
          message += interpreterResultMessage;
          return new InterpreterResult(ret.code(), ret.type(), message);
        } else {
          return new InterpreterResult(ret.code(), outputType, message);
        }
      }
    } finally {
      InterpreterContext.remove();
      effectiveText = null;
    }
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
      repl.cancel(getInterpreterContext(null));
    }
    return true;
  }

  private InterpreterContext getInterpreterContext() {
    final Paragraph self = this;

    return getInterpreterContext(new InterpreterOutput(new InterpreterOutputListener() {
      @Override
      public void onAppend(InterpreterOutput out, byte[] line) {
        updateParagraphResult(out);
        ((ParagraphJobListener) getListener()).onOutputAppend(self, out, new String(line));
      }

      @Override
      public void onUpdate(InterpreterOutput out, byte[] output) {
        updateParagraphResult(out);
        ((ParagraphJobListener) getListener()).onOutputUpdate(self, out,
            new String(output));
      }

      private void updateParagraphResult(InterpreterOutput out) {
        // update paragraph result
        Throwable t = null;
        String message = null;
        try {
          message = new String(out.toByteArray());
        } catch (IOException e) {
          logger.error(e.getMessage(), e);
          t = e;
        }
        setReturn(new InterpreterResult(Code.SUCCESS, out.getType(), message), t);
      }
    }));
  }

  private InterpreterContext getInterpreterContext(InterpreterOutput output) {
    AngularObjectRegistry registry = null;
    ResourcePool resourcePool = null;

    if (!factory.getInterpreterSettings(note.getId()).isEmpty()) {
      InterpreterSetting intpGroup = factory.getInterpreterSettings(note.getId()).get(0);
      registry = intpGroup.getInterpreterGroup(note.id()).getAngularObjectRegistry();
      resourcePool = intpGroup.getInterpreterGroup(note.id()).getResourcePool();
    }

    List<InterpreterContextRunner> runners = new LinkedList<InterpreterContextRunner>();
    for (Paragraph p : note.getParagraphs()) {
      runners.add(new ParagraphRunner(note, note.id(), p.getId()));
    }

    final Paragraph self = this;

    Credentials credentials = note.getCredentials();
    if (authenticationInfo != null) {
      UserCredentials userCredentials = credentials.getUserCredentials(
              authenticationInfo.getUser());
      authenticationInfo.setUserCredentials(userCredentials);
    }

    InterpreterContext interpreterContext = new InterpreterContext(
            note.id(),
            getId(),
            this.getTitle(),
            this.getText(),
            this.getAuthenticationInfo(),
            this.getConfig(),
            this.settings,
            registry,
            resourcePool,
            runners,
            output);
    return interpreterContext;
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
      return new LinkedList<ApplicationState>(apps);
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
}
