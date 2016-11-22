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

import org.apache.commons.exec.*;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * This class manages start / stop of remote interpreter process
 */
public class RemoteInterpreterManagedProcess extends RemoteInterpreterProcess
    implements ExecuteResultHandler {
  private static final Logger logger = LoggerFactory.getLogger(
      RemoteInterpreterManagedProcess.class);
  private final String interpreterRunner;

  private DefaultExecutor executor;
  private ExecuteWatchdog watchdog;
  boolean running = false;
  private int port = -1;
  private final String interpreterDir;
  private final String localRepoDir;

  private Map<String, String> env;

  public RemoteInterpreterManagedProcess(
      String intpRunner,
      String intpDir,
      String localRepoDir,
      Map<String, String> env,
      int connectTimeout,
      RemoteInterpreterProcessListener listener,
      ApplicationEventListener appListener) {
    super(new RemoteInterpreterEventPoller(listener, appListener),
        connectTimeout);
    this.interpreterRunner = intpRunner;
    this.env = env;
    this.interpreterDir = intpDir;
    this.localRepoDir = localRepoDir;

  }

  RemoteInterpreterManagedProcess(String intpRunner,
                                  String intpDir,
                                  String localRepoDir,
                                  Map<String, String> env,
                                  RemoteInterpreterEventPoller remoteInterpreterEventPoller,
                                  int connectTimeout) {
    super(remoteInterpreterEventPoller,
        connectTimeout);
    this.interpreterRunner = intpRunner;
    this.env = env;
    this.interpreterDir = intpDir;
    this.localRepoDir = localRepoDir;
  }

  @Override
  public String getHost() {
    return "localhost";
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public void start(String userName, Boolean isUserImpersonate) {
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
    if (isUserImpersonate && !userName.equals("anonymous")) {
      cmdLine.addArgument("-u", false);
      cmdLine.addArgument(userName, false);
    }
    cmdLine.addArgument("-l", false);
    cmdLine.addArgument(localRepoDir, false);

    executor = new DefaultExecutor();
    executor.setStreamHandler(new PumpStreamHandler(new ProcessLogOutputStream(logger)));
    watchdog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
    executor.setWatchdog(watchdog);

    try {
      Map procEnv = EnvironmentUtils.getProcEnvironment();
      procEnv.putAll(env);

      logger.info("Run interpreter process {}", cmdLine);
      executor.execute(cmdLine, procEnv, this);
      running = true;
    } catch (IOException e) {
      running = false;
      throw new InterpreterException(e);
    }


    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < getConnectTimeout()) {
      try {
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
      } catch (Exception e) {
        if (logger.isDebugEnabled()) {
          logger.debug("Remote interpreter not yet accessible at localhost:" + port);
        }
      }
    }
  }

  public void stop() {
    if (isRunning()) {
      logger.info("kill interpreter process");
      watchdog.destroyProcess();
    }

    executor = null;
    watchdog = null;
    running = false;
    logger.info("Remote process terminated");
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

  private static class ProcessLogOutputStream extends LogOutputStream {

    private Logger logger;

    public ProcessLogOutputStream(Logger logger) {
      this.logger = logger;
    }

    @Override
    protected void processLine(String s, int i) {
      this.logger.debug(s);
    }
  }
}
