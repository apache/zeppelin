package org.apache.zeppelin.kotlin;


import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.kotlin.repl.KotlinPluginLoader;
import org.apache.zeppelin.kotlin.repl.ZeppelinReplConfiguration;
import org.jetbrains.kotlin.cli.common.repl.ReplEvalResult;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.scripting.repl.ReplInterpreter;
import org.jetbrains.kotlin.scripting.repl.configuration.ReplConfiguration;

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
    CompilerConfiguration compilerConf = loader.loadCompilerConfiguration();
    ReplConfiguration replConf = new ZeppelinReplConfiguration();
    interpreter = new ReplInterpreter(
        () -> {},
        compilerConf,
        replConf);
  }

  @Override
  public void close() throws InterpreterException {

  }

  @Override
  public InterpreterResult interpret(String st,
                                     InterpreterContext context) throws InterpreterException {
    ReplEvalResult result = interpreter.eval(st);
    if (result instanceof ReplEvalResult.ValueResult) {
      String value = ((ReplEvalResult.ValueResult) result).getValue().toString();
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, value);
    }
    if (result instanceof ReplEvalResult.UnitResult) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, "");
    }
    if (result instanceof ReplEvalResult.Error) {
      String errorMsg = ((ReplEvalResult.Error) result).getMessage();
      return new InterpreterResult(InterpreterResult.Code.ERROR, errorMsg);
    }
    if (result instanceof ReplEvalResult.Incomplete) {
      return new InterpreterResult(InterpreterResult.Code.INCOMPLETE);
    }

    return new InterpreterResult(InterpreterResult.Code.ERROR, "Unknown error");
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException { }

  @Override
  public FormType getFormType() throws InterpreterException {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }
}
