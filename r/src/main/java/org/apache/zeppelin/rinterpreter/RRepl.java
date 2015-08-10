package org.apache.zeppelin.rinterpreter;

import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.scheduler.Scheduler;

import java.net.URL;
import java.util.List;
import java.util.Properties;

/**
* Created by aelberg on 7/28/15.
*/
public class RRepl extends Interpreter implements WrappedInterpreter {
  RReplInterpreter intp;
  static {
    Interpreter.register("r", "r", RRepl.class.getName(), RInterpreter.getProps());
  }

  public RRepl(Properties property) {
    super(property);
    intp = new RReplInterpreter(property);
  }
  public RRepl() {
    this(new Properties());
  }

  @Override
  public void open() {
    intp.open();
  }

  @Override
  public void close() {
    intp.close();
  }

  @Override
  public InterpreterResult interpret(String s, InterpreterContext interpreterContext) {
    return intp.interpret(s, interpreterContext);
  }

  @Override
  public void cancel(InterpreterContext interpreterContext) {
    intp.cancel(interpreterContext);
  }

  @Override
  public FormType getFormType() {
    return intp.getFormType();
  }

  @Override
  public int getProgress(InterpreterContext interpreterContext) {
    return intp.getProgress(interpreterContext);
  }

  @Override
  public List<String> completion(String s, int i) {
    return intp.completion(s, i);
  }

  @Override
  public Interpreter getInnerInterpreter() {
    return intp;
  }

  @Override
  public Scheduler getScheduler() {
    return intp.getScheduler();
  }

  @Override
  public void setProperty(Properties property) {
    super.setProperty(property);
    intp.setProperty(property);
  }

  @Override
  public Properties getProperty() {
    return intp.getProperty();
  }

  @Override
  public String getProperty(String key) {
    return intp.getProperty(key);
  }

  @Override
  public void setInterpreterGroup(InterpreterGroup interpreterGroup) {
    super.setInterpreterGroup(interpreterGroup);
    intp.setInterpreterGroup(interpreterGroup);
  }

  @Override
  public InterpreterGroup getInterpreterGroup() {
    return intp.getInterpreterGroup();
  }

  @Override
  public void setClassloaderUrls(URL[] classloaderUrls) {
    intp.setClassloaderUrls(classloaderUrls);
  }

  @Override
  public URL[] getClassloaderUrls() {
    return intp.getClassloaderUrls();
  }
}
