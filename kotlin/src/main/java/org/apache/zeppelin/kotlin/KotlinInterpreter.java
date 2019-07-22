package org.apache.zeppelin.kotlin;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.PrintStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream;
import org.apache.zeppelin.scheduler.Job;

public class KotlinInterpreter extends Interpreter {

  private static Logger logger = LoggerFactory.getLogger(KotlinInterpreter.class);

  private InterpreterOutputStream out;
  private KotlinRepl interpreter;

  private KotlinReplBuilder builder;

  public KotlinInterpreter(Properties properties) {
    super(properties);
    logger.debug("Creating KotlinInterpreter");
    builder = new KotlinReplBuilder();
  }

  public void setExecutionContext(ExecutionContext ctx) {
    logger.debug("Setting ctx in KotlinIntp to " + ctx);
    builder.executionContext(ctx);
  }

  public void setCompilerOptions(List<String> options) {
    logger.debug("Setting options in KotlinIntp to " + options);
    builder.compilerOptions(options);
  }

  @Override
  public void open() throws InterpreterException {

    interpreter = builder.build();

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

    return interpreter.eval(st);
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
