package com.nflabs.zeppelin.interpreter;

import java.net.URL;
import java.util.List;
import java.util.Properties;

import com.nflabs.zeppelin.scheduler.Scheduler;

/**
 * Interpreter wrapper for lazy initialization
 */
public class LazyOpenInterpreter
    extends Interpreter
    implements WrappedInterpreter {

  private Interpreter intp;
  boolean opened = false;

  public LazyOpenInterpreter(Interpreter intp) {
    super(new Properties());
    this.intp = intp;
  }

  @Override
  public Interpreter getInnerInterpreter() {
    return intp;
  }

  @Override
  public void setProperty(Properties property) {
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
  public void open() {
    if (opened == true) {
      return;
    }

    synchronized (this) {
      if (opened == false) {
        intp.open();
        opened = true;
      }
    }
  }

  @Override
  public void close() {
    synchronized (this) {
      if (opened == true) {
        intp.close();
        opened = false;
      }
    }
  }

  @Override
  public Object getValue(String name) {
    open();
    return intp.getValue(name);
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    open();
    return intp.interpret(st, context);
  }

  @Override
  public void cancel(InterpreterContext context) {
    open();
    intp.cancel(context);
  }

  @Override
  public void bindValue(String name, Object o) {
    open();
    intp.bindValue(name, o);
  }

  @Override
  public FormType getFormType() {
    return intp.getFormType();
  }

  @Override
  public int getProgress(InterpreterContext context) {
    open();
    return intp.getProgress(context);
  }

  @Override
  public Scheduler getScheduler() {
    return intp.getScheduler();
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    open();
    return intp.completion(buf, cursor);
  }

  @Override
  public String getClassName() {
    return intp.getClassName();
  }

  @Override
  public InterpreterGroup getInterpreterGroup() {
    return intp.getInterpreterGroup();
  }

  @Override
  public void setInterpreterGroup(InterpreterGroup interpreterGroup) {
    intp.setInterpreterGroup(interpreterGroup);
  }

  @Override
  public URL [] getClassloaderUrls() {
    return intp.getClassloaderUrls();
  }

  @Override
  public void setClassloaderUrls(URL [] urls) {
    intp.setClassloaderUrls(urls);
  }
}
