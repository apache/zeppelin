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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.thrift.TException;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.apache.zeppelin.interpreter.WrappedInterpreter;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterContext;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResult;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 *
 */
public class RemoteInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(RemoteInterpreter.class);
  Gson gson = new Gson();
  private String interpreterRunner;
  private String interpreterPath;
  private String className;
  FormType formType;
  boolean initialized;
  private Map<String, String> env;

  private int connectTimeout;

  public RemoteInterpreter(Properties property,
      String className,
      String interpreterRunner,
      String interpreterPath,
      int connectTimeout) {
    super(property);

    this.className = className;
    initialized = false;
    this.interpreterRunner = interpreterRunner;
    this.interpreterPath = interpreterPath;
    env = new HashMap<String, String>();
    this.connectTimeout = connectTimeout;
  }

  public RemoteInterpreter(Properties property,
      String className,
      String interpreterRunner,
      String interpreterPath,
      Map<String, String> env,
      int connectTimeout) {
    super(property);
    this.className = className;
    this.interpreterRunner = interpreterRunner;
    this.interpreterPath = interpreterPath;
    this.env = env;
    this.connectTimeout = connectTimeout;
  }

  @Override
  public String getClassName() {
    return className;
  }

  public RemoteInterpreterProcess getInterpreterProcess() {
    InterpreterGroup intpGroup = getInterpreterGroup();
    if (intpGroup == null) {
      return null;
    }

    synchronized (intpGroup) {
      if (intpGroup.getRemoteInterpreterProcess() == null) {
        // create new remote process
        RemoteInterpreterProcess remoteProcess = new RemoteInterpreterProcess(
                interpreterRunner, interpreterPath, env, connectTimeout);

        intpGroup.setRemoteInterpreterProcess(remoteProcess);
      }

      return intpGroup.getRemoteInterpreterProcess();
    }
  }

  private synchronized void init() {
    if (initialized == true) {
      return;
    }

    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    int rc = interpreterProcess.reference(getInterpreterGroup());

    synchronized (interpreterProcess) {
      // when first process created
      if (rc == 1) {
        // create all interpreter class in this interpreter group
        Client client = null;
        try {
          client = interpreterProcess.getClient();
        } catch (Exception e1) {
          throw new InterpreterException(e1);
        }

        boolean broken = false;
        try {
          for (Interpreter intp : this.getInterpreterGroup()) {
            logger.info("Create remote interpreter {}", intp.getClassName());
            client.createInterpreter(intp.getClassName(), (Map) property);

          }
        } catch (TException e) {
          broken = true;
          throw new InterpreterException(e);
        } finally {
          interpreterProcess.releaseClient(client, broken);
        }
      }
    }
    initialized = true;
  }



  @Override
  public void open() {
    init();
  }

  @Override
  public void close() {
    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    Client client = null;

    boolean broken = false;
    try {
      client = interpreterProcess.getClient();
      client.close(className);
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
    logger.debug("st: {}", st);
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
      GUI settings = context.getGui();
      RemoteInterpreterResult remoteResult = client.interpret(className, st, convert(context));

      Map<String, Object> remoteConfig = (Map<String, Object>) gson.fromJson(
          remoteResult.getConfig(), new TypeToken<Map<String, Object>>() {
          }.getType());
      context.getConfig().clear();
      context.getConfig().putAll(remoteConfig);

      if (form == FormType.NATIVE) {
        GUI remoteGui = gson.fromJson(remoteResult.getGui(), GUI.class);
        context.getGui().clear();
        context.getGui().setParams(remoteGui.getParams());
        context.getGui().setForms(remoteGui.getForms());
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
      client.cancel(className, convert(context));
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
      formType = FormType.valueOf(client.getFormType(className));
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
    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    boolean broken = false;
    try {
      return client.getProgress(className, convert(context));
    } catch (TException e) {
      broken = true;
      throw new InterpreterException(e);
    } finally {
      interpreterProcess.releaseClient(client, broken);
    }
  }


  @Override
  public List<String> completion(String buf, int cursor) {
    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    boolean broken = false;
    try {
      return client.completion(className, buf, cursor);
    } catch (TException e) {
      broken = true;
      throw new InterpreterException(e);
    } finally {
      interpreterProcess.releaseClient(client, broken);
    }
  }

  @Override
  public Scheduler getScheduler() {
    int maxConcurrency = 10;
    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    if (interpreterProcess == null) {
      return null;
    } else {
      return SchedulerFactory.singleton().createOrGetRemoteScheduler(
          "remoteinterpreter_" + interpreterProcess.hashCode(), interpreterProcess,
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
        ic.getParagraphTitle(),
        ic.getParagraphText(),
        gson.toJson(ic.getConfig()),
        gson.toJson(ic.getGui()),
        gson.toJson(ic.getRunners()));
  }

  private InterpreterResult convert(RemoteInterpreterResult result) {
    return new InterpreterResult(
        InterpreterResult.Code.valueOf(result.getCode()),
        Type.valueOf(result.getType()),
        result.getMsg());
  }
}
