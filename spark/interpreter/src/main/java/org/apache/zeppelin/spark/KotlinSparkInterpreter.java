package org.apache.zeppelin.spark;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.kotlin.KotlinInterpreter;

import java.util.Properties;

public class KotlinSparkInterpreter extends Interpreter {
  public KotlinSparkInterpreter(Properties properties) {
    super(properties);
  }

  private KotlinInterpreter interpreter;

  @Override
  public void open() throws InterpreterException {
    interpreter.open();
  }

  @Override
  public void close() throws InterpreterException {
    interpreter.close();
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) throws InterpreterException {
    return interpreter.interpret(st, context);
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    interpreter.cancel(context);
  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return interpreter.getFormType();
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return interpreter.getProgress(context);
  }
}
