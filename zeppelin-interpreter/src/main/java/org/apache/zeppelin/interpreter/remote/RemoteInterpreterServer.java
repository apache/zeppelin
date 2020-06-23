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
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.apache.zeppelin.cluster.ClusterManagerClient;
import org.apache.zeppelin.cluster.meta.ClusterMeta;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
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
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.thrift.RegisterInfo;
import org.apache.zeppelin.interpreter.thrift.RemoteApplicationResult;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterContext;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterEventService;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResult;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResultMessage;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService;
import org.apache.zeppelin.resource.DistributedResourcePool;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.resource.ResourceSet;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.scheduler.JobListener;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.apache.zeppelin.cluster.meta.ClusterMetaType.INTP_PROCESS_META;

/**
 * Entry point for Interpreter process.
 * Accepting thrift connections from ZeppelinServer.
 */
public class RemoteInterpreterServer extends Thread
    implements RemoteInterpreterService.Iface {

  private static Logger LOGGER = LoggerFactory.getLogger(RemoteInterpreterServer.class);

  private String interpreterGroupId;
  private InterpreterGroup interpreterGroup;
  private AngularObjectRegistry angularObjectRegistry;
  private InterpreterHookRegistry hookRegistry;
  private DistributedResourcePool resourcePool;
  private ApplicationLoader appLoader;
  private Gson gson = new Gson();
  private String launcherEnv = System.getenv("ZEPPELIN_INTERPRETER_LAUNCHER");

  private String intpEventServerHost;
  private int intpEventServerPort;
  private String host;
  private int port;
  private TThreadPoolServer server;
  RemoteInterpreterEventClient intpEventClient;
  private DependencyResolver depLoader;

  private final Map<String, RunningApplication> runningApplications =
      Collections.synchronizedMap(new HashMap<String, RunningApplication>());

  private Map<String, Object> remoteWorksResponsePool;

  private final long DEFAULT_SHUTDOWN_TIMEOUT = 2000;

  // Hold information for manual progress update
  private ConcurrentMap<String, Integer> progressMap = new ConcurrentHashMap<>();

  // keep track of the running jobs for job recovery.
  private ConcurrentMap<String, InterpretJob> runningJobs = new ConcurrentHashMap<>();
  // cache result threshold, result cache is for purpose of recover paragraph even after
  // paragraph is finished
  private int resultCacheInSeconds;
  private ScheduledExecutorService resultCleanService = Executors.newSingleThreadScheduledExecutor();

  private boolean isTest;

  // cluster manager client
  private ZeppelinConfiguration zconf = ZeppelinConfiguration.create();
  private ClusterManagerClient clusterManagerClient;

  public RemoteInterpreterServer(String intpEventServerHost,
                                 int intpEventServerPort,
                                 String interpreterGroupId,
                                 String portRange)
      throws IOException, TTransportException {
    this(intpEventServerHost, intpEventServerPort, portRange, interpreterGroupId, false);
  }

  public RemoteInterpreterServer(String intpEventServerHost,
                                 int intpEventServerPort,
                                 String portRange,
                                 String interpreterGroupId,
                                 boolean isTest)
      throws TTransportException, IOException {
    LOGGER.info("Starting remote interpreter server on port {}, intpEventServerAddress: {}:{}", port,
            intpEventServerHost, intpEventServerPort);
    if (null != intpEventServerHost) {
      this.intpEventServerHost = intpEventServerHost;
      this.intpEventServerPort = intpEventServerPort;
      if (!isTest) {
        intpEventClient = new RemoteInterpreterEventClient(intpEventServerHost, intpEventServerPort);
      }
    } else {
      // DevInterpreter
      this.port = intpEventServerPort;
    }
    this.isTest = isTest;
    this.interpreterGroupId = interpreterGroupId;
    RemoteInterpreterService.Processor<RemoteInterpreterServer> processor =
        new RemoteInterpreterService.Processor<>(this);
    TServerSocket serverTransport;
    if (null == intpEventServerHost) {
      // Dev Interpreter
      serverTransport = new TServerSocket(intpEventServerPort);
    } else {
      serverTransport = RemoteInterpreterUtils.createTServerSocket(portRange);
      this.port = serverTransport.getServerSocket().getLocalPort();
      this.host = RemoteInterpreterUtils.findAvailableHostAddress();
      LOGGER.info("Launching ThriftServer at " + this.host + ":" + this.port);
    }
    server = new TThreadPoolServer(
        new TThreadPoolServer.Args(serverTransport).processor(processor));
    remoteWorksResponsePool = Collections.synchronizedMap(new HashMap<String, Object>());

    if (zconf.isClusterMode()) {
      clusterManagerClient = ClusterManagerClient.getInstance(zconf);
      clusterManagerClient.start(interpreterGroupId);
    }
  }

  @Override
  public void run() {
    if (null != intpEventServerHost && !isTest) {
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

          if (zconf.isClusterMode()) {
            // Cluster mode, discovering interpreter processes through metadata registration
            // TODO (Xun): Unified use of cluster metadata for process discovery of all operating modes
            // 1. Can optimize the startup logic of the process
            // 2. Can solve the problem that running the interpreter's IP in docker may be a virtual IP
            putClusterMeta();
          } else {
            if (!interrupted) {
              RegisterInfo registerInfo = new RegisterInfo(host, port, interpreterGroupId);
              try {
                LOGGER.info("Registering interpreter process");
                intpEventClient.registerInterpreterProcess(registerInfo);
                LOGGER.info("Registered interpreter process");
              } catch (Exception e) {
                LOGGER.error("Error while registering interpreter: {}", registerInfo, e);
                try {
                  shutdown();
                } catch (TException e1) {
                  LOGGER.warn("Exception occurs while shutting down", e1);
                }
              }
            }
          }

          if (launcherEnv != null && "yarn".endsWith(launcherEnv)) {
            try {
              YarnUtils.register(host, port);
              Thread thread = new Thread(() -> {
                while(!Thread.interrupted() && server.isServing()) {
                  YarnUtils.heartbeat();
                  try {
                    Thread.sleep(60 * 1000);
                  } catch (InterruptedException e) {
                    LOGGER.warn(e.getMessage(), e);
                  }
                }
              });
              thread.setName("RM-Heartbeat-Thread");
              thread.start();
            } catch (Exception e) {
              LOGGER.error("Fail to register yarn app", e);
            }
          }
        }
      }).start();
    }
    server.serve();
  }

  @Override
  public void shutdown() throws TException {
    Thread shutDownThread = new Thread(() -> {
      LOGGER.info("Shutting down...");
      // delete interpreter cluster meta
      deleteClusterMeta();

      if (interpreterGroup != null) {
        synchronized (interpreterGroup) {
          for (List<Interpreter> session : interpreterGroup.values()) {
            for (Interpreter interpreter : session) {
              try {
                interpreter.close();
              } catch (InterpreterException e) {
                LOGGER.warn("Fail to close interpreter", e);
              }
            }
          }
        }
      }
      if (!isTest) {
        SchedulerFactory.singleton().destroy();
      }

      if ("yarn".equals(launcherEnv)) {
        try {
          YarnUtils.unregister(true, "");
        } catch (Exception e) {
          LOGGER.error("Fail to unregister yarn app", e);
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
          LOGGER.info("Exception in RemoteInterpreterServer while shutdown, Thread.sleep", e);
        }
      }

      if (server.isServing()) {
        LOGGER.info("Force shutting down");
        System.exit(0);
      }

      LOGGER.info("Shutting down");
    }, "Shutdown-Thread");

    shutDownThread.start();
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
    String zeppelinServerHost = null;
    int port = Constants.ZEPPELIN_INTERPRETER_DEFAUlT_PORT;
    String portRange = ":";
    String interpreterGroupId = null;
    if (args.length > 0) {
      zeppelinServerHost = args[0];
      port = Integer.parseInt(args[1]);
      interpreterGroupId = args[2];
      if (args.length > 3) {
        portRange = args[3];
      }
    }
    RemoteInterpreterServer remoteInterpreterServer =
        new RemoteInterpreterServer(zeppelinServerHost, port, interpreterGroupId, portRange);
    remoteInterpreterServer.start();

    // add signal handler
    Signal.handle(new Signal("TERM"), new SignalHandler() {
      @Override
      public void handle(Signal signal) {
        try {
          LOGGER.info("Receive TERM Signal");
          remoteInterpreterServer.shutdown();
        } catch (TException e) {
          LOGGER.error("Error on shutdown RemoteInterpreterServer", e);
        }
      }
    });

    remoteInterpreterServer.join();
    LOGGER.info("RemoteInterpreterServer thread is finished");
    System.exit(0);
  }

  // Submit interpreter process metadata information to cluster metadata
  private void putClusterMeta() {
    if (!zconf.isClusterMode()){
      return;
    }
    String nodeName = clusterManagerClient.getClusterNodeName();

    // commit interpreter meta
    HashMap<String, Object> meta = new HashMap<>();
    meta.put(ClusterMeta.NODE_NAME, nodeName);
    meta.put(ClusterMeta.INTP_PROCESS_NAME, interpreterGroupId);
    meta.put(ClusterMeta.INTP_TSERVER_HOST, host);
    meta.put(ClusterMeta.INTP_TSERVER_PORT, port);
    meta.put(ClusterMeta.INTP_START_TIME, LocalDateTime.now());
    meta.put(ClusterMeta.LATEST_HEARTBEAT, LocalDateTime.now());
    meta.put(ClusterMeta.STATUS, ClusterMeta.ONLINE_STATUS);

    clusterManagerClient.putClusterMeta(INTP_PROCESS_META, interpreterGroupId, meta);
  }

  private void deleteClusterMeta() {
    if (!zconf.isClusterMode()){
      return;
    }

    try {
      // delete interpreter cluster meta
      clusterManagerClient.deleteClusterMeta(INTP_PROCESS_META, interpreterGroupId);
      Thread.sleep(300);
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  @Override
  public void createInterpreter(String interpreterGroupId, String sessionId, String
      className, Map<String, String> properties, String userName) throws TException {
    try {
      if (interpreterGroup == null) {
        interpreterGroup = new InterpreterGroup(interpreterGroupId);
        angularObjectRegistry = new AngularObjectRegistry(interpreterGroup.getId(), intpEventClient);
        hookRegistry = new InterpreterHookRegistry();
        resourcePool = new DistributedResourcePool(interpreterGroup.getId(), intpEventClient);
        interpreterGroup.setInterpreterHookRegistry(hookRegistry);
        interpreterGroup.setAngularObjectRegistry(angularObjectRegistry);
        interpreterGroup.setResourcePool(resourcePool);
        intpEventClient.setIntpGroupId(interpreterGroupId);

        String localRepoPath = properties.get("zeppelin.interpreter.localRepo");
        if (properties.containsKey("zeppelin.interpreter.output.limit")) {
          InterpreterOutput.limit = Integer.parseInt(
                  properties.get("zeppelin.interpreter.output.limit"));
        }

        depLoader = new DependencyResolver(localRepoPath);
        appLoader = new ApplicationLoader(resourcePool, depLoader);

        resultCacheInSeconds =
                Integer.parseInt(properties.getOrDefault("zeppelin.interpreter.result.cache", "0"));
      }

      try {
        Class<Interpreter> replClass = (Class<Interpreter>) Object.class.forName(className);
        Properties p = new Properties();
        p.putAll(properties);
        setSystemProperty(p);

        Constructor<Interpreter> constructor =
                replClass.getConstructor(new Class[]{Properties.class});
        Interpreter repl = constructor.newInstance(p);
        repl.setClassloaderUrls(new URL[]{});
        LOGGER.info("Instantiate interpreter {}", className);
        repl.setInterpreterGroup(interpreterGroup);
        repl.setUserName(userName);

        interpreterGroup.addInterpreterToSession(new LazyOpenInterpreter(repl), sessionId);
      } catch (ClassNotFoundException | NoSuchMethodException | SecurityException
              | InstantiationException | IllegalAccessException
              | IllegalArgumentException | InvocationTargetException e) {
        LOGGER.error(e.getMessage(), e);
        throw new TException(e);
      }
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
      throw new TException(e.getMessage(), e);
    }
  }

  protected InterpreterGroup getInterpreterGroup() {
    return interpreterGroup;
  }

  protected ResourcePool getResourcePool() {
    return resourcePool;
  }

  protected RemoteInterpreterEventClient getIntpEventClient() {
    return intpEventClient;
  }

  private void setSystemProperty(Properties properties) {
    for (Object key : properties.keySet()) {
      if (!RemoteInterpreterUtils.isEnvString((String) key)) {
        String value = properties.getProperty((String) key);
        if (!StringUtils.isBlank(value)) {
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
    LOGGER.info(String.format("Open Interpreter %s for session %s ", className, sessionId));
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
          LOGGER.info("Unload App {} ", appInfo.pkg.getName());
          appInfo.app.unload();
          // see ApplicationState.Status.UNLOADED
          intpEventClient.onAppStatusUpdate(appInfo.noteId, appInfo.paragraphId, appId, "UNLOADED");
        } catch (ApplicationException e) {
          LOGGER.error(e.getMessage(), e);
        }
      }
    }

    // close interpreters
    if (interpreterGroup != null) {
      synchronized (interpreterGroup) {
        List<Interpreter> interpreters = interpreterGroup.get(sessionId);
        if (interpreters != null) {
          Iterator<Interpreter> it = interpreters.iterator();
          while (it.hasNext()) {
            Interpreter inp = it.next();
            if (inp.getClassName().equals(className)) {
              try {
                inp.close();
              } catch (InterpreterException e) {
                LOGGER.warn("Fail to close interpreter", e);
              }
              it.remove();
              break;
            }
          }
        }
      }
    }
  }

  @Override
  public void reconnect(String host, int port) throws TException {
    try {
      LOGGER.info("Reconnect to this interpreter process from {}:{}", host, port);
      this.intpEventServerHost = host;
      this.intpEventServerPort = port;
      intpEventClient = new RemoteInterpreterEventClient(intpEventServerHost, intpEventServerPort);
      intpEventClient.setIntpGroupId(interpreterGroupId);

      this.angularObjectRegistry = new AngularObjectRegistry(interpreterGroup.getId(), intpEventClient);
      this.resourcePool = new DistributedResourcePool(interpreterGroup.getId(), intpEventClient);

      // reset all the available InterpreterContext's components that use intpEventClient.
      for (InterpreterContext context : InterpreterContext.getAllContexts().values()) {
        context.setIntpEventClient(intpEventClient);
        context.setAngularObjectRegistry(angularObjectRegistry);
        context.setResourcePool(resourcePool);
      }
    } catch (Exception e) {
      throw new TException("Fail to reconnect", e);
    }
  }

  @Override
  public RemoteInterpreterResult interpret(String sessionId,
                                           String className,
                                           String st,
                                           RemoteInterpreterContext interpreterContext)
      throws TException {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("st:\n{}", st);
    }
    Interpreter intp = getInterpreter(sessionId, className);
    InterpreterContext context = convert(interpreterContext);
    context.setInterpreterClassName(intp.getClassName());

    InterpretJob interpretJob = null;
    boolean isRecover = Boolean.parseBoolean(
            context.getLocalProperties().getOrDefault("isRecover", "false"));
    if (isRecover) {
      LOGGER.info("Recovering paragraph: " + context.getParagraphId() + " of note: "
              + context.getNoteId());
      interpretJob = runningJobs.get(context.getParagraphId());
      if (interpretJob == null) {
        InterpreterResult result = new InterpreterResult(Code.ERROR, "Job is finished, unable to recover it");
        return convert(result,
                context.getConfig(),
                context.getGui(),
                context.getNoteGui());
      }
    } else {
      Scheduler scheduler = intp.getScheduler();
      InterpretJobListener jobListener = new InterpretJobListener();
      interpretJob = new InterpretJob(
              context.getParagraphId(),
              "RemoteInterpretJob_" + System.currentTimeMillis(),
              jobListener,
              intp,
              st,
              context);
      runningJobs.put(context.getParagraphId(), interpretJob);
      scheduler.submit(interpretJob);
    }

    while (!interpretJob.isTerminated()) {
      JobListener jobListener = interpretJob.getListener();
      synchronized (jobListener) {
        try {
          jobListener.wait(1000);
        } catch (InterruptedException e) {
          LOGGER.info("Exception in RemoteInterpreterServer while interpret, jobListener.wait", e);
        }
      }
    }

    progressMap.remove(context.getParagraphId());
    resultCleanService.schedule(()-> {
      runningJobs.remove(context.getParagraphId());
      }, resultCacheInSeconds, TimeUnit.SECONDS);

    InterpreterResult result = interpretJob.getReturn();
    // in case of job abort in PENDING status, result can be null
    if (result == null) {
      result = new InterpreterResult(Code.KEEP_PREVIOUS_RESULT);
    }
    return convert(result,
            context.getConfig(),
            context.getGui(),
            context.getNoteGui());
  }

  class InterpretJobListener implements JobListener {

    @Override
    public void onProgressUpdate(Job job, int progress) {
    }

    @Override
    public void onStatusChange(Job job, Status before, Status after) {
      synchronized (this) {
        notifyAll();
      }
    }
  }

  public static class InterpretJob extends Job<InterpreterResult> {

    private Interpreter interpreter;
    private String script;
    private InterpreterContext context;
    private Map<String, Object> infos;
    private InterpreterResult results;

    public InterpretJob(
        String jobId,
        String jobName,
        JobListener listener,
        Interpreter interpreter,
        String script,
        InterpreterContext context) {
      super(jobId, jobName, listener);
      this.interpreter = interpreter;
      this.script = script;
      this.context = context;
    }

    @Override
    public InterpreterResult getReturn() {
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
          String cmdDev = interpreter.getHook(noteId, HookType.PRE_EXEC_DEV.getName());
          String cmdUser = interpreter.getHook(noteId, HookType.PRE_EXEC.getName());

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
          String cmdDev = interpreter.getHook(noteId, HookType.POST_EXEC_DEV.getName());
          String cmdUser = interpreter.getHook(noteId, HookType.POST_EXEC.getName());

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
    public InterpreterResult jobRun() throws Throwable {
      ClassLoader currentThreadContextClassloader = Thread.currentThread().getContextClassLoader();
      try {
        InterpreterContext.set(context);
        // clear the result of last run in frontend before running this paragraph.
        context.out.clear();

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
          // note scope first, followed by global scope.
          // Here's the code after hooking:
          //     global_pre_hook
          //     note_pre_hook
          //     script
          //     note_post_hook
          //     global_post_hook
          processInterpreterHooks(context.getNoteId());
          processInterpreterHooks(null);
          LOGGER.debug("Script after hooks: " + script);
          result = interpreter.interpret(script, context);
        }

        // data from context.out is prepended to InterpreterResult if both defined
        context.out.flush();
        List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();

        for (InterpreterResultMessage resultMessage : result.message()) {
          // only add non-empty InterpreterResultMessage
          if (!StringUtils.isBlank(resultMessage.getData())) {
            resultMessages.add(resultMessage);
          }
        }

        List<String> stringResult = new ArrayList<>();
        for (InterpreterResultMessage msg : resultMessages) {
          if (msg.getType() == InterpreterResult.Type.IMG) {
            LOGGER.debug("InterpreterResultMessage: IMAGE_DATA");
          } else {
            LOGGER.debug("InterpreterResultMessage: " + msg.toString());
          }
          stringResult.add(msg.getData());
        }
        // put result into resource pool
        if (context.getLocalProperties().containsKey("saveAs")) {
          if (stringResult.size() == 1) {
            LOGGER.info("Saving result into ResourcePool as single string: " +
                    context.getLocalProperties().get("saveAs"));
            context.getResourcePool().put(
                    context.getLocalProperties().get("saveAs"), stringResult.get(0));
          } else {
            LOGGER.info("Saving result into ResourcePool as string list: " +
                    context.getLocalProperties().get("saveAs"));
            context.getResourcePool().put(
                    context.getLocalProperties().get("saveAs"), stringResult);
          }
        }
        return new InterpreterResult(result.code(), resultMessages);
      } catch (Throwable e) {
        return new InterpreterResult(Code.ERROR, ExceptionUtils.getStackTrace(e));
      } finally {
        Thread.currentThread().setContextClassLoader(currentThreadContextClassloader);
        InterpreterContext.remove();
      }
    }

    @Override
    protected boolean jobAbort() {
      return false;
    }

    @Override
    public void setResult(InterpreterResult result) {
      this.results = result;
    }
  }


  @Override
  public void cancel(String sessionId,
                     String className,
                     RemoteInterpreterContext interpreterContext) throws TException {
    LOGGER.info("cancel {} {}", className, interpreterContext.getParagraphId());
    Interpreter intp = getInterpreter(sessionId, className);
    String jobId = interpreterContext.getParagraphId();
    Job job = intp.getScheduler().getJob(jobId);

    if (job != null && job.getStatus() == Status.PENDING) {
      job.setStatus(Status.ABORT);
    } else {
      Thread thread = new Thread( ()-> {
        try {
          intp.cancel(convert(interpreterContext, null));
        } catch (InterpreterException e) {
          LOGGER.error("Fail to cancel paragraph: " + interpreterContext.getParagraphId());
        }
      });
      thread.start();
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
                                                String className,
                                                String buf,
                                                int cursor,
                                                RemoteInterpreterContext remoteInterpreterContext)
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
    return InterpreterContext.builder()
        .setNoteId(ric.getNoteId())
        .setNoteName(ric.getNoteName())
        .setParagraphId(ric.getParagraphId())
        .setReplName(ric.getReplName())
        .setParagraphTitle(ric.getParagraphTitle())
        .setParagraphText(ric.getParagraphText())
        .setLocalProperties(ric.getLocalProperties())
        .setAuthenticationInfo(AuthenticationInfo.fromJson(ric.getAuthenticationInfo()))
        .setGUI(GUI.fromJson(ric.getGui()))
        .setConfig(gson.fromJson(ric.getConfig(),
                   new TypeToken<Map<String, Object>>() {}.getType()))
        .setNoteGUI(GUI.fromJson(ric.getNoteGui()))
        .setAngularObjectRegistry(interpreterGroup.getAngularObjectRegistry())
        .setResourcePool(interpreterGroup.getResourcePool())
        .setInterpreterOut(output)
        .setIntpEventClient(intpEventClient)
        .setProgressMap(progressMap)
        .build();
  }


  protected InterpreterOutput createInterpreterOutput(final String noteId, final String
      paragraphId) {
    return new InterpreterOutput(new InterpreterOutputListener() {
      @Override
      public void onUpdateAll(InterpreterOutput out) {
        try {
          intpEventClient.onInterpreterOutputUpdateAll(
              noteId, paragraphId, out.toInterpreterResultMessage());
        } catch (IOException e) {
          LOGGER.error(e.getMessage(), e);
        }
      }

      @Override
      public void onAppend(int index, InterpreterResultMessageOutput out, byte[] line) {
        String output = new String(line);
        LOGGER.debug("Output Append: {}", output);
        intpEventClient.onInterpreterOutputAppend(
            noteId, paragraphId, index, output);
      }

      @Override
      public void onUpdate(int index, InterpreterResultMessageOutput out) {
        String output;
        try {
          output = new String(out.toByteArray());
          LOGGER.debug("Output Update for index {}: {}", index, output);
          intpEventClient.onInterpreterOutputUpdate(
              noteId, paragraphId, index, out.getType(), output);
        } catch (IOException e) {
          LOGGER.error(e.getMessage(), e);
        }
      }
    });
  }

  private RemoteInterpreterResult convert(InterpreterResult result,
                                          Map<String, Object> config, GUI gui, GUI noteGui) {

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
        gui.toJson(),
        noteGui.toJson());
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
        Scheduler scheduler = intp.getScheduler();
        if (scheduler != null) {
          Job job = scheduler.getJob(jobId);
          if (job != null) {
            return job.getStatus().name();
          }
        }
      }
    }
    return Status.UNKNOWN.name();
  }

  /**
   * called when object is updated in client (web) side.
   *
   * @param name
   * @param noteId      noteId where the update issues
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
      LOGGER.debug("Angular object {} not exists", name);
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
        LOGGER.debug(e.getMessage(), e);
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
        LOGGER.debug(e.getMessage(), e);
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
      LOGGER.debug(e.getMessage(), e);
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
  public List<String> resourcePoolGetAll() throws TException {
    LOGGER.debug("Request resourcePoolGetAll from ZeppelinServer");
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
    LOGGER.debug("Request resourceGet {} from ZeppelinServer", resourceName);
    Resource resource = resourcePool.get(noteId, paragraphId, resourceName, false);

    if (resource == null || resource.get() == null || !resource.isSerializable()) {
      return ByteBuffer.allocate(0);
    } else {
      try {
        return Resource.serializeObject(resource.get());
      } catch (IOException e) {
        LOGGER.error(e.getMessage(), e);
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
          // and return the Resource class instead of actual return object.
          resourcePool.put(
              noteId,
              paragraphId,
              message.returnResourceName,
              ret);

          Resource returnValResource = resourcePool.get(noteId, paragraphId, message.returnResourceName);
          ByteBuffer serialized = Resource.serializeObject(returnValResource);
          if (serialized == null) {
            return ByteBuffer.allocate(0);
          } else {
            return serialized;
          }
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
        LOGGER.error(e.getMessage(), e);
        return ByteBuffer.allocate(0);
      }
    }
  }

  @Override
  public void angularRegistryPush(String registryAsString) throws TException {
    try {
      Map<String, Map<String, AngularObject>> deserializedRegistry = gson
          .fromJson(registryAsString,
              new TypeToken<Map<String, Map<String, AngularObject>>>() {
              }.getType());
      interpreterGroup.getAngularObjectRegistry().setRegistry(deserializedRegistry);
    } catch (Exception e) {
      LOGGER.info("Exception in RemoteInterpreterServer while angularRegistryPush, nolock", e);
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
        intpEventClient.onAppOutputAppend(noteId, paragraphId, index, appId, new String(line));
      }

      @Override
      public void onUpdate(int index, InterpreterResultMessageOutput out) {
        try {
          intpEventClient.onAppOutputUpdate(noteId, paragraphId, index, appId,
              out.getType(), new String(out.toByteArray()));
        } catch (IOException e) {
          LOGGER.error(e.getMessage(), e);
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
      LOGGER.warn("Application instance {} is already running");
      return new RemoteApplicationResult(true, "");
    }
    HeliumPackage pkgInfo = HeliumPackage.fromJson(packageInfo);
    ApplicationContext context = getApplicationContext(
        pkgInfo, noteId, paragraphId, applicationInstanceId);
    try {
      Application app = null;
      LOGGER.info(
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
      LOGGER.error(e.getMessage(), e);
      return new RemoteApplicationResult(false, e.getMessage());
    }
  }

  @Override
  public RemoteApplicationResult unloadApplication(String applicationInstanceId)
      throws TException {
    RunningApplication runningApplication = runningApplications.remove(applicationInstanceId);
    if (runningApplication != null) {
      try {
        LOGGER.info("Unloading application {}", applicationInstanceId);
        runningApplication.app.unload();
      } catch (ApplicationException e) {
        LOGGER.error(e.getMessage(), e);
        return new RemoteApplicationResult(false, e.getMessage());
      }
    }
    return new RemoteApplicationResult(true, "");
  }

  @Override
  public RemoteApplicationResult runApplication(String applicationInstanceId)
      throws TException {
    LOGGER.info("run application {}", applicationInstanceId);

    RunningApplication runningApp = runningApplications.get(applicationInstanceId);
    if (runningApp == null) {
      LOGGER.error("Application instance {} not exists", applicationInstanceId);
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
        intpEventClient.onAppOutputUpdate(
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

    RunningApplication(HeliumPackage pkg,
                              Application app,
                              String noteId,
                              String paragraphId) {
      this.app = app;
      this.pkg = pkg;
      this.noteId = noteId;
      this.paragraphId = paragraphId;
    }
  }

  ;
}
