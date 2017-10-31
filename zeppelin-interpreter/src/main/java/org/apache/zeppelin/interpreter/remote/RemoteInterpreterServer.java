/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter.remote;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.thrift.TException;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportException;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.helium.Application;
import org.apache.zeppelin.helium.ApplicationContext;
import org.apache.zeppelin.helium.ApplicationException;
import org.apache.zeppelin.helium.ApplicationLoader;
import org.apache.zeppelin.helium.HeliumAppAngularObjectRegistry;
import org.apache.zeppelin.helium.HeliumPackage;
import org.apache.zeppelin.interpreter.Constants;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterHookListener;
import org.apache.zeppelin.interpreter.InterpreterHookRegistry;
import org.apache.zeppelin.interpreter.InterpreterHookRegistry.HookType;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.RemoteWorksController;
import org.apache.zeppelin.interpreter.RemoteZeppelinServerResource;
import org.apache.zeppelin.interpreter.thrift.CallbackInfo;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.thrift.RemoteApplicationResult;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterContext;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterEvent;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResult;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResultMessage;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService;
import org.apache.zeppelin.interpreter.thrift.ZeppelinServerResourceParagraphRunner;
import org.apache.zeppelin.resource.DistributedResourcePool;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.resource.ResourceSet;
import org.apache.zeppelin.resource.WellKnownResourceName;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.scheduler.JobListener;
import org.apache.zeppelin.scheduler.JobProgressPoller;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Entry point for Interpreter process.
 * Accepting thrift connections from ZeppelinServer.
 */
public class RemoteInterpreterServer
  extends Thread
  implements RemoteInterpreterService.Iface, AngularObjectRegistryListener {
  Logger logger = LoggerFactory.getLogger(RemoteInterpreterServer.class);

  InterpreterGroup interpreterGroup;
  AngularObjectRegistry angularObjectRegistry;
  InterpreterHookRegistry hookRegistry;
  DistributedResourcePool resourcePool;
  private ApplicationLoader appLoader;

  Gson gson = new Gson();

  RemoteInterpreterService.Processor<RemoteInterpreterServer> processor;
  private String callbackHost;
  private int callbackPort;
  private String host;
  private int port;
  private TThreadPoolServer server;

  RemoteInterpreterEventClient eventClient = new RemoteInterpreterEventClient();
  private DependencyResolver depLoader;

  private final Map<String, RunningApplication> runningApplications =
      Collections.synchronizedMap(new HashMap<String, RunningApplication>());

  private Map<String, Object> remoteWorksResponsePool;
  private ZeppelinRemoteWorksController remoteWorksController;

  private final long DEFAULT_SHUTDOWN_TIMEOUT = 2000;

  // Hold information for manual progress update
  private ConcurrentMap<String, Integer> progressMap = new ConcurrentHashMap<>();

  private boolean isTest;

  public RemoteInterpreterServer(String callbackHost, int port) throws IOException,
      TTransportException {
    this(callbackHost, port, false);
  }

  public RemoteInterpreterServer(String callbackHost, int port, boolean isTest)
      throws TTransportException, IOException {
    if (null != callbackHost) {
      this.callbackHost = callbackHost;
      this.callbackPort = port;
    } else {
      // DevInterpreter
      this.port = port;
    }
    this.isTest = isTest;

    processor = new RemoteInterpreterService.Processor<>(this);
    TServerSocket serverTransport;
    if (null == callbackHost) {
      // Dev Interpreter
      serverTransport = new TServerSocket(port);
    } else {
      this.port = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
      this.host = RemoteInterpreterUtils.findAvailableHostAddress();
      serverTransport = new TServerSocket(this.port);
    }
    server = new TThreadPoolServer(
        new TThreadPoolServer.Args(serverTransport).processor(processor));
    remoteWorksResponsePool = Collections.synchronizedMap(new HashMap<String, Object>());
    remoteWorksController = new ZeppelinRemoteWorksController(this, remoteWorksResponsePool);
  }

  @Override
  public void run() {
    if (null != callbackHost && !isTest) {
      new Thread(new Runnable() {
        boolean interrupted = false;
        @Override
        public void run() {
          while (!interrupted && !server.isServing()) {
            try {
              Thread.sleep(1000);
            } catch (InterruptedException e) {
              interrupted = true;
            }
          }

          if (!interrupted) {
            CallbackInfo callbackInfo = new CallbackInfo(host, port);
            try {
              RemoteInterpreterUtils
                  .registerInterpreter(callbackHost, callbackPort, callbackInfo);
            } catch (TException e) {
              logger.error("Error while registering interpreter: {}", callbackInfo, e);
              try {
                shutdown();
              } catch (TException e1) {
                logger.warn("Exception occurs while shutting down", e1);
              }
            }
          }
        }
      }).start();
    }
    logger.info("Starting remote interpreter server on port {}", port);
    server.serve();
  }

  @Override
  public void shutdown() throws TException {
    logger.info("Shutting down...");
    eventClient.waitForEventQueueBecomesEmpty(DEFAULT_SHUTDOWN_TIMEOUT);
    if (interpreterGroup != null) {
      for (List<Interpreter> session : interpreterGroup.values()) {
        for (Interpreter interpreter : session) {
          try {
            interpreter.close();
          } catch (InterpreterException e) {
            logger.warn("Fail to close interpreter", e);
          }
        }
      }
    }

    server.stop();

    // server.stop() does not always finish server.serve() loop
    // sometimes server.serve() is hanging even after server.stop() call.
    // this case, need to force kill the process

    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < DEFAULT_SHUTDOWN_TIMEOUT &&
        server.isServing()) {
      try {
        Thread.sleep(300);
      } catch (InterruptedException e) {
        logger.info("Exception in RemoteInterpreterServer while shutdown, Thread.sleep", e);
      }
    }

    if (server.isServing()) {
      System.exit(0);
    }
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
      throws TTransportException, InterruptedException, IOException {
    String callbackHost = null;
    int port = Constants.ZEPPELIN_INTERPRETER_DEFAUlT_PORT;
    if (args.length > 0) {
      callbackHost = args[0];
      port = Integer.parseInt(args[1]);
    }
    RemoteInterpreterServer remoteInterpreterServer =
        new RemoteInterpreterServer(callbackHost, port);
    remoteInterpreterServer.start();
    remoteInterpreterServer.join();
    System.exit(0);
  }

  @Override
  public void createInterpreter(String interpreterGroupId, String sessionId, String
      className, Map<String, String> properties, String userName) throws TException {
    if (interpreterGroup == null) {
      interpreterGroup = new InterpreterGroup(interpreterGroupId);
      angularObjectRegistry = new AngularObjectRegistry(interpreterGroup.getId(), this);
      hookRegistry = new InterpreterHookRegistry(interpreterGroup.getId());
      resourcePool = new DistributedResourcePool(interpreterGroup.getId(), eventClient);
      interpreterGroup.setInterpreterHookRegistry(hookRegistry);
      interpreterGroup.setAngularObjectRegistry(angularObjectRegistry);
      interpreterGroup.setResourcePool(resourcePool);

      String localRepoPath = properties.get("zeppelin.interpreter.localRepo");
      if (properties.containsKey("zeppelin.interpreter.output.limit")) {
        InterpreterOutput.limit = Integer.parseInt(
            properties.get("zeppelin.interpreter.output.limit"));
      }

      depLoader = new DependencyResolver(localRepoPath);
      appLoader = new ApplicationLoader(resourcePool, depLoader);
    }

    try {
      Class<Interpreter> replClass = (Class<Interpreter>) Object.class.forName(className);
      Properties p = new Properties();
      p.putAll(properties);
      setSystemProperty(p);

      Constructor<Interpreter> constructor =
          replClass.getConstructor(new Class[] {Properties.class});
      Interpreter repl = constructor.newInstance(p);
      repl.setClassloaderUrls(new URL[]{});
      logger.info("Instantiate interpreter {}", className);
      repl.setInterpreterGroup(interpreterGroup);
      repl.setUserName(userName);

      interpreterGroup.addInterpreterToSession(new LazyOpenInterpreter(repl), sessionId);
    } catch (ClassNotFoundException | NoSuchMethodException | SecurityException
        | InstantiationException | IllegalAccessException
        | IllegalArgumentException | InvocationTargetException e) {
      logger.error(e.toString(), e);
      throw new TException(e);
    }
  }

  protected InterpreterGroup getInterpreterGroup() {
    return interpreterGroup;
  }

  protected ResourcePool getResourcePool() {
    return resourcePool;
  }

  protected RemoteInterpreterEventClient getEventClient() {
    return eventClient;
  }

  private void setSystemProperty(Properties properties) {
    for (Object key : properties.keySet()) {
      if (!RemoteInterpreterUtils.isEnvString((String) key)) {
        String value = properties.getProperty((String) key);
        if (value == null || value.isEmpty()) {
          System.clearProperty((String) key);
        } else {
          System.setProperty((String) key, properties.getProperty((String) key));
        }
      }
    }
  }

  protected Interpreter getInterpreter(String sessionId, String className) throws TException {
    if (interpreterGroup == null) {
      throw new TException(
          new InterpreterException("Interpreter instance " + className + " not created"));
    }
    synchronized (interpreterGroup) {
      List<Interpreter> interpreters = interpreterGroup.get(sessionId);
      if (interpreters == null) {
        throw new TException(
            new InterpreterException("Interpreter " + className + " not initialized"));
      }
      for (Interpreter inp : interpreters) {
        if (inp.getClassName().equals(className)) {
          return inp;
        }
      }
    }
    throw new TException(new InterpreterException("Interpreter instance "
        + className + " not found"));
  }

  @Override
  public void open(String sessionId, String className) throws TException {
    logger.info(String.format("Open Interpreter %s for session %s ", className, sessionId));
    Interpreter intp = getInterpreter(sessionId, className);
    try {
      intp.open();
    } catch (InterpreterException e) {
      throw new TException("Fail to open interpreter", e);
    }
  }

  @Override
  public void close(String sessionId, String className) throws TException {
    // unload all applications
    for (String appId : runningApplications.keySet()) {
      RunningApplication appInfo = runningApplications.get(appId);

      // see NoteInterpreterLoader.SHARED_SESSION
      if (appInfo.noteId.equals(sessionId) || sessionId.equals("shared_session")) {
        try {
          logger.info("Unload App {} ", appInfo.pkg.getName());
          appInfo.app.unload();
          // see ApplicationState.Status.UNLOADED
          eventClient.onAppStatusUpdate(appInfo.noteId, appInfo.paragraphId, appId, "UNLOADED");
        } catch (ApplicationException e) {
          logger.error(e.getMessage(), e);
        }
      }
    }

    // close interpreters
    List<Interpreter> interpreters;
    synchronized (interpreterGroup) {
      interpreters = interpreterGroup.get(sessionId);
    }
    if (interpreters != null) {
      Iterator<Interpreter> it = interpreters.iterator();
      while (it.hasNext()) {
        Interpreter inp = it.next();
        if (inp.getClassName().equals(className)) {
          try {
            inp.close();
          } catch (InterpreterException e) {
            logger.warn("Fail to close interpreter", e);
          }
          it.remove();
          break;
        }
      }
    }
  }


  @Override
  public RemoteInterpreterResult interpret(String noteId, String className, String st,
      RemoteInterpreterContext interpreterContext) throws TException {
    if (logger.isDebugEnabled()) {
      logger.debug("st:\n{}", st);
    }
    Interpreter intp = getInterpreter(noteId, className);
    InterpreterContext context = convert(interpreterContext);
    context.setClassName(intp.getClassName());

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
          logger.info("Exception in RemoteInterpreterServer while interpret, jobListener.wait", e);
        }
      }
    }

    progressMap.remove(interpreterContext.getParagraphId());

    InterpreterResult result;
    if (job.getStatus() == Status.ERROR) {
      result = new InterpreterResult(Code.ERROR, Job.getStack(job.getException()));
    } else {
      result = (InterpreterResult) job.getReturn();

      // in case of job abort in PENDING status, result can be null
      if (result == null) {
        result = new InterpreterResult(Code.KEEP_PREVIOUS_RESULT);
      }
    }
    return convert(result,
        context.getConfig(),
        context.getGui());
  }

  @Override
  public void onReceivedZeppelinResource(String responseJson) throws TException {
    RemoteZeppelinServerResource response = RemoteZeppelinServerResource.fromJson(responseJson);
    if (response == null) {
      throw new TException("Bad response for remote resource");
    }

    try {
      if (response.getResourceType() == RemoteZeppelinServerResource.Type.PARAGRAPH_RUNNERS) {
        List<InterpreterContextRunner> intpContextRunners = new LinkedList<>();
        List<Map<String, Object>> remoteRunnersMap =
            (List<Map<String, Object>>) response.getData();

        String noteId = null;
        String paragraphId = null;

        for (Map<String, Object> runnerItem : remoteRunnersMap) {
          noteId = (String) runnerItem.get("noteId");
          paragraphId = (String) runnerItem.get("paragraphId");
          intpContextRunners.add(
              new ParagraphRunner(this, noteId, paragraphId)
          );
        }

        synchronized (this.remoteWorksResponsePool) {
          this.remoteWorksResponsePool.put(
              response.getOwnerKey(),
              intpContextRunners);
        }
      }
    } catch (Exception e) {
      throw e;
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
    private Map<String, Object> infos;
    private Object results;

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
    public Object getReturn() {
      return results;
    }

    @Override
    public int progress() {
      return 0;
    }

    @Override
    public Map<String, Object> info() {
      if (infos == null) {
        infos = new HashMap<>();
      }
      return infos;
    }

    private void processInterpreterHooks(final String noteId) {
      InterpreterHookListener hookListener = new InterpreterHookListener() {
        @Override
        public void onPreExecute(String script) {
          String cmdDev = interpreter.getHook(noteId, HookType.PRE_EXEC_DEV);
          String cmdUser = interpreter.getHook(noteId, HookType.PRE_EXEC);

          // User defined hook should be executed before dev hook
          List<String> cmds = Arrays.asList(cmdDev, cmdUser);
          for (String cmd : cmds) {
            if (cmd != null) {
              script = cmd + '\n' + script;
            }
          }

          InterpretJob.this.script = script;
        }

        @Override
        public void onPostExecute(String script) {
          String cmdDev = interpreter.getHook(noteId, HookType.POST_EXEC_DEV);
          String cmdUser = interpreter.getHook(noteId, HookType.POST_EXEC);

          // User defined hook should be executed after dev hook
          List<String> cmds = Arrays.asList(cmdUser, cmdDev);
          for (String cmd : cmds) {
            if (cmd != null) {
              script += '\n' + cmd;
            }
          }

          InterpretJob.this.script = script;
        }
      };
      hookListener.onPreExecute(script);
      hookListener.onPostExecute(script);
    }

    @Override
    protected Object jobRun() throws Throwable {
      try {
        InterpreterContext.set(context);

        InterpreterResult result = null;

        // Open the interpreter instance prior to calling interpret().
        // This is necessary because the earliest we can register a hook
        // is from within the open() method.
        LazyOpenInterpreter lazy = (LazyOpenInterpreter) interpreter;
        if (!lazy.isOpen()) {
          lazy.open();
          result = lazy.executePrecode(context);
        }

        if (result == null || result.code() == Code.SUCCESS) {
          // Add hooks to script from registry.
          // Global scope first, followed by notebook scope
          processInterpreterHooks(null);
          processInterpreterHooks(context.getNoteId());
          result = interpreter.interpret(script, context);
        }

        // data from context.out is prepended to InterpreterResult if both defined
        context.out.flush();
        List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
        resultMessages.addAll(result.message());

        for (InterpreterResultMessage msg : resultMessages) {
          if (msg.getType() == InterpreterResult.Type.IMG) {
            logger.debug("InterpreterResultMessage: IMAGE_DATA");
          } else {
            logger.debug("InterpreterResultMessage: " + msg.toString());
          }
        }
        // put result into resource pool
        if (resultMessages.size() > 0) {
          int lastMessageIndex = resultMessages.size() - 1;
          if (resultMessages.get(lastMessageIndex).getType() ==
              InterpreterResult.Type.TABLE) {
            context.getResourcePool().put(
                context.getNoteId(),
                context.getParagraphId(),
                WellKnownResourceName.ZeppelinTableResult.toString(),
                resultMessages.get(lastMessageIndex));
          }
        }
        return new InterpreterResult(result.code(), resultMessages);
      } finally {
        InterpreterContext.remove();
      }
    }

    @Override
    protected boolean jobAbort() {
      return false;
    }

    @Override
    public void setResult(Object results) {
      this.results = results;
    }
  }


  @Override
  public void cancel(String noteId, String className, RemoteInterpreterContext interpreterContext)
      throws TException {
    logger.info("cancel {} {}", className, interpreterContext.getParagraphId());
    Interpreter intp = getInterpreter(noteId, className);
    String jobId = interpreterContext.getParagraphId();
    Job job = intp.getScheduler().removeFromWaitingQueue(jobId);

    if (job != null) {
      job.setStatus(Status.ABORT);
    } else {
      try {
        intp.cancel(convert(interpreterContext, null));
      } catch (InterpreterException e) {
        throw new TException("Fail to cancel", e);
      }
    }
  }

  @Override
  public int getProgress(String sessionId, String className,
                         RemoteInterpreterContext interpreterContext)
      throws TException {
    Integer manuallyProvidedProgress = progressMap.get(interpreterContext.getParagraphId());
    if (manuallyProvidedProgress != null) {
      return manuallyProvidedProgress;
    } else {
      Interpreter intp = getInterpreter(sessionId, className);
      if (intp == null) {
        throw new TException("No interpreter {} existed for session {}".format(
            className, sessionId));
      }
      try {
        return intp.getProgress(convert(interpreterContext, null));
      } catch (InterpreterException e) {
        throw new TException("Fail to getProgress", e);
      }
    }
  }


  @Override
  public String getFormType(String sessionId, String className) throws TException {
    Interpreter intp = getInterpreter(sessionId, className);
    try {
      return intp.getFormType().toString();
    } catch (InterpreterException e) {
      throw new TException(e);
    }
  }

  @Override
  public List<InterpreterCompletion> completion(String sessionId,
      String className, String buf, int cursor, RemoteInterpreterContext remoteInterpreterContext)
      throws TException {
    Interpreter intp = getInterpreter(sessionId, className);
    try {
      return intp.completion(buf, cursor, convert(remoteInterpreterContext, null));
    } catch (InterpreterException e) {
      throw new TException("Fail to get completion", e);
    }
  }

  private InterpreterContext convert(RemoteInterpreterContext ric) {
    return convert(ric, createInterpreterOutput(ric.getNoteId(), ric.getParagraphId()));
  }

  private InterpreterContext convert(RemoteInterpreterContext ric, InterpreterOutput output) {
    List<InterpreterContextRunner> contextRunners = new LinkedList<>();
    List<InterpreterContextRunner> runners = gson.fromJson(ric.getRunners(),
            new TypeToken<List<RemoteInterpreterContextRunner>>() {
        }.getType());

    for (InterpreterContextRunner r : runners) {
      contextRunners.add(new ParagraphRunner(this, r.getNoteId(), r.getParagraphId()));
    }

    return new InterpreterContext(
        ric.getNoteId(),
        ric.getParagraphId(),
        ric.getReplName(),
        ric.getParagraphTitle(),
        ric.getParagraphText(),
        AuthenticationInfo.fromJson(ric.getAuthenticationInfo()),
        (Map<String, Object>) gson.fromJson(ric.getConfig(),
            new TypeToken<Map<String, Object>>() {}.getType()),
        GUI.fromJson(ric.getGui()),
        interpreterGroup.getAngularObjectRegistry(),
        interpreterGroup.getResourcePool(),
        contextRunners, output, remoteWorksController, eventClient, progressMap);
  }


  protected InterpreterOutput createInterpreterOutput(final String noteId, final String
      paragraphId) {
    return new InterpreterOutput(new InterpreterOutputListener() {
      @Override
      public void onUpdateAll(InterpreterOutput out) {
        try {
          eventClient.onInterpreterOutputUpdateAll(
              noteId, paragraphId, out.toInterpreterResultMessage());
        } catch (IOException e) {
          logger.error(e.getMessage(), e);
        }
      }

      @Override
      public void onAppend(int index, InterpreterResultMessageOutput out, byte[] line) {
        String output = new String(line);
        logger.debug("Output Append: {}", output);
        eventClient.onInterpreterOutputAppend(
            noteId, paragraphId, index, output);
      }

      @Override
      public void onUpdate(int index, InterpreterResultMessageOutput out) {
        String output;
        try {
          output = new String(out.toByteArray());
          logger.debug("Output Update: {}", output);
          eventClient.onInterpreterOutputUpdate(
              noteId, paragraphId, index, out.getType(), output);
        } catch (IOException e) {
          logger.error(e.getMessage(), e);
        }
      }
    });
  }


  static class ParagraphRunner extends InterpreterContextRunner {
    Logger logger = LoggerFactory.getLogger(ParagraphRunner.class);
    private transient RemoteInterpreterServer server;

    public ParagraphRunner(RemoteInterpreterServer server, String noteId, String paragraphId) {
      super(noteId, paragraphId);
      this.server = server;
    }

    @Override
    public void run() {
      server.eventClient.run(this);
    }
  }

  static class ZeppelinRemoteWorksController implements RemoteWorksController{
    Logger logger = LoggerFactory.getLogger(ZeppelinRemoteWorksController.class);

    private final long DEFAULT_TIMEOUT_VALUE = 300000;
    private final Map<String, Object> remoteWorksResponsePool;
    private RemoteInterpreterServer server;
    public ZeppelinRemoteWorksController(
        RemoteInterpreterServer server, Map<String, Object> remoteWorksResponsePool) {
      this.remoteWorksResponsePool = remoteWorksResponsePool;
      this.server = server;
    }

    public String generateOwnerKey() {
      String hashKeyText = new String("ownerKey" + System.currentTimeMillis());
      String hashKey = String.valueOf(hashKeyText.hashCode());
      return hashKey;
    }

    public boolean waitForEvent(String eventOwnerKey) throws InterruptedException {
      return waitForEvent(eventOwnerKey, DEFAULT_TIMEOUT_VALUE);
    }

    public boolean waitForEvent(String eventOwnerKey, long timeout) throws InterruptedException {
      boolean wasGetData = false;
      long now = System.currentTimeMillis();
      long endTime = System.currentTimeMillis() + timeout;

      while (endTime >= now) {
        synchronized (this.remoteWorksResponsePool) {
          wasGetData = this.remoteWorksResponsePool.containsKey(eventOwnerKey);
        }
        if (wasGetData == true) {
          break;
        }
        now = System.currentTimeMillis();
        sleep(500);
      }

      return wasGetData;
    }

    @Override
    public List<InterpreterContextRunner> getRemoteContextRunner(String noteId) {
      return getRemoteContextRunner(noteId, null);
    }

    public List<InterpreterContextRunner> getRemoteContextRunner(
        String noteId, String paragraphID) {

      List<InterpreterContextRunner> runners = null;
      String ownerKey = generateOwnerKey();

      ZeppelinServerResourceParagraphRunner resource = new ZeppelinServerResourceParagraphRunner();
      resource.setNoteId(noteId);
      resource.setParagraphId(paragraphID);
      server.eventClient.getZeppelinServerNoteRunner(ownerKey, resource);

      try {
        this.waitForEvent(ownerKey);
      } catch (Exception e) {
        return new LinkedList<>();
      }
      synchronized (this.remoteWorksResponsePool) {
        runners = (List<InterpreterContextRunner>) this.remoteWorksResponsePool.get(ownerKey);
        this.remoteWorksResponsePool.remove(ownerKey);
      }
      return runners;
    }


  }

  private RemoteInterpreterResult convert(InterpreterResult result,
      Map<String, Object> config, GUI gui) {

    List<RemoteInterpreterResultMessage> msg = new LinkedList<>();
    for (InterpreterResultMessage m : result.message()) {
      msg.add(new RemoteInterpreterResultMessage(
          m.getType().name(),
          m.getData()));
    }

    return new RemoteInterpreterResult(
        result.code().name(),
        msg,
        gson.toJson(config),
        gui.toJson());
  }

  @Override
  public String getStatus(String sessionId, String jobId)
      throws TException {
    if (interpreterGroup == null) {
      return Status.UNKNOWN.name();
    }

    synchronized (interpreterGroup) {
      List<Interpreter> interpreters = interpreterGroup.get(sessionId);
      if (interpreters == null) {
        return Status.UNKNOWN.name();
      }

      for (Interpreter intp : interpreters) {
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
    return Status.UNKNOWN.name();
  }



  @Override
  public void onAdd(String interpreterGroupId, AngularObject object) {
    eventClient.angularObjectAdd(object);
  }

  @Override
  public void onUpdate(String interpreterGroupId, AngularObject object) {
    eventClient.angularObjectUpdate(object);
  }

  @Override
  public void onRemove(String interpreterGroupId, String name, String noteId, String paragraphId) {
    eventClient.angularObjectRemove(name, noteId, paragraphId);
  }


  /**
   * Poll event from RemoteInterpreterEventPoller
   * @return
   * @throws TException
   */
  @Override
  public RemoteInterpreterEvent getEvent() throws TException {
    return eventClient.pollEvent();
  }

  /**
   * called when object is updated in client (web) side.
   * @param name
   * @param noteId noteId where the update issues
   * @param paragraphId paragraphId where the update issues
   * @param object
   * @throws TException
   */
  @Override
  public void angularObjectUpdate(String name, String noteId, String paragraphId, String object)
      throws TException {
    AngularObjectRegistry registry = interpreterGroup.getAngularObjectRegistry();
    // first try local objects
    AngularObject ao = registry.get(name, noteId, paragraphId);
    if (ao == null) {
      logger.debug("Angular object {} not exists", name);
      return;
    }

    if (object == null) {
      ao.set(null, false);
      return;
    }

    Object oldObject = ao.get();
    Object value = null;
    if (oldObject != null) {  // first try with previous object's type
      try {
        value = gson.fromJson(object, oldObject.getClass());
        ao.set(value, false);
        return;
      } catch (Exception e) {
        // it's not a previous object's type. proceed to treat as a generic type
        logger.debug(e.getMessage(), e);
      }
    }

    // Generic java object type for json.
    if (value == null) {
      try {
        value = gson.fromJson(object,
          new TypeToken<Map<String, Object>>() {
          }.getType());
      } catch (Exception e) {
        // it's not a generic json object, too. okay, proceed to threat as a string type
        logger.debug(e.getMessage(), e);
      }
    }

    // try string object type at last
    if (value == null) {
      value = gson.fromJson(object, String.class);
    }

    ao.set(value, false);
  }

  /**
   * When zeppelinserver initiate angular object add.
   * Dont't need to emit event to zeppelin server
   */
  @Override
  public void angularObjectAdd(String name, String noteId, String paragraphId, String object)
      throws TException {
    AngularObjectRegistry registry = interpreterGroup.getAngularObjectRegistry();
    // first try local objects
    AngularObject ao = registry.get(name, noteId, paragraphId);
    if (ao != null) {
      angularObjectUpdate(name, noteId, paragraphId, object);
      return;
    }

    // Generic java object type for json.
    Object value = null;
    try {
      value = gson.fromJson(object,
          new TypeToken<Map<String, Object>>() {
          }.getType());
    } catch (Exception e) {
      // it's okay. proceed to treat object as a string
      logger.debug(e.getMessage(), e);
    }

    // try string object type at last
    if (value == null) {
      value = gson.fromJson(object, String.class);
    }

    registry.add(name, value, noteId, paragraphId, false);
  }

  @Override
  public void angularObjectRemove(String name, String noteId, String paragraphId) throws
          TException {
    AngularObjectRegistry registry = interpreterGroup.getAngularObjectRegistry();
    registry.remove(name, noteId, paragraphId, false);
  }

  @Override
  public void resourcePoolResponseGetAll(List<String> resources) throws TException {
    eventClient.putResponseGetAllResources(resources);
  }

  /**
   * Get payload of resource from remote
   * @param resourceId json serialized ResourceId
   * @param object java serialized of the object
   * @throws TException
   */
  @Override
  public void resourceResponseGet(String resourceId, ByteBuffer object) throws TException {
    eventClient.putResponseGetResource(resourceId, object);
  }

  @Override
  public List<String> resourcePoolGetAll() throws TException {
    logger.debug("Request getAll from ZeppelinServer");
    List<String> result = new LinkedList<>();

    if (resourcePool == null) {
      return result;
    }

    ResourceSet resourceSet = resourcePool.getAll(false);
    for (Resource r : resourceSet) {
      result.add(r.toJson());
    }

    return result;
  }

  @Override
  public boolean resourceRemove(String noteId, String paragraphId, String resourceName)
      throws TException {
    Resource resource = resourcePool.remove(noteId, paragraphId, resourceName);
    return resource != null;
  }

  @Override
  public ByteBuffer resourceGet(String noteId, String paragraphId, String resourceName)
      throws TException {
    logger.debug("Request resourceGet {} from ZeppelinServer", resourceName);
    Resource resource = resourcePool.get(noteId, paragraphId, resourceName, false);

    if (resource == null || resource.get() == null || !resource.isSerializable()) {
      return ByteBuffer.allocate(0);
    } else {
      try {
        return Resource.serializeObject(resource.get());
      } catch (IOException e) {
        logger.error(e.getMessage(), e);
        return ByteBuffer.allocate(0);
      }
    }
  }

  @Override
  public ByteBuffer resourceInvokeMethod(
      String noteId, String paragraphId, String resourceName, String invokeMessage) {
    InvokeResourceMethodEventMessage message =
        InvokeResourceMethodEventMessage.fromJson(invokeMessage);

    Resource resource = resourcePool.get(noteId, paragraphId, resourceName, false);
    if (resource == null || resource.get() == null) {
      return ByteBuffer.allocate(0);
    } else {
      try {
        Object o = resource.get();
        Method method = o.getClass().getMethod(
            message.methodName,
            message.getParamTypes());
        Object ret = method.invoke(o, message.params);
        if (message.shouldPutResultIntoResourcePool()) {
          // if return resource name is specified,
          // then put result into resource pool
          // and return empty byte buffer
          resourcePool.put(
              noteId,
              paragraphId,
              message.returnResourceName,
              ret);
          return ByteBuffer.allocate(0);
        } else {
          // if return resource name is not specified,
          // then return serialized result
          ByteBuffer serialized = Resource.serializeObject(ret);
          if (serialized == null) {
            return ByteBuffer.allocate(0);
          } else {
            return serialized;
          }
        }
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
        return ByteBuffer.allocate(0);
      }
    }
  }

  /**
   * Get payload of resource from remote
   * @param invokeResourceMethodEventMessage json serialized InvokeResourcemethodEventMessage
   * @param object java serialized of the object
   * @throws TException
   */
  @Override
  public void resourceResponseInvokeMethod(
      String invokeResourceMethodEventMessage, ByteBuffer object) throws TException {
    InvokeResourceMethodEventMessage message =
        InvokeResourceMethodEventMessage.fromJson(invokeResourceMethodEventMessage);

    if (message.shouldPutResultIntoResourcePool()) {
      Resource resource = resourcePool.get(
          message.resourceId.getNoteId(),
          message.resourceId.getParagraphId(),
          message.returnResourceName,
          true);
      eventClient.putResponseInvokeMethod(message, resource);
    } else {
      eventClient.putResponseInvokeMethod(message, object);
    }
  }

  @Override
  public void angularRegistryPush(String registryAsString) throws TException {
    try {
      Map<String, Map<String, AngularObject>> deserializedRegistry = gson
              .fromJson(registryAsString,
                      new TypeToken<Map<String, Map<String, AngularObject>>>() { }.getType());
      interpreterGroup.getAngularObjectRegistry().setRegistry(deserializedRegistry);
    } catch (Exception e) {
      logger.info("Exception in RemoteInterpreterServer while angularRegistryPush, nolock", e);
    }
  }

  protected InterpreterOutput createAppOutput(final String noteId,
                                            final String paragraphId,
                                            final String appId) {
    return new InterpreterOutput(new InterpreterOutputListener() {
      @Override
      public void onUpdateAll(InterpreterOutput out) {

      }

      @Override
      public void onAppend(int index, InterpreterResultMessageOutput out, byte[] line) {
        eventClient.onAppOutputAppend(noteId, paragraphId, index, appId, new String(line));
      }

      @Override
      public void onUpdate(int index, InterpreterResultMessageOutput out) {
        try {
          eventClient.onAppOutputUpdate(noteId, paragraphId, index, appId,
              out.getType(), new String(out.toByteArray()));
        } catch (IOException e) {
          logger.error(e.getMessage(), e);
        }
      }
    });

  }

  private ApplicationContext getApplicationContext(
      HeliumPackage packageInfo, String noteId, String paragraphId, String applicationInstanceId) {
    InterpreterOutput out = createAppOutput(noteId, paragraphId, applicationInstanceId);
    return new ApplicationContext(
        noteId,
        paragraphId,
        applicationInstanceId,
        new HeliumAppAngularObjectRegistry(angularObjectRegistry, noteId, applicationInstanceId),
        out);
  }

  @Override
  public RemoteApplicationResult loadApplication(
      String applicationInstanceId, String packageInfo, String noteId, String paragraphId)
      throws TException {
    if (runningApplications.containsKey(applicationInstanceId)) {
      logger.warn("Application instance {} is already running");
      return new RemoteApplicationResult(true, "");
    }
    HeliumPackage pkgInfo = HeliumPackage.fromJson(packageInfo);
    ApplicationContext context = getApplicationContext(
        pkgInfo, noteId, paragraphId, applicationInstanceId);
    try {
      Application app = null;
      logger.info(
          "Loading application {}({}), artifact={}, className={} into note={}, paragraph={}",
          pkgInfo.getName(),
          applicationInstanceId,
          pkgInfo.getArtifact(),
          pkgInfo.getClassName(),
          noteId,
          paragraphId);
      app = appLoader.load(pkgInfo, context);
      runningApplications.put(
          applicationInstanceId,
          new RunningApplication(pkgInfo, app, noteId, paragraphId));
      return new RemoteApplicationResult(true, "");
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      return new RemoteApplicationResult(false, e.getMessage());
    }
  }

  @Override
  public RemoteApplicationResult unloadApplication(String applicationInstanceId)
      throws TException {
    RunningApplication runningApplication = runningApplications.remove(applicationInstanceId);
    if (runningApplication != null) {
      try {
        logger.info("Unloading application {}", applicationInstanceId);
        runningApplication.app.unload();
      } catch (ApplicationException e) {
        logger.error(e.getMessage(), e);
        return new RemoteApplicationResult(false, e.getMessage());
      }
    }
    return new RemoteApplicationResult(true, "");
  }

  @Override
  public RemoteApplicationResult runApplication(String applicationInstanceId)
      throws TException {
    logger.info("run application {}", applicationInstanceId);

    RunningApplication runningApp = runningApplications.get(applicationInstanceId);
    if (runningApp == null) {
      logger.error("Application instance {} not exists", applicationInstanceId);
      return new RemoteApplicationResult(false, "Application instance does not exists");
    } else {
      ApplicationContext context = runningApp.app.context();
      try {
        context.out.clear();
        context.out.setType(InterpreterResult.Type.ANGULAR);
        ResourceSet resource = appLoader.findRequiredResourceSet(
            runningApp.pkg.getResources(),
            context.getNoteId(),
            context.getParagraphId());
        for (Resource res : resource) {
          System.err.println("Resource " + res.get());
        }
        runningApp.app.run(resource);
        context.out.flush();
        InterpreterResultMessageOutput out = context.out.getOutputAt(0);
        eventClient.onAppOutputUpdate(
            context.getNoteId(),
            context.getParagraphId(),
            0,
            applicationInstanceId,
            out.getType(),
            new String(out.toByteArray()));
        return new RemoteApplicationResult(true, "");
      } catch (ApplicationException | IOException e) {
        return new RemoteApplicationResult(false, e.getMessage());
      }
    }
  }

  private static class RunningApplication {
    public final Application app;
    public final HeliumPackage pkg;
    public final String noteId;
    public final String paragraphId;

    public RunningApplication(HeliumPackage pkg,
                              Application app,
                              String noteId,
                              String paragraphId) {
      this.app = app;
      this.pkg = pkg;
      this.noteId = noteId;
      this.paragraphId = paragraphId;
    }
  };
}
