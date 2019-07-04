package org.apache.zeppelin.kotlin;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.kotlin.repl.KotlinPluginLoader;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.scripting.repl.ReplInterpreter;

import java.util.Properties;

public class KotlinInterpreter extends Interpreter {
  public KotlinInterpreter(Properties properties) {
    super(properties);
  }

  private KotlinPluginLoader loader;
  private ReplInterpreter interpreter;

  @Override
  public void open() throws InterpreterException {
    loader = new KotlinPluginLoader();
    CompilerConfiguration configuration = loader.loadCompilerConfiguration();

  }

  @Override
  public void close() throws InterpreterException {

  }

  @Override
  public InterpreterResult interpret(String st,
                                     InterpreterContext context) throws InterpreterException {
    String result = loader.getJarPathByName(st);
    return new InterpreterResult(InterpreterResult.Code.SUCCESS, result);
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {

  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }
}
