package com.nflabs.zeppelin.markdown;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.markdown4j.Markdown4jProcessor;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.InterpreterResult.Code;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;

/**
 * Markdown interpreter for Zeppelin.
 *
 * @author Leemoonsoo
 * @author anthonycorbacho
 *
 */
public class Markdown extends Interpreter {
  private Markdown4jProcessor md;

  static {
    Interpreter.register("md", Markdown.class.getName());
  }

  public Markdown(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    md = new Markdown4jProcessor();
  }

  @Override
  public void close() {}

  @Override
  public Object getValue(String name) {
    return null;
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext interpreterContext) {
    String html;
    try {
      html = md.process(st);
    } catch (IOException e) {
      return new InterpreterResult(Code.ERROR, e.getMessage());
    }
    return new InterpreterResult(Code.SUCCESS, "%html " + html);
  }

  @Override
  public void cancel(InterpreterContext context) {}

  @Override
  public void bindValue(String name, Object o) {}

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetParallelScheduler(
        Markdown.class.getName() + this.hashCode(), 5);
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }
}
