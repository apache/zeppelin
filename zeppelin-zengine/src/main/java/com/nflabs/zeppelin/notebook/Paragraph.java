package com.nflabs.zeppelin.notebook;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.display.GUI;
import com.nflabs.zeppelin.display.Input;
import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.Interpreter.FormType;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.JobListener;

/**
 * Paragraph is a representation of an execution unit.
 *
 * @author Leemoonsoo
 */
public class Paragraph extends Job implements Serializable {
  private static final transient long serialVersionUID = -6328572073497992016L;
  private transient NoteInterpreterLoader replLoader;

  String title;
  String text;
  private Map<String, Object> config; // paragraph configs like isOpen, colWidth, etc
  public final GUI settings;          // form and parameter settings

  public Paragraph(JobListener listener, NoteInterpreterLoader replLoader) {
    super(generateId(), listener);
    this.replLoader = replLoader;
    title = null;
    text = null;
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
  }


  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
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
      if (ch == ' ' || ch == '\n') {
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
    if (magic.length() + 2 >= text.length()) {
      return "";
    }
    return text.substring(magic.length() + 2);
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
    logger().info("RUN : " + script);
    InterpreterResult ret = repl.interpret(script, getInterpreterContext());
    return ret;
  }

  @Override
  protected boolean jobAbort() {
    Interpreter repl = getRepl(getRequiredReplName());
    repl.cancel(getInterpreterContext());
    return true;
  }

  private InterpreterContext getInterpreterContext() {
    InterpreterContext interpreterContext = new InterpreterContext(getId(),
            this.getTitle(),
            this.getText(),
            this.getConfig(),
            this.settings);
    return interpreterContext;
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
}
