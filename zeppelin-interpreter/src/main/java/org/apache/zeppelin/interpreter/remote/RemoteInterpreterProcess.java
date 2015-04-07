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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.thrift.TException;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 *
 */
public class RemoteInterpreterProcess implements ExecuteResultHandler {
  Logger logger = LoggerFactory.getLogger(RemoteInterpreterProcess.class);
  AtomicInteger referenceCount;
  private DefaultExecutor executor;
  private ExecuteWatchdog watchdog;
  boolean running = false;
  int port = -1;
  private String interpreterRunner;
  private String interpreterDir;

  private GenericObjectPool<Client> clientPool;
  private Map<String, String> env;
  private RemoteInterpreterEventPoller remoteInterpreterEventPoller;
  private InterpreterContextRunnerPool interpreterContextRunnerPool;

  public RemoteInterpreterProcess(String intpRunner,
      String intpDir,
      Map<String, String> env,
      InterpreterContextRunnerPool interpreterContextRunnerPool) {
    this.interpreterRunner = intpRunner;
    this.interpreterDir = intpDir;
    this.env = env;
    this.interpreterContextRunnerPool = interpreterContextRunnerPool;
    referenceCount = new AtomicInteger(0);
  }

  public int getPort() {
    return port;
  }

  public int reference(InterpreterGroup interpreterGroup) {
    synchronized (referenceCount) {
      if (executor == null) {
        // start server process
        try {
          port = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
        } catch (IOException e1) {
          throw new InterpreterException(e1);
        }


        CommandLine cmdLine = CommandLine.parse(interpreterRunner);
        cmdLine.addArgument("-d", false);
        cmdLine.addArgument(interpreterDir, false);
        cmdLine.addArgument("-p", false);
        cmdLine.addArgument(Integer.toString(port), false);

        executor = new DefaultExecutor();

        watchdog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
        executor.setWatchdog(watchdog);

        running = true;
        try {
          Map procEnv = EnvironmentUtils.getProcEnvironment();
          procEnv.putAll(env);

          logger.info("Run interpreter process {}", cmdLine);
          executor.execute(cmdLine, procEnv, this);
        } catch (IOException e) {
          running = false;
          throw new InterpreterException(e);
        }


        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 5 * 1000) {
          if (RemoteInterpreterUtils.checkIfRemoteEndpointAccessible("localhost", port)) {
            break;
          } else {
            try {
              Thread.sleep(500);
            } catch (InterruptedException e) {
            }
          }
        }

        clientPool = new GenericObjectPool<Client>(new ClientFactory("localhost", port));

        remoteInterpreterEventPoller = new RemoteInterpreterEventPoller(interpreterGroup, this);
        remoteInterpreterEventPoller.start();
      }
      return referenceCount.incrementAndGet();
    }
  }

  public Client getClient() throws Exception {
    return clientPool.borrowObject();
  }

  public void releaseClient(Client client) {
    clientPool.returnObject(client);
  }

  public int dereference() {
    synchronized (referenceCount) {
      int r = referenceCount.decrementAndGet();
      if (r == 0) {
        logger.info("shutdown interpreter process");
        remoteInterpreterEventPoller.shutdown();

        // first try shutdown
        try {
          Client client = getClient();
          client.shutdown();
          releaseClient(client);
        } catch (Exception e) {
          logger.error("Error", e);
          watchdog.destroyProcess();
        }

        clientPool.clear();
        clientPool.close();

        // wait for 3 sec and force kill
        // remote process server.serve() loop is not always finishing gracefully
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 3 * 1000) {
          if (this.isRunning()) {
            try {
              Thread.sleep(500);
            } catch (InterruptedException e) {
            }
          } else {
            break;
          }
        }

        if (isRunning()) {
          logger.info("kill interpreter process");
          watchdog.destroyProcess();
        }

        executor = null;
        watchdog = null;
        running = false;
        logger.info("Remote process terminated");
      }
      return r;
    }
  }

  public int referenceCount() {
    synchronized (referenceCount) {
      return referenceCount.get();
    }
  }

  @Override
  public void onProcessComplete(int exitValue) {
    logger.info("Interpreter process exited {}", exitValue);
    running = false;

  }

  @Override
  public void onProcessFailed(ExecuteException e) {
    logger.info("Interpreter process failed {}", e);
    running = false;
  }

  public boolean isRunning() {
    return running;
  }

  public int getNumActiveClient() {
    if (clientPool == null) {
      return 0;
    } else {
      return clientPool.getNumActive();
    }
  }

  public int getNumIdleClient() {
    if (clientPool == null) {
      return 0;
    } else {
      return clientPool.getNumIdle();
    }
  }

  /**
   * Called when angular object is updated in client side to propagate
   * change to the remote process
   * @param name
   * @param o
   */
  public void updateRemoteAngularObject(String name, Object o) {
    Client client = null;
    try {
      client = getClient();
    } catch (NullPointerException e) {
      // remote process not started
      return;
    } catch (Exception e) {
      logger.error("Can't update angular object", e);
    }

    try {
      Gson gson = new Gson();
      client.angularObjectUpdate(name, gson.toJson(o));
    } catch (TException e) {
      logger.error("Can't update angular object", e);
    } finally {
      releaseClient(client);
    }
  }

  public InterpreterContextRunnerPool getInterpreterContextRunnerPool() {
    return interpreterContextRunnerPool;
  }
}
