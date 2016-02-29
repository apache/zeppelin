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
import org.apache.commons.exec.*;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.thrift.TException;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public class RemoteInterpreterProcess implements ExecuteResultHandler {
  private static final Logger logger = LoggerFactory.getLogger(RemoteInterpreterProcess.class);

  private final AtomicInteger referenceCount;
  private DefaultExecutor executor;
  private ExecuteWatchdog watchdog;
  boolean running = false;
  private int port = -1;
  private final String interpreterRunner;
  private final String interpreterDir;
  private final String localRepoDir;

  private GenericObjectPool<Client> clientPool;
  private Map<String, String> env;
  private final RemoteInterpreterEventPoller remoteInterpreterEventPoller;
  private final InterpreterContextRunnerPool interpreterContextRunnerPool;
  private int connectTimeout;

  public RemoteInterpreterProcess(String intpRunner,
      String intpDir,
      String localRepoDir,
      Map<String, String> env,
      int connectTimeout,
      RemoteInterpreterProcessListener listener) {
    this(intpRunner,
        intpDir,
        localRepoDir,
        env,
        new RemoteInterpreterEventPoller(listener),
        connectTimeout);
  }

  RemoteInterpreterProcess(String intpRunner,
      String intpDir,
      String localRepoDir,
      Map<String, String> env,
      RemoteInterpreterEventPoller remoteInterpreterEventPoller,
      int connectTimeout) {
    this.interpreterRunner = intpRunner;
    this.interpreterDir = intpDir;
    this.localRepoDir = localRepoDir;
    this.env = env;
    this.interpreterContextRunnerPool = new InterpreterContextRunnerPool();
    referenceCount = new AtomicInteger(0);
    this.remoteInterpreterEventPoller = remoteInterpreterEventPoller;
    this.connectTimeout = connectTimeout;
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
        cmdLine.addArgument("-l", false);
        cmdLine.addArgument(localRepoDir, false);

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
        while (System.currentTimeMillis() - startTime < connectTimeout) {
          if (RemoteInterpreterUtils.checkIfRemoteEndpointAccessible("localhost", port)) {
            break;
          } else {
            try {
              Thread.sleep(500);
            } catch (InterruptedException e) {
              logger.error("Exception in RemoteInterpreterProcess while synchronized reference " +
                  "Thread.sleep", e);
            }
          }
        }

        clientPool = new GenericObjectPool<Client>(new ClientFactory("localhost", port));

        remoteInterpreterEventPoller.setInterpreterGroup(interpreterGroup);
        remoteInterpreterEventPoller.setInterpreterProcess(this);
        remoteInterpreterEventPoller.start();
      }
      return referenceCount.incrementAndGet();
    }
  }

  public Client getClient() throws Exception {
    if (clientPool == null || clientPool.isClosed()) {
      return null;
    }
    return clientPool.borrowObject();
  }

  public void releaseClient(Client client) {
    releaseClient(client, false);
  }

  public void releaseClient(Client client, boolean broken) {
    if (broken) {
      releaseBrokenClient(client);
    } else {
      try {
        clientPool.returnObject(client);
      } catch (Exception e) {
        logger.warn("exception occurred during releasing thrift client", e);
      }
    }
  }

  public void releaseBrokenClient(Client client) {
    try {
      clientPool.invalidateObject(client);
    } catch (Exception e) {
      logger.warn("exception occurred during releasing thrift client", e);
    }
  }

  public int dereference() {
    synchronized (referenceCount) {
      int r = referenceCount.decrementAndGet();
      if (r == 0) {
        logger.info("shutdown interpreter process");
        remoteInterpreterEventPoller.shutdown();

        // first try shutdown
        Client client = null;
        try {
          client = getClient();
          client.shutdown();
        } catch (Exception e) {
          // safely ignore exception while client.shutdown() may terminates remote process
          logger.info("Exception in RemoteInterpreterProcess while synchronized dereference, can " +
              "safely ignore exception while client.shutdown() may terminates remote process");
          logger.debug(e.getMessage(), e);
        } finally {
          if (client != null) {
            // no longer used
            releaseBrokenClient(client);
          }
        }

        clientPool.clear();
        clientPool.close();

        // wait for some time (connectTimeout) and force kill
        // remote process server.serve() loop is not always finishing gracefully
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < connectTimeout) {
          if (this.isRunning()) {
            try {
              Thread.sleep(500);
            } catch (InterruptedException e) {
              logger.error("Exception in RemoteInterpreterProcess while synchronized dereference " +
                  "Thread.sleep", e);
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

  public void setMaxPoolSize(int size) {
    if (clientPool != null) {
      //Size + 2 for progress poller , cancel operation
      clientPool.setMaxTotal(size + 2);
    }
  }

  public int getMaxPoolSize() {
    if (clientPool != null) {
      return clientPool.getMaxTotal();
    } else {
      return 0;
    }
  }

  /**
   * Called when angular object is updated in client side to propagate
   * change to the remote process
   * @param name
   * @param o
   */
  public void updateRemoteAngularObject(String name, String noteId, String paragraphId, Object o) {
    Client client = null;
    try {
      client = getClient();
    } catch (NullPointerException e) {
      // remote process not started
      logger.info("NullPointerException in RemoteInterpreterProcess while " +
          "updateRemoteAngularObject getClient, remote process not started", e);
      return;
    } catch (Exception e) {
      logger.error("Can't update angular object", e);
    }

    boolean broken = false;
    try {
      Gson gson = new Gson();
      client.angularObjectUpdate(name, noteId, paragraphId, gson.toJson(o));
    } catch (TException e) {
      broken = true;
      logger.error("Can't update angular object", e);
    } catch (NullPointerException e) {
      logger.error("Remote interpreter process not started", e);
      return;
    } finally {
      if (client != null) {
        releaseClient(client, broken);
      }
    }
  }

  public InterpreterContextRunnerPool getInterpreterContextRunnerPool() {
    return interpreterContextRunnerPool;
  }
}
