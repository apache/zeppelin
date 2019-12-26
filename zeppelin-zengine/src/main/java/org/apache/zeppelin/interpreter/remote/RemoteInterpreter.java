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

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.thrift.TException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.interpreter.ConfInterpreter;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.LifecycleManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterContext;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResult;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResultMessage;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.RemoteScheduler;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Proxy for Interpreter instance that runs on separate process
 */
public class RemoteInterpreter extends Interpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteInterpreter.class);
  private static final Gson gson = new Gson();


  private String className;
  private String sessionId;
  private FormType formType;

  private RemoteInterpreterProcess interpreterProcess;
  private volatile boolean isOpened = false;
  private volatile boolean isCreated = false;

  private LifecycleManager lifecycleManager;

  /**
   * Remote interpreter and manage interpreter process
   */
  public RemoteInterpreter(Properties properties,
                           String sessionId,
                           String className,
                           String userName,
                           LifecycleManager lifecycleManager) {
    super(properties);
    this.sessionId = sessionId;
    this.className = className;
    this.setUserName(userName);
    this.lifecycleManager = lifecycleManager;
  }

  public boolean isOpened() {
    return isOpened;
  }

  @VisibleForTesting
  public void setOpened(boolean opened) {
    isOpened = opened;
  }

  @Override
  public String getClassName() {
    return className;
  }

  public String getSessionId() {
    return this.sessionId;
  }

  public synchronized RemoteInterpreterProcess getOrCreateInterpreterProcess() throws IOException {
    if (this.interpreterProcess != null) {
      return this.interpreterProcess;
    }
    ManagedInterpreterGroup intpGroup = getInterpreterGroup();
    this.interpreterProcess = intpGroup.getOrCreateInterpreterProcess(getUserName(), properties);
    return interpreterProcess;
  }

  public ManagedInterpreterGroup getInterpreterGroup() {
    return (ManagedInterpreterGroup) super.getInterpreterGroup();
  }

  @Override
  public void open() throws InterpreterException {
    synchronized (this) {
      if (!isOpened) {
        // create all the interpreters of the same session first, then Open the internal interpreter
        // of this RemoteInterpreter.
        // The why we we create all the interpreter of the session is because some interpreter
        // depends on other interpreter. e.g. PySparkInterpreter depends on SparkInterpreter.
        // also see method Interpreter.getInterpreterInTheSameSessionByClassName
        for (Interpreter interpreter : getInterpreterGroup()
                                        .getOrCreateSession(this.getUserName(), sessionId)) {
          try {
            if (!(interpreter instanceof ConfInterpreter)) {
              ((RemoteInterpreter) interpreter).internal_create();
            }
          } catch (IOException e) {
            throw new InterpreterException(e);
          }
        }

        interpreterProcess.callRemoteFunction(new RemoteInterpreterProcess.RemoteFunction<Void>() {
          @Override
          public Void call(Client client) throws Exception {
            LOGGER.info("Open RemoteInterpreter {}", getClassName());
            // open interpreter here instead of in the jobRun method in RemoteInterpreterServer
            // client.open(sessionId, className);
            // Push angular object loaded from JSON file to remote interpreter
            synchronized (getInterpreterGroup()) {
              if (!getInterpreterGroup().isAngularRegistryPushed()) {
                pushAngularObjectRegistryToRemote(client);
                getInterpreterGroup().setAngularRegistryPushed(true);
              }
            }
            return null;
          }
        });
        isOpened = true;
        this.lifecycleManager.onInterpreterUse(this.getInterpreterGroup(), sessionId);
      }
    }
  }

  private void internal_create() throws IOException {
    synchronized (this) {
      if (!isCreated) {
        this.interpreterProcess = getOrCreateInterpreterProcess();
        if (!interpreterProcess.isRunning()) {
          throw new IOException("Interpreter process is not running:\n" +
                  interpreterProcess.getErrorMessage());
        }
        interpreterProcess.callRemoteFunction(new RemoteInterpreterProcess.RemoteFunction<Void>() {
          @Override
          public Void call(Client client) throws Exception {
            LOGGER.info("Create RemoteInterpreter {}", getClassName());
            client.createInterpreter(getInterpreterGroup().getId(), sessionId,
                className, (Map) getProperties(), getUserName());
            return null;
          }
        });
        isCreated = true;
      }
    }
  }


  @Override
  public void close() throws InterpreterException {
    if (isOpened) {
      RemoteInterpreterProcess interpreterProcess = null;
      try {
        interpreterProcess = getOrCreateInterpreterProcess();
      } catch (IOException e) {
        throw new InterpreterException(e);
      }
      interpreterProcess.callRemoteFunction(new RemoteInterpreterProcess.RemoteFunction<Void>() {
        @Override
        public Void call(Client client) throws Exception {
          client.close(sessionId, className);
          return null;
        }
      });
      isOpened = false;
      this.lifecycleManager.onInterpreterUse(this.getInterpreterGroup(), sessionId);
    } else {
      LOGGER.warn("close is called when RemoterInterpreter is not opened for " + className);
    }
  }

  @Override
  public InterpreterResult interpret(final String st, final InterpreterContext context)
      throws InterpreterException {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("st:\n{}", st);
    }

    final FormType form = getFormType();
    RemoteInterpreterProcess interpreterProcess = null;
    try {
      interpreterProcess = getOrCreateInterpreterProcess();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
    if (!interpreterProcess.isRunning()) {
      throw new InterpreterException("Interpreter process is not running:\n" +
              interpreterProcess.getErrorMessage());
    }
    this.lifecycleManager.onInterpreterUse(this.getInterpreterGroup(), sessionId);
    return interpreterProcess.callRemoteFunction(
        new RemoteInterpreterProcess.RemoteFunction<InterpreterResult>() {
          @Override
          public InterpreterResult call(Client client) throws Exception {

            RemoteInterpreterResult remoteResult = client.interpret(
                sessionId, className, st, convert(context));
            Map<String, Object> remoteConfig = (Map<String, Object>) gson.fromJson(
                remoteResult.getConfig(), new TypeToken<Map<String, Object>>() {
                }.getType());
            context.getConfig().clear();
            if (remoteConfig != null) {
              context.getConfig().putAll(remoteConfig);
            }
            GUI currentGUI = context.getGui();
            GUI currentNoteGUI = context.getNoteGui();
            if (form == FormType.NATIVE) {
              GUI remoteGui = GUI.fromJson(remoteResult.getGui());
              GUI remoteNoteGui = GUI.fromJson(remoteResult.getNoteGui());
              currentGUI.clear();
              currentGUI.setParams(remoteGui.getParams());
              currentGUI.setForms(remoteGui.getForms());
              currentNoteGUI.setParams(remoteNoteGui.getParams());
              currentNoteGUI.setForms(remoteNoteGui.getForms());
            } else if (form == FormType.SIMPLE) {
              final Map<String, Input> currentForms = currentGUI.getForms();
              final Map<String, Object> currentParams = currentGUI.getParams();
              final GUI remoteGUI = GUI.fromJson(remoteResult.getGui());
              final Map<String, Input> remoteForms = remoteGUI.getForms();
              final Map<String, Object> remoteParams = remoteGUI.getParams();
              currentForms.putAll(remoteForms);
              currentParams.putAll(remoteParams);
            }

            InterpreterResult result = convert(remoteResult);
            return result;
          }
        }
    );

  }

  @Override
  public void cancel(final InterpreterContext context) throws InterpreterException {
    if (!isOpened) {
      LOGGER.warn("Cancel is called when RemoterInterpreter is not opened for " + className);
      return;
    }
    RemoteInterpreterProcess interpreterProcess = null;
    try {
      interpreterProcess = getOrCreateInterpreterProcess();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
    this.lifecycleManager.onInterpreterUse(this.getInterpreterGroup(), sessionId);
    interpreterProcess.callRemoteFunction(new RemoteInterpreterProcess.RemoteFunction<Void>() {
      @Override
      public Void call(Client client) throws Exception {
        client.cancel(sessionId, className, convert(context));
        return null;
      }
    });
  }

  @Override
  public FormType getFormType() throws InterpreterException {
    if (formType != null) {
      return formType;
    }

    // it is possible to call getFormType before it is opened
    synchronized (this) {
      if (!isOpened) {
        open();
      }
    }
    RemoteInterpreterProcess interpreterProcess = null;
    try {
      interpreterProcess = getOrCreateInterpreterProcess();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }

    this.lifecycleManager.onInterpreterUse(this.getInterpreterGroup(), sessionId);
    FormType type = interpreterProcess.callRemoteFunction(
        new RemoteInterpreterProcess.RemoteFunction<FormType>() {
          @Override
          public FormType call(Client client) throws Exception {
            formType = FormType.valueOf(client.getFormType(sessionId, className));
            return formType;
          }
        });
    return type;
  }


  @Override
  public int getProgress(final InterpreterContext context) throws InterpreterException {
    if (!isOpened) {
      LOGGER.warn("getProgress is called when RemoterInterpreter is not opened for " + className);
      return 0;
    }
    RemoteInterpreterProcess interpreterProcess = null;
    try {
      interpreterProcess = getOrCreateInterpreterProcess();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
    this.lifecycleManager.onInterpreterUse(this.getInterpreterGroup(), sessionId);
    return interpreterProcess.callRemoteFunction(
        new RemoteInterpreterProcess.RemoteFunction<Integer>() {
          @Override
          public Integer call(Client client) throws Exception {
            return client.getProgress(sessionId, className, convert(context));
          }
        });
  }


  @Override
  public List<InterpreterCompletion> completion(final String buf, final int cursor,
                                                final InterpreterContext interpreterContext)
      throws InterpreterException {
    if (!isOpened) {
      open();
    }
    RemoteInterpreterProcess interpreterProcess = null;
    try {
      interpreterProcess = getOrCreateInterpreterProcess();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
    this.lifecycleManager.onInterpreterUse(this.getInterpreterGroup(), sessionId);
    return interpreterProcess.callRemoteFunction(
        new RemoteInterpreterProcess.RemoteFunction<List<InterpreterCompletion>>() {
          @Override
          public List<InterpreterCompletion> call(Client client) throws Exception {
            return client.completion(sessionId, className, buf, cursor,
                convert(interpreterContext));
          }
        });
  }

  public String getStatus(final String jobId) {
    if (!isOpened) {
      LOGGER.warn("getStatus is called when RemoteInterpreter is not opened for " + className);
      return Job.Status.UNKNOWN.name();
    }
    RemoteInterpreterProcess interpreterProcess = null;
    try {
      interpreterProcess = getOrCreateInterpreterProcess();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    this.lifecycleManager.onInterpreterUse(this.getInterpreterGroup(), sessionId);
    return interpreterProcess.callRemoteFunction(
        new RemoteInterpreterProcess.RemoteFunction<String>() {
          @Override
          public String call(Client client) throws Exception {
            return client.getStatus(sessionId, jobId);
          }
        });
  }


  @Override
  public Scheduler getScheduler() {
    // one session own one Scheduler, so that when one session is closed, all the jobs/paragraphs
    // running under the scheduler of this session will be aborted.
    Scheduler s = new RemoteScheduler(
        RemoteInterpreter.class.getSimpleName() + "-" + getInterpreterGroup().getId() + "-" + sessionId,
        SchedulerFactory.singleton().getExecutor(),
        this);
    return SchedulerFactory.singleton().createOrGetScheduler(s);
  }

  private RemoteInterpreterContext convert(InterpreterContext ic) {
    return new RemoteInterpreterContext(ic.getNoteId(), ic.getNoteName(), ic.getParagraphId(),
        ic.getReplName(), ic.getParagraphTitle(), ic.getParagraphText(),
        gson.toJson(ic.getAuthenticationInfo()), gson.toJson(ic.getConfig()), ic.getGui().toJson(),
        gson.toJson(ic.getNoteGui()),
        ic.getLocalProperties());
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
   * call ONLY once when the first Interpreter is created
   */
  private void pushAngularObjectRegistryToRemote(Client client) throws TException {
    final AngularObjectRegistry angularObjectRegistry = this.getInterpreterGroup()
        .getAngularObjectRegistry();
    if (angularObjectRegistry != null && angularObjectRegistry.getRegistry() != null) {
      final Map<String, Map<String, AngularObject>> registry = angularObjectRegistry
          .getRegistry();
      LOGGER.info("Push local angular object registry from ZeppelinServer to" +
          " remote interpreter group {}", this.getInterpreterGroup().getId());
      final java.lang.reflect.Type registryType = new TypeToken<Map<String,
          Map<String, AngularObject>>>() {
      }.getType();
      client.angularRegistryPush(gson.toJson(registry, registryType));
    }
  }

  @Override
  public String toString() {
    return "RemoteInterpreter_" + className + "_" + sessionId;
  }
}
