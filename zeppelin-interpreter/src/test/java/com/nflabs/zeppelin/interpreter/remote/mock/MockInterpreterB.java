package com.nflabs.zeppelin.interpreter.remote.mock;

import java.util.List;
import java.util.Properties;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterException;
import com.nflabs.zeppelin.interpreter.InterpreterGroup;
import com.nflabs.zeppelin.interpreter.InterpreterPropertyBuilder;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.InterpreterResult.Code;
import com.nflabs.zeppelin.interpreter.WrappedInterpreter;
import com.nflabs.zeppelin.scheduler.Scheduler;

public class MockInterpreterB extends Interpreter {
  static {
    Interpreter.register(
        "interpreterB",
        "group1",
        MockInterpreterA.class.getName(),
        new InterpreterPropertyBuilder()
            .add("p1", "v1", "property1").build());

  }
  public MockInterpreterB(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    //new RuntimeException().printStackTrace();
  }

  @Override
  public void close() {
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    MockInterpreterA intpA = getInterpreterA();
    String intpASt = intpA.getLastStatement();
    long timeToSleep = Long.parseLong(st);
    if (intpASt != null) {
      timeToSleep += Long.parseLong(intpASt);
    }
    try {
      Thread.sleep(timeToSleep);
    } catch (NumberFormatException | InterruptedException e) {
      throw new InterpreterException(e);
    }
    return new InterpreterResult(Code.SUCCESS, Long.toString(timeToSleep));
  }

  @Override
  public void cancel(InterpreterContext context) {

  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }

  public MockInterpreterA getInterpreterA() {
    InterpreterGroup interpreterGroup = getInterpreterGroup();
    for (Interpreter intp : interpreterGroup) {
      if (intp.getClassName().equals(MockInterpreterA.class.getName())) {
        Interpreter p = intp;
        while (p instanceof WrappedInterpreter) {
          p = ((WrappedInterpreter) p).getInnerInterpreter();
        }
        return (MockInterpreterA) p;
      }
    }
    return null;
  }

  @Override
  public Scheduler getScheduler() {
    InterpreterGroup interpreterGroup = getInterpreterGroup();
    for (Interpreter intp : interpreterGroup) {
      if (intp.getClassName().equals(MockInterpreterA.class.getName())) {
        return intp.getScheduler();
      }
    }

    return null;
  }

}
