package com.nflabs.zeppelin.interpreter.remote;


import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.thrift.TException;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nflabs.zeppelin.display.GUI;
import com.nflabs.zeppelin.interpreter.ClassloaderInterpreter;
import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.Interpreter.FormType;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterException;
import com.nflabs.zeppelin.interpreter.InterpreterGroup;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.LazyOpenInterpreter;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterContext;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterResult;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterService;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.Job.Status;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.scheduler.JobProgressPoller;
import com.nflabs.zeppelin.scheduler.Scheduler;


/**
 *
 */
public class RemoteInterpreterServer
  extends Thread
  implements RemoteInterpreterService.Iface {
  Logger logger = LoggerFactory.getLogger(RemoteInterpreterServer.class);

  InterpreterGroup interpreterGroup = new InterpreterGroup();
  Gson gson = new Gson();

  RemoteInterpreterService.Processor<RemoteInterpreterServer> processor;
  RemoteInterpreterServer handler;
  private int port;
  private TThreadPoolServer server;

  public RemoteInterpreterServer(int port) throws TTransportException {
    this.port = port;
    processor = new RemoteInterpreterService.Processor<RemoteInterpreterServer>(this);
    TServerSocket serverTransport = new TServerSocket(port);
    server = new TThreadPoolServer(
        new TThreadPoolServer.Args(serverTransport).processor(processor));
  }

  @Override
  public void run() {
    logger.info("Starting remote interpreter server on port {}", port);
    server.serve();
  }

  @Override
  public void shutdown() throws TException {
    // server.stop() does not always finish server.serve() loop
    // sometimes server.serve() is hanging even after server.stop() call.
    // this case, need to force kill the process
    server.stop();
  }

  public int getPort() {
    return port;
  }

  public boolean isRunning() {
    if (server == null) {
      return false;
    } else {
      return server.isServing();
    }
  }


  public static void main(String[] args)
      throws TTransportException, InterruptedException {
    int port = Integer.parseInt(args[0]);
    RemoteInterpreterServer remoteInterpreterServer = new RemoteInterpreterServer(port);
    remoteInterpreterServer.start();
    remoteInterpreterServer.join();
    System.exit(0);
  }


  @Override
  public void createInterpreter(String className, Map<String, String> properties)
      throws TException {
    try {
      Class<Interpreter> replClass = (Class<Interpreter>) Object.class.forName(className);
      Properties p = new Properties();
      p.putAll(properties);

      Constructor<Interpreter> constructor =
          replClass.getConstructor(new Class[] {Properties.class});
      Interpreter repl = constructor.newInstance(p);

      ClassLoader cl = ClassLoader.getSystemClassLoader();
      repl.setClassloaderUrls(new URL[]{});

      synchronized (interpreterGroup) {
        interpreterGroup.add(new LazyOpenInterpreter((new ClassloaderInterpreter(repl, cl))));
      }

      logger.info("Instantiate interpreter {}", className);
      repl.setInterpreterGroup(interpreterGroup);
    } catch (ClassNotFoundException | NoSuchMethodException | SecurityException
        | InstantiationException | IllegalAccessException
        | IllegalArgumentException | InvocationTargetException e) {
      e.printStackTrace();
      throw new TException(e);
    }
  }

  private Interpreter getInterpreter(String className) throws TException {
    synchronized (interpreterGroup) {
      for (Interpreter inp : interpreterGroup) {
        if (inp.getClassName().equals(className)) {
          return inp;
        }
      }
    }
    throw new TException(new InterpreterException("Interpreter instance "
        + className + " not found"));
  }

  @Override
  public void open(String className) throws TException {
    Interpreter intp = getInterpreter(className);
    intp.open();
  }

  @Override
  public void close(String className) throws TException {
    Interpreter intp = getInterpreter(className);
    intp.close();
  }


  @Override
  public RemoteInterpreterResult interpret(String className, String st,
      RemoteInterpreterContext interpreterContext) throws TException {
    Interpreter intp = getInterpreter(className);
    InterpreterContext context = convert(interpreterContext);

    Scheduler scheduler = intp.getScheduler();
    InterpretJobListener jobListener = new InterpretJobListener();
    InterpretJob job = new InterpretJob(
        interpreterContext.getParagraphId(),
        "remoteInterpretJob_" + System.currentTimeMillis(),
        jobListener,
        JobProgressPoller.DEFAULT_INTERVAL_MSEC,
        intp,
        st,
        context);

    scheduler.submit(job);

    while (!job.isTerminated()) {
      synchronized (jobListener) {
        try {
          jobListener.wait(1000);
        } catch (InterruptedException e) {
        }
      }
    }

    if (job.getStatus() == Status.ERROR) {
      throw new TException(job.getException());
    } else {
      if (intp.getFormType() == FormType.NATIVE) {
        // serialize dynamic form

      }

      return convert((InterpreterResult) job.getReturn(),
          context.getConfig(),
          context.getGui());
    }
  }

  class InterpretJobListener implements JobListener {

    @Override
    public void onProgressUpdate(Job job, int progress) {
    }

    @Override
    public void beforeStatusChange(Job job, Status before, Status after) {
    }

    @Override
    public void afterStatusChange(Job job, Status before, Status after) {
      synchronized (this) {
        notifyAll();
      }
    }
  }

  class InterpretJob extends Job {

    private Interpreter interpreter;
    private String script;
    private InterpreterContext context;

    public InterpretJob(
        String jobId,
        String jobName,
        JobListener listener,
        long progressUpdateIntervalMsec,
        Interpreter interpreter,
        String script,
        InterpreterContext context) {
      super(jobId, jobName, listener, progressUpdateIntervalMsec);
      this.interpreter = interpreter;
      this.script = script;
      this.context = context;
    }

    @Override
    public int progress() {
      return 0;
    }

    @Override
    public Map<String, Object> info() {
      return null;
    }

    @Override
    protected Object jobRun() throws Throwable {
      InterpreterResult result = interpreter.interpret(script, context);
      return result;
    }

    @Override
    protected boolean jobAbort() {
      return false;
    }
  }


  @Override
  public void cancel(String className, RemoteInterpreterContext interpreterContext)
      throws TException {
    Interpreter intp = getInterpreter(className);
    intp.cancel(convert(interpreterContext));
  }

  @Override
  public int getProgress(String className, RemoteInterpreterContext interpreterContext)
      throws TException {
    Interpreter intp = getInterpreter(className);
    return intp.getProgress(convert(interpreterContext));
  }


  @Override
  public String getFormType(String className) throws TException {
    Interpreter intp = getInterpreter(className);
    return intp.getFormType().toString();
  }

  @Override
  public List<String> completion(String className, String buf, int cursor) throws TException {
    Interpreter intp = getInterpreter(className);
    return intp.completion(buf, cursor);
  }

  private InterpreterContext convert(RemoteInterpreterContext ric) {
    return new InterpreterContext(
        ric.getParagraphId(),
        ric.getParagraphTitle(),
        ric.getParagraphText(),
        (Map<String, Object>) gson.fromJson(ric.getConfig(),
            new TypeToken<Map<String, Object>>() {}.getType()),
        gson.fromJson(ric.getGui(), GUI.class));
  }

  private RemoteInterpreterResult convert(InterpreterResult result,
      Map<String, Object> config, GUI gui) {
    return new RemoteInterpreterResult(
        result.code().name(),
        result.type().name(),
        result.message(),
        gson.toJson(config),
        gson.toJson(gui));
  }

  @Override
  public String getStatus(String jobId)
      throws TException {
    synchronized (interpreterGroup) {
      for (Interpreter intp : interpreterGroup) {
        for (Job job : intp.getScheduler().getJobsRunning()) {
          if (jobId.equals(job.getId())) {
            return job.getStatus().name();
          }
        }

        for (Job job : intp.getScheduler().getJobsWaiting()) {
          if (jobId.equals(job.getId())) {
            return job.getStatus().name();
          }
        }
      }
    }
    return "Unknown";
  }
}
