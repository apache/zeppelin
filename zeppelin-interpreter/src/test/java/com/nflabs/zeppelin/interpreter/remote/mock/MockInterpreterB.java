package com.nflabs.zeppelin.interpreter.remote.mock;

import java.util.List;
import java.util.Properties;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterPropertyBuilder;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.InterpreterResult.Code;

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

  }

  @Override
  public void close() {
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    return new InterpreterResult(Code.SUCCESS, st);
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

}
