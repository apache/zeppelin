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
import org.apache.zeppelin.interpreter.InterpreterProcessHeartbeatFailedException;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
public class RemoteInterpreterProcess implements ExecuteResultHandler {
  private static final Logger logger = LoggerFactory.getLogger(RemoteInterpreterProcess.class);

  private final AtomicInteger referenceCount;
  private DefaultExecutor executor;
  private ExecuteWatchdog watchdog;
  boolean running = false;
  boolean active = true;
  private int port = -1;
  private final String interpreterRunner;
  private final String interpreterDir;

  private GenericObjectPool<Client> clientPool;
  private Map<String, String> env;
  private final RemoteInterpreterEventPoller remoteInterpreterEventPoller;
  private final InterpreterContextRunnerPool interpreterContextRunnerPool;
  private int connectTimeout;

  private ScheduledExecutorService heartBeatSenderExecutorService;
  private ScheduledExecutorService heartBeatCheckerExecutorService;
  private AtomicLong lastHeartbeatTimestamp = new AtomicLong();

  public RemoteInterpreterProcess(String intpRunner,
      String intpDir,
      Map<String, String> env,
      int connectTimeout) {
    this(intpRunner, intpDir, env, new RemoteInterpreterEventPoller(), connectTimeout);
  }

  RemoteInterpreterProcess(String intpRunner,
      String intpDir,
      Map<String, String> env,
      RemoteInterpreterEventPoller remoteInterpreterEventPoller,
      int connectTimeout) {
    this.interpreterRunner = intpRunner;
    this.interpreterDir = intpDir;
    this.env = env;
    this.interpreterContextRunnerPool = new InterpreterContextRunnerPool();
    referenceCount = new AtomicInteger(0);
    this.remoteInterpreterEventPoller = remoteInterpreterEventPoller;
    this.connectTimeout = connectTimeout;

    initHeartbeatExecutorService();
  }

  private void initHeartbeatExecutorService() {
    this.heartBeatSenderExecutorService = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
        thread.setName("heartbeat-sender-thread");
        thread.setDaemon(true);
        return thread;
      }
    });

    this.heartBeatCheckerExecutorService = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
        thread.setName("heartbeat-checker-thread");
        thread.setDaemon(true);
        return thread;
      }
    });
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

        initializeHeartbeat();

        clientPool = new GenericObjectPool<Client>(new ClientFactory("localhost", port));

        remoteInterpreterEventPoller.setInterpreterGroup(interpreterGroup);
        remoteInterpreterEventPoller.setInterpreterProcess(this);
        remoteInterpreterEventPoller.start();
      }
      return referenceCount.incrementAndGet();
    }
  }

  public Client getClient() throws Exception {
    if (!active) {
      throw new InterpreterProcessHeartbeatFailedException(
          "Interpreter fails to respond heartbeat within time");
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

        shutdownHeartbeat();

        // first try shutdown
        Client client = null;
        try {
          client = getClient();
          client.shutdown();
        } catch (Exception e) {
          // safely ignore exception while client.shutdown() may terminates remote process
          logger.info("Exception in RemoteInterpreterProcess while synchronized dereference, can " +
              "safely ignore exception while client.shutdown() may terminates remote process", e);
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

  /**
   * Called when angular object is updated in client side to propagate
   * change to the remote process
   * @param name
   * @param o
   */
  public void updateRemoteAngularObject(String name, String noteId, Object o) {
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
      client.angularObjectUpdate(name, noteId, gson.toJson(o));
    } catch (TException e) {
      broken = true;
      logger.error("Can't update angular object", e);
    } finally {
      releaseClient(client, broken);
    }
  }

  public InterpreterContextRunnerPool getInterpreterContextRunnerPool() {
    return interpreterContextRunnerPool;
  }

  public boolean isActive() {
    return active;
  }

  private void initializeHeartbeat() {
    updateHeartbeatTimestamp();

    heartBeatSenderExecutorService.scheduleAtFixedRate(
        new InterpreterHeartbeatSenderTimerTask(), 1, 1, TimeUnit.SECONDS);
    heartBeatCheckerExecutorService.scheduleAtFixedRate(
        new InterpreterHeartbeatCheckerTimerTask(), 1, 1, TimeUnit.SECONDS);
  }

  private void shutdownHeartbeat() {
    this.heartBeatCheckerExecutorService.shutdownNow();
    this.heartBeatSenderExecutorService.shutdownNow();
  }

  private void updateHeartbeatTimestamp() {
    lastHeartbeatTimestamp.set(System.currentTimeMillis());
  }

  private long getLastHeartbeat() {
    return lastHeartbeatTimestamp.get();
  }

  private void setActive(boolean active) {
    this.active = active;
  }

  private class InterpreterHeartbeatSenderTimerTask extends TimerTask {
    @Override
    public void run() {
      // ping
      Client client = null;
      try {
        client = getClient();
      } catch (Exception e) {
        logger.error("Can't ping to interpreter process", e);
      }

      if (client != null) {
        try {
          client.ping();
          // succeed to ping
          updateHeartbeatTimestamp();
        } catch (Exception e) {
          logger.error("Can't ping to interpreter process", e);
        } finally {
          releaseClient(client);
        }
      }
    }
  }

  private class InterpreterHeartbeatCheckerTimerTask extends TimerTask {
    @Override
    public void run() {
      long currentTimestamp = System.currentTimeMillis();
      long lastHeartbeat = getLastHeartbeat();

      logger.debug("checking heartbeat : last heartbeat {} / current {}",
          lastHeartbeat, currentTimestamp);

      if (currentTimestamp - lastHeartbeat > connectTimeout) {
        // fail to respond heartbeat in time
        logger.error("Interpreter fails to respond heartbeat within time: last heartbeat {} " +
            " / current {}", lastHeartbeat, currentTimestamp);
        setActive(false);
      } else if (!active) {
        logger.info("Interpreter seems to be back to normal: last heartbeat {} / current {}",
            lastHeartbeat, currentTimestamp);
        setActive(true);
      }
    }
  }

}
