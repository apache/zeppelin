package org.apache.zeppelin.interpreter;

import java.util.Properties;

/**
 *
 */
public class DummyInterpreter extends Interpreter {

  public DummyInterpreter(Properties property) {
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
    return null;
  }

  @Override
  public void cancel(InterpreterContext context) {

  }

  @Override
  public FormType getFormType() {
    return null;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }
}
