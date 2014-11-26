package com.nflabs.zeppelin.interpreter;

import java.util.List;
import java.util.Properties;

import com.nflabs.zeppelin.scheduler.Scheduler;

/**
 * Add to the classpath interperters.
 * 
 * @author Leemoonsoo
 *
 */
public class ClassloaderInterpreter extends Interpreter {

  private ClassLoader cl;
  private Interpreter intp;

  public ClassloaderInterpreter(Interpreter intp, ClassLoader cl, Properties property) {
    super(property);
    this.cl = cl;
    this.intp = intp;
  }

  public Interpreter getInnerRepl() {
    return intp;
  }

  public ClassLoader getClassloader() {
    return cl;
  }

  @Override
  public Object getValue(String name) {
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      return intp.getValue(name);
    } catch (Exception e) {
      throw new InterpreterException(e);
    } finally {
      cl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  @Override
  public InterpreterResult interpret(String st) {
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      return intp.interpret(st);
    } catch (Exception e) {
      e.printStackTrace();
      throw new InterpreterException(e);
    } finally {
      cl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  @Override
  public void bindValue(String name, Object o) {
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      intp.bindValue(name, o);
    } catch (Exception e) {
      throw new InterpreterException(e);
    } finally {
      cl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  @Override
  public void open() {
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      intp.open();
    } catch (Exception e) {
      throw new InterpreterException(e);
    } finally {
      cl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  @Override
  public void close() {
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      intp.close();
    } catch (Exception e) {
      throw new InterpreterException(e);
    } finally {
      cl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  @Override
  public void cancel() {
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      intp.cancel();
    } catch (Exception e) {
      throw new InterpreterException(e);
    } finally {
      cl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  @Override
  public FormType getFormType() {
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      return intp.getFormType();
    } catch (Exception e) {
      throw new InterpreterException(e);
    } finally {
      cl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  @Override
  public int getProgress() {
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      return intp.getProgress();
    } catch (Exception e) {
      throw new InterpreterException(e);
    } finally {
      cl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  @Override
  public Scheduler getScheduler() {
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      return intp.getScheduler();
    } catch (Exception e) {
      throw new InterpreterException(e);
    } finally {
      cl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      return intp.completion(buf, cursor);
    } catch (Exception e) {
      throw new InterpreterException(e);
    } finally {
      cl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }
}
