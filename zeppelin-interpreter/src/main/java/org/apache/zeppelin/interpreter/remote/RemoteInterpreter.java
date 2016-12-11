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

import java.util.*;

import org.apache.thrift.TException;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterContext;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResult;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResultMessage;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Proxy for Interpreter instance that runs on separate process
 */
public class RemoteInterpreter extends Interpreter {
  private final RemoteInterpreterProcessListener remoteInterpreterProcessListener;
  private final ApplicationEventListener applicationEventListener;
  Logger logger = LoggerFactory.getLogger(RemoteInterpreter.class);
  Gson gson = new Gson();
  private String interpreterRunner;
  private String interpreterPath;
  private String localRepoPath;
  private String className;
  private String sessionKey;
  FormType formType;
  boolean initialized;
  private Map<String, String> env;
  private int connectTimeout;
  private int maxPoolSize;
  private String host;
  private int port;
  private String userName;
  private Boolean isUserImpersonate;

  /**
   * Remote interpreter and manage interpreter process
   */
  public RemoteInterpreter(Properties property,
                           String sessionKey,
                           String className,
                           String interpreterRunner,
                           String interpreterPath,
                           String localRepoPath,
                           int connectTimeout,
                           int maxPoolSize,
                           RemoteInterpreterProcessListener remoteInterpreterProcessListener,
                           ApplicationEventListener appListener,
                           String userName,
                           Boolean isUserImpersonate) {
    super(property);
    this.sessionKey = sessionKey;
    this.className = className;
    initialized = false;
    this.interpreterRunner = interpreterRunner;
    this.interpreterPath = interpreterPath;
    this.localRepoPath = localRepoPath;
    env = getEnvFromInterpreterProperty(property);
    this.connectTimeout = connectTimeout;
    this.maxPoolSize = maxPoolSize;
    this.remoteInterpreterProcessListener = remoteInterpreterProcessListener;
    this.applicationEventListener = appListener;
    this.userName = userName;
    this.isUserImpersonate = isUserImpersonate;
  }


  /**
   * Connect to existing process
   */
  public RemoteInterpreter(
      Properties property,
      String sessionKey,
      String className,
      String host,
      int port,
      int connectTimeout,
      int maxPoolSize,
      RemoteInterpreterProcessListener remoteInterpreterProcessListener,
      ApplicationEventListener appListener,
      String userName,
      Boolean isUserImpersonate) {
    super(property);
    this.sessionKey = sessionKey;
    this.className = className;
    initialized = false;
    this.host = host;
    this.port = port;
    this.connectTimeout = connectTimeout;
    this.maxPoolSize = maxPoolSize;
    this.remoteInterpreterProcessListener = remoteInterpreterProcessListener;
    this.applicationEventListener = appListener;
    this.userName = userName;
    this.isUserImpersonate = isUserImpersonate;
  }


  // VisibleForTesting
  public RemoteInterpreter(
      Properties property,
      String sessionKey,
      String className,
      String interpreterRunner,
      String interpreterPath,
      String localRepoPath,
      Map<String, String> env,
      int connectTimeout,
      RemoteInterpreterProcessListener remoteInterpreterProcessListener,
      ApplicationEventListener appListener,
      String userName,
      Boolean isUserImpersonate) {
    super(property);
    this.className = className;
    this.sessionKey = sessionKey;
    this.interpreterRunner = interpreterRunner;
    this.interpreterPath = interpreterPath;
    this.localRepoPath = localRepoPath;
    env.putAll(getEnvFromInterpreterProperty(property));
    this.env = env;
    this.connectTimeout = connectTimeout;
    this.maxPoolSize = 10;
    this.remoteInterpreterProcessListener = remoteInterpreterProcessListener;
    this.applicationEventListener = appListener;
    this.userName = userName;
    this.isUserImpersonate = isUserImpersonate;
  }

  private Map<String, String> getEnvFromInterpreterProperty(Properties property) {
    Map<String, String> env = new HashMap<>();
    for (Object key : property.keySet()) {
      if (isEnvString((String) key)) {
        env.put((String) key, property.getProperty((String) key));
      }
    }
    return env;
  }

  static boolean isEnvString(String key) {
    if (key == null || key.length() == 0) {
      return false;
    }

    return key.matches("^[A-Z_0-9]*");
  }

  @Override
  public String getClassName() {
    return className;
  }

  private boolean connectToExistingProcess() {
    return host != null && port > 0;
  }

  public RemoteInterpreterProcess getInterpreterProcess() {
    InterpreterGroup intpGroup = getInterpreterGroup();
    if (intpGroup == null) {
      return null;
    }

    synchronized (intpGroup) {
      if (intpGroup.getRemoteInterpreterProcess() == null) {
        RemoteInterpreterProcess remoteProcess;
        if (connectToExistingProcess()) {
          remoteProcess = new RemoteInterpreterRunningProcess(
              connectTimeout,
              remoteInterpreterProcessListener,
              applicationEventListener,
              host,
              port);
        } else {
          // create new remote process
          remoteProcess = new RemoteInterpreterManagedProcess(
              interpreterRunner, interpreterPath, localRepoPath, env, connectTimeout,
              remoteInterpreterProcessListener, applicationEventListener);
        }

        intpGroup.setRemoteInterpreterProcess(remoteProcess);
      }

      return intpGroup.getRemoteInterpreterProcess();
    }
  }

  public synchronized void init() {
    if (initialized == true) {
      return;
    }

    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();

    final InterpreterGroup interpreterGroup = getInterpreterGroup();
    interpreterProcess.reference(interpreterGroup, userName, isUserImpersonate);
    interpreterProcess.setMaxPoolSize(
        Math.max(this.maxPoolSize, interpreterProcess.getMaxPoolSize()));
    String groupId = interpreterGroup.getId();

    synchronized (interpreterProcess) {
      Client client = null;
      try {
        client = interpreterProcess.getClient();
      } catch (Exception e1) {
        throw new InterpreterException(e1);
      }

      boolean broken = false;
      try {
        logger.info("Create remote interpreter {}", getClassName());
        if (localRepoPath != null) {
          property.put("zeppelin.interpreter.localRepo", localRepoPath);
        }
        client.createInterpreter(groupId, sessionKey,
          getClassName(), (Map) property, userName);
        // Push angular object loaded from JSON file to remote interpreter
        if (!interpreterGroup.isAngularRegistryPushed()) {
          pushAngularObjectRegistryToRemote(client);
          interpreterGroup.setAngularRegistryPushed(true);
        }

      } catch (TException e) {
        logger.error("Failed to create interpreter: {}", getClassName());
        throw new InterpreterException(e);
      } finally {
        // TODO(jongyoul): Fixed it when not all of interpreter in same interpreter group are broken
        interpreterProcess.releaseClient(client, broken);
      }
    }
    initialized = true;
  }



  @Override
  public void open() {
    InterpreterGroup interpreterGroup = getInterpreterGroup();

    synchronized (interpreterGroup) {
      // initialize all interpreters in this interpreter group
      List<Interpreter> interpreters = interpreterGroup.get(sessionKey);
      for (Interpreter intp : new ArrayList<>(interpreters)) {
        Interpreter p = intp;
        while (p instanceof WrappedInterpreter) {
          p = ((WrappedInterpreter) p).getInnerInterpreter();
        }
        try {
          ((RemoteInterpreter) p).init();
        } catch (InterpreterException e) {
          logger.error("Failed to initialize interpreter: {}. Remove it from interpreterGroup",
              p.getClassName());
          interpreters.remove(p);
        }
      }
    }
  }

  @Override
  public void close() {
    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();

    Client client = null;
    boolean broken = false;
    try {
      client = interpreterProcess.getClient();
      if (client != null) {
        client.close(sessionKey, className);
      }
    } catch (TException e) {
      broken = true;
      throw new InterpreterException(e);
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    } finally {
      if (client != null) {
        interpreterProcess.releaseClient(client, broken);
      }
      getInterpreterProcess().dereference();
    }
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    if (logger.isDebugEnabled()) {
      logger.debug("st:\n{}", st);
    }

    FormType form = getFormType();
    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    InterpreterContextRunnerPool interpreterContextRunnerPool = interpreterProcess
        .getInterpreterContextRunnerPool();

    List<InterpreterContextRunner> runners = context.getRunners();
    if (runners != null && runners.size() != 0) {
      // assume all runners in this InterpreterContext have the same note id
      String noteId = runners.get(0).getNoteId();

      interpreterContextRunnerPool.clear(noteId);
      interpreterContextRunnerPool.addAll(noteId, runners);
    }

    boolean broken = false;
    try {

      final GUI currentGUI = context.getGui();
      RemoteInterpreterResult remoteResult = client.interpret(
          sessionKey, className, st, convert(context));

      Map<String, Object> remoteConfig = (Map<String, Object>) gson.fromJson(
          remoteResult.getConfig(), new TypeToken<Map<String, Object>>() {
          }.getType());
      context.getConfig().clear();
      context.getConfig().putAll(remoteConfig);


      if (form == FormType.NATIVE) {
        GUI remoteGui = gson.fromJson(remoteResult.getGui(), GUI.class);
        currentGUI.clear();
        currentGUI.setParams(remoteGui.getParams());
        currentGUI.setForms(remoteGui.getForms());
      } else if (form == FormType.SIMPLE) {
        final Map<String, Input> currentForms = currentGUI.getForms();
        final Map<String, Object> currentParams = currentGUI.getParams();
        final GUI remoteGUI = gson.fromJson(remoteResult.getGui(), GUI.class);
        final Map<String, Input> remoteForms = remoteGUI.getForms();
        final Map<String, Object> remoteParams = remoteGUI.getParams();
        currentForms.putAll(remoteForms);
        currentParams.putAll(remoteParams);
      }

      InterpreterResult result = convert(remoteResult);
      return result;
    } catch (TException e) {
      broken = true;
      throw new InterpreterException(e);
    } finally {
      interpreterProcess.releaseClient(client, broken);
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    boolean broken = false;
    try {
      client.cancel(sessionKey, className, convert(context));
    } catch (TException e) {
      broken = true;
      throw new InterpreterException(e);
    } finally {
      interpreterProcess.releaseClient(client, broken);
    }
  }


  @Override
  public FormType getFormType() {
    init();

    if (formType != null) {
      return formType;
    }

    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    boolean broken = false;
    try {
      formType = FormType.valueOf(client.getFormType(sessionKey, className));
      return formType;
    } catch (TException e) {
      broken = true;
      throw new InterpreterException(e);
    } finally {
      interpreterProcess.releaseClient(client, broken);
    }
  }

  @Override
  public int getProgress(InterpreterContext context) {
    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    if (interpreterProcess == null || !interpreterProcess.isRunning()) {
      return 0;
    }

    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    boolean broken = false;
    try {
      return client.getProgress(sessionKey, className, convert(context));
    } catch (TException e) {
      broken = true;
      throw new InterpreterException(e);
    } finally {
      interpreterProcess.releaseClient(client, broken);
    }
  }


  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    boolean broken = false;
    try {
      List completion = client.completion(sessionKey, className, buf, cursor);
      return completion;
    } catch (TException e) {
      broken = true;
      throw new InterpreterException(e);
    } finally {
      interpreterProcess.releaseClient(client, broken);
    }
  }

  @Override
  public Scheduler getScheduler() {
    int maxConcurrency = maxPoolSize;
    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    if (interpreterProcess == null) {
      return null;
    } else {
      return SchedulerFactory.singleton().createOrGetRemoteScheduler(
          RemoteInterpreter.class.getName() + sessionKey + interpreterProcess.hashCode(),
          sessionKey,
          interpreterProcess,
          maxConcurrency);
    }
  }

  private String getInterpreterGroupKey(InterpreterGroup interpreterGroup) {
    return interpreterGroup.getId();
  }

  private RemoteInterpreterContext convert(InterpreterContext ic) {
    return new RemoteInterpreterContext(
        ic.getNoteId(),
        ic.getParagraphId(),
        ic.getReplName(),
        ic.getParagraphTitle(),
        ic.getParagraphText(),
        gson.toJson(ic.getAuthenticationInfo()),
        gson.toJson(ic.getConfig()),
        gson.toJson(ic.getGui()),
        gson.toJson(ic.getRunners()));
  }

  private InterpreterResult convert(RemoteInterpreterResult result) {
    InterpreterResult r = new InterpreterResult(
        InterpreterResult.Code.valueOf(result.getCode()));

    for (RemoteInterpreterResultMessage m : result.getMsg()) {
      r.add(InterpreterResult.Type.valueOf(m.getType()), m.getData());
    }

    return r;
  }

  /**
   * Push local angular object registry to
   * remote interpreter. This method should be
   * call ONLY inside the init() method
   * @param client
   * @throws TException
   */
  void pushAngularObjectRegistryToRemote(Client client) throws TException {
    final AngularObjectRegistry angularObjectRegistry = this.getInterpreterGroup()
            .getAngularObjectRegistry();

    if (angularObjectRegistry != null && angularObjectRegistry.getRegistry() != null) {
      final Map<String, Map<String, AngularObject>> registry = angularObjectRegistry
              .getRegistry();

      logger.info("Push local angular object registry from ZeppelinServer to" +
              " remote interpreter group {}", this.getInterpreterGroup().getId());

      final java.lang.reflect.Type registryType = new TypeToken<Map<String,
              Map<String, AngularObject>>>() {}.getType();

      Gson gson = new Gson();
      client.angularRegistryPush(gson.toJson(registry, registryType));
    }
  }

  public Map<String, String> getEnv() {
    return env;
  }

  public void setEnv(Map<String, String> env) {
    this.env = env;
  }

  public void addEnv(Map<String, String> env) {
    if (this.env == null) {
      this.env = new HashMap<>();
    }
    this.env.putAll(env);
  }
}
