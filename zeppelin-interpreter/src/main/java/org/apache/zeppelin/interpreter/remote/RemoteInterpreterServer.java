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


import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.*;

import org.apache.thrift.TException;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportException;
import org.apache.zeppelin.display.*;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterContext;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterEvent;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResult;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService;
import org.apache.zeppelin.resource.*;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.scheduler.JobListener;
import org.apache.zeppelin.scheduler.JobProgressPoller;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

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
  DistributedResourcePool resourcePool;
  Gson gson = new Gson();

  RemoteInterpreterService.Processor<RemoteInterpreterServer> processor;
  RemoteInterpreterServer handler;
  private int port;
  private TThreadPoolServer server;

  RemoteInterpreterEventClient eventClient = new RemoteInterpreterEventClient();

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
    if (interpreterGroup != null) {
      interpreterGroup.close();
      interpreterGroup.destroy();
    }

    server.stop();

    // server.stop() does not always finish server.serve() loop
    // sometimes server.serve() is hanging even after server.stop() call.
    // this case, need to force kill the process

    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < 2000 && server.isServing()) {
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
      throws TTransportException, InterruptedException {
    int port = Integer.parseInt(args[0]);
    RemoteInterpreterServer remoteInterpreterServer = new RemoteInterpreterServer(port);
    remoteInterpreterServer.start();
    remoteInterpreterServer.join();
    System.exit(0);
  }


  @Override
  public void createInterpreter(String interpreterGroupId, String noteId, String
      className,
                                Map<String, String> properties) throws TException {
    if (interpreterGroup == null) {
      interpreterGroup = new InterpreterGroup(interpreterGroupId);
      angularObjectRegistry = new AngularObjectRegistry(interpreterGroup.getId(), this);
      resourcePool = new DistributedResourcePool(interpreterGroup.getId(), eventClient);
      interpreterGroup.setAngularObjectRegistry(angularObjectRegistry);
      interpreterGroup.setResourcePool(resourcePool);
    }

    try {
      Class<Interpreter> replClass = (Class<Interpreter>) Object.class.forName(className);
      Properties p = new Properties();
      p.putAll(properties);
      setSystemProperty(p);

      Constructor<Interpreter> constructor =
          replClass.getConstructor(new Class[] {Properties.class});
      Interpreter repl = constructor.newInstance(p);

      ClassLoader cl = ClassLoader.getSystemClassLoader();
      repl.setClassloaderUrls(new URL[]{});

      synchronized (interpreterGroup) {
        List<Interpreter> interpreters = interpreterGroup.get(noteId);
        if (interpreters == null) {
          interpreters = new LinkedList<Interpreter>();
          interpreterGroup.put(noteId, interpreters);
        }

        interpreters.add(new LazyOpenInterpreter(new ClassloaderInterpreter(repl, cl)));
      }

      logger.info("Instantiate interpreter {}", className);
      repl.setInterpreterGroup(interpreterGroup);
    } catch (ClassNotFoundException | NoSuchMethodException | SecurityException
        | InstantiationException | IllegalAccessException
        | IllegalArgumentException | InvocationTargetException e) {
      logger.error(e.toString(), e);
      throw new TException(e);
    }
  }

  private void setSystemProperty(Properties properties) {
    for (Object key : properties.keySet()) {
      if (!RemoteInterpreter.isEnvString((String) key)) {
        String value = properties.getProperty((String) key);
        if (value == null || value.isEmpty()) {
          System.clearProperty((String) key);
        } else {
          System.setProperty((String) key, properties.getProperty((String) key));
        }
      }
    }
  }

  private Interpreter getInterpreter(String noteId, String className) throws TException {
    if (interpreterGroup == null) {
      throw new TException(
          new InterpreterException("Interpreter instance " + className + " not created"));
    }
    synchronized (interpreterGroup) {
      List<Interpreter> interpreters = interpreterGroup.get(noteId);
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
  public void open(String noteId, String className) throws TException {
    Interpreter intp = getInterpreter(noteId, className);
    intp.open();
  }

  @Override
  public void close(String noteId, String className) throws TException {
    synchronized (interpreterGroup) {
      List<Interpreter> interpreters = interpreterGroup.get(noteId);
      if (interpreters != null) {
        Iterator<Interpreter> it = interpreters.iterator();
        while (it.hasNext()) {
          Interpreter inp = it.next();
          if (inp.getClassName().equals(className)) {
            inp.close();
            it.remove();
            break;
          }
        }
      }
    }
  }


  @Override
  public RemoteInterpreterResult interpret(String noteId, String className, String st,
      RemoteInterpreterContext interpreterContext) throws TException {
    logger.debug("st: {}", st);
    Interpreter intp = getInterpreter(noteId, className);
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
          logger.info("Exception in RemoteInterpreterServer while interpret, jobListener.wait", e);
        }
      }
    }

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
      if (infos == null) {
        infos = new HashMap<>();
      }
      return infos;
    }

    @Override
    protected Object jobRun() throws Throwable {
      try {
        InterpreterContext.set(context);
        InterpreterResult result = interpreter.interpret(script, context);

        // data from context.out is prepended to InterpreterResult if both defined
        String message = "";

        context.out.flush();
        InterpreterResult.Type outputType = context.out.getType();
        byte[] interpreterOutput = context.out.toByteArray();
        context.out.clear();

        if (interpreterOutput != null && interpreterOutput.length > 0) {
          message = new String(interpreterOutput);
        }

        String interpreterResultMessage = result.message();

        InterpreterResult combinedResult;
        if (interpreterResultMessage != null && !interpreterResultMessage.isEmpty()) {
          message += interpreterResultMessage;
          combinedResult = new InterpreterResult(result.code(), result.type(), message);
        } else {
          combinedResult = new InterpreterResult(result.code(), outputType, message);
        }

        // put result into resource pool
        context.getResourcePool().put(
            context.getNoteId(),
            context.getParagraphId(),
            WellKnownResourceName.ParagraphResult.toString(),
            combinedResult);
        return combinedResult;
      } finally {
        InterpreterContext.remove();
      }
    }

    @Override
    protected boolean jobAbort() {
      return false;
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
      intp.cancel(convert(interpreterContext));
    }
  }

  @Override
  public int getProgress(String noteId, String className,
                         RemoteInterpreterContext interpreterContext)
      throws TException {
    Interpreter intp = getInterpreter(noteId, className);
    return intp.getProgress(convert(interpreterContext));
  }


  @Override
  public String getFormType(String noteId, String className) throws TException {
    Interpreter intp = getInterpreter(noteId, className);
    return intp.getFormType().toString();
  }

  @Override
  public List<String> completion(String noteId, String className, String buf, int cursor)
      throws TException {
    Interpreter intp = getInterpreter(noteId, className);
    return intp.completion(buf, cursor);
  }

  private InterpreterContext convert(RemoteInterpreterContext ric) {
    List<InterpreterContextRunner> contextRunners = new LinkedList<InterpreterContextRunner>();
    List<InterpreterContextRunner> runners = gson.fromJson(ric.getRunners(),
            new TypeToken<List<RemoteInterpreterContextRunner>>() {
        }.getType());

    for (InterpreterContextRunner r : runners) {
      contextRunners.add(new ParagraphRunner(this, r.getNoteId(), r.getParagraphId()));
    }

    return new InterpreterContext(
        ric.getNoteId(),
        ric.getParagraphId(),
        ric.getParagraphTitle(),
        ric.getParagraphText(),
        gson.fromJson(ric.getAuthenticationInfo(), AuthenticationInfo.class),
        (Map<String, Object>) gson.fromJson(ric.getConfig(),
            new TypeToken<Map<String, Object>>() {}.getType()),
        gson.fromJson(ric.getGui(), GUI.class),
        interpreterGroup.getAngularObjectRegistry(),
        interpreterGroup.getResourcePool(),
        contextRunners, createInterpreterOutput(ric.getNoteId(), ric.getParagraphId()));
  }


  private InterpreterOutput createInterpreterOutput(final String noteId, final String paragraphId) {
    return new InterpreterOutput(new InterpreterOutputListener() {
      @Override
      public void onAppend(InterpreterOutput out, byte[] line) {
        eventClient.onInterpreterOutputAppend(noteId, paragraphId, new String(line));
      }

      @Override
      public void onUpdate(InterpreterOutput out, byte[] output) {
        eventClient.onInterpreterOutputUpdate(noteId, paragraphId, new String(output));
      }
    });
  }


  static class ParagraphRunner extends InterpreterContextRunner {

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
  public String getStatus(String noteId, String jobId)
      throws TException {
    if (interpreterGroup == null) {
      return "Unknown";
    }

    synchronized (interpreterGroup) {
      List<Interpreter> interpreters = interpreterGroup.get(noteId);
      if (interpreters == null) {
        return "Unknown";
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
    return "Unknown";
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

    ResourceSet resourceSet = resourcePool.getAll(false);
    List<String> result = new LinkedList<String>();
    Gson gson = new Gson();

    for (Resource r : resourceSet) {
      result.add(gson.toJson(r));
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
}
