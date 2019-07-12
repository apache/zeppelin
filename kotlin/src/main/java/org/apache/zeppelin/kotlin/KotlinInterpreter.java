package org.apache.zeppelin.kotlin;


import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream;
import org.apache.zeppelin.kotlin.conf.repl.ZeppelinReplConfiguration;
import org.apache.zeppelin.scheduler.Job;
import org.jetbrains.kotlin.cli.common.repl.ReplEvalResult;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.scripting.repl.ReplInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;

import static org.apache.zeppelin.kotlin.conf.compiler.KotlinCompilerLoader.loadCompilerConfiguration;

public class KotlinInterpreter extends Interpreter {

  private static Logger logger = LoggerFactory.getLogger(KotlinInterpreter.class);

  private ReplInterpreter interpreter;
  private ZeppelinReplConfiguration replConf;
  private InterpreterOutputStream out;

  public KotlinInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public void open() throws InterpreterException {
    CompilerConfiguration compilerConf = loadCompilerConfiguration();
    replConf = new ZeppelinReplConfiguration();
    interpreter = new ReplInterpreter(
        Disposer.newDisposable(),
        compilerConf,
        replConf);

    out = new InterpreterOutputStream(logger);
    System.setOut(new PrintStream(out));
  }

  @Override
  public void close() {

  }

  @Override
  public InterpreterResult interpret(String st,
                                     InterpreterContext context) throws InterpreterException{

    // saving job's running thread for cancelling
    Job<?> runningJob = getRunningJob(context.getParagraphId());
    if (runningJob != null) {
      runningJob.info().put("CURRENT_THREAD", Thread.currentThread());
    }

    out.setInterpreterOutput(context.out);

    ReplEvalResult result = interpreter.eval(st);

    if (result instanceof ReplEvalResult.ValueResult) {
      Object value = ((ReplEvalResult.ValueResult) result).getValue();
      String valueString;
      if (value != null) {
        valueString = value.toString();
      } else {
        valueString = "null";
      }
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, valueString);
    }

    if (result instanceof ReplEvalResult.UnitResult) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
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
  public void cancel(InterpreterContext context) throws InterpreterException {
    Job<?> runningJob = getRunningJob(context.getParagraphId());
    if (runningJob != null) {
      Map<String, Object> info = runningJob.info();
      Object object = info.get("CURRENT_THREAD");
      if (object instanceof Thread) {
        try {
          Thread t = (Thread) object;
          t.interrupt();
        } catch (Throwable t) {
          logger.error("Failed to cancel script: " + t, t);
        }
      }
    }
  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }

  private void bind(String className, String module, String alias) {
    interpreter.eval("import " + className + "." + module + " as " + alias);
  }

  private Job<?> getRunningJob(String paragraphId) {
    Job foundJob = null;
    Collection<Job> jobsRunning = getScheduler().getAllJobs();
    for (Job job : jobsRunning) {
      if (job.getId().equals(paragraphId)) {
        foundJob = job;
      }
    }
    return foundJob;
  }

}
