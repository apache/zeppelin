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

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.Interpreter.FormType;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.JobListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * Paragraph is a representation of an execution unit.
 *
 */
public class Paragraph extends Job implements Serializable, Cloneable {
  private static final long serialVersionUID = -6328572073497992016L;

  private transient NoteInterpreterLoader replLoader;
  private transient Note note;

  String title;
  String text;
  Date dateUpdated;
  private Map<String, Object> config; // paragraph configs like isOpen, colWidth, etc
  public final GUI settings;          // form and parameter settings

  public Paragraph(Note note, JobListener listener, NoteInterpreterLoader replLoader) {
    super(generateId(), listener);
    this.note = note;
    this.replLoader = replLoader;
    title = null;
    text = null;
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

  public String getRequiredReplName() {
    return getRequiredReplName(text);
  }

  public static String getRequiredReplName(String text) {
    if (text == null) {
      return null;
    }

    // get script head
    int scriptHeadIndex = 0;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch == ' ' || ch == '\n' || ch == '(') {
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

  private String getScriptBody() {
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
    if (magic.length() + 1 >= text.length()) {
      return "";
    }
    return text.substring(magic.length() + 1).trim();
  }

  public NoteInterpreterLoader getNoteReplLoader() {
    return replLoader;
  }

  public Interpreter getRepl(String name) {
    return replLoader.get(name);
  }

  public List<String> completion(String buffer, int cursor) {
    String replName = getRequiredReplName(buffer);
    if (replName != null) {
      cursor -= replName.length() + 1;
    }
    String body = getScriptBody(buffer);
    Interpreter repl = getRepl(replName);
    if (repl == null) {
      return null;
    }

    return repl.completion(body, cursor);
  }

  public void setNoteReplLoader(NoteInterpreterLoader repls) {
    this.replLoader = repls;
  }

  public InterpreterResult getResult() {
    return (InterpreterResult) getReturn();
  }

  @Override
  public int progress() {
    String replName = getRequiredReplName();
    Interpreter repl = getRepl(replName);
    if (repl != null) {
      return repl.getProgress(getInterpreterContext());
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
    logger().info("run paragraph {} using {} " + repl, getId(), replName);
    if (repl == null) {
      logger().error("Can not find interpreter name " + repl);
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
      settings.setForms(inputs);
      script = Input.getSimpleQuery(settings.getParams(), scriptBody);
    }
    logger().debug("RUN : " + script);
    try {
      InterpreterContext context = getInterpreterContext();
      InterpreterContext.set(context);
      InterpreterResult ret = repl.interpret(script, context);

      if (Code.KEEP_PREVIOUS_RESULT == ret.code()) {
        return getReturn();
      }
      return ret;
    } finally {
      InterpreterContext.remove();
    }
  }

  @Override
  protected boolean jobAbort() {
    Interpreter repl = getRepl(getRequiredReplName());
    Job job = repl.getScheduler().removeFromWaitingQueue(getId());
    if (job != null) {
      job.setStatus(Status.ABORT);
    } else {
      repl.cancel(getInterpreterContext());
    }
    return true;
  }

  private InterpreterContext getInterpreterContext() {
    AngularObjectRegistry registry = null;

    if (!getNoteReplLoader().getInterpreterSettings().isEmpty()) {
      InterpreterSetting intpGroup = getNoteReplLoader().getInterpreterSettings().get(0);
      registry = intpGroup.getInterpreterGroup().getAngularObjectRegistry();
    }

    List<InterpreterContextRunner> runners = new LinkedList<InterpreterContextRunner>();
    for (Paragraph p : note.getParagraphs()) {
      runners.add(new ParagraphRunner(note, note.id(), p.getId()));
    }

    InterpreterContext interpreterContext = new InterpreterContext(
            note.id(),
            getId(),
            this.getTitle(),
            this.getText(),
            this.getConfig(),
            this.settings,
            registry,
            runners);
    return interpreterContext;
  }

  static class ParagraphRunner extends InterpreterContextRunner {
    private Note note;

    public ParagraphRunner(Note note, String noteId, String paragraphId) {
      super(noteId, paragraphId);
      this.note = note;
    }

    @Override
    public void run() {
      note.run(getParagraphId());
    }
  }


  private Logger logger() {
    Logger logger = LoggerFactory.getLogger(Paragraph.class);
    return logger;
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
}
