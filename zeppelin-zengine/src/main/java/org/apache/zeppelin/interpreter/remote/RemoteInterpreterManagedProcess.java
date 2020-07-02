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
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.ExecuteException;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.zeppelin.interpreter.YarnAppMonitor;
import org.apache.zeppelin.interpreter.util.ProcessLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class manages start / stop of remote interpreter process
 */
public class RemoteInterpreterManagedProcess extends RemoteInterpreterProcess {
  private static final Logger LOGGER = LoggerFactory.getLogger(
      RemoteInterpreterManagedProcess.class);
  private static final Pattern YARN_APP_PATTER =
          Pattern.compile("Submitted application (\\w+)");

  private final String interpreterRunner;
  private final String interpreterPortRange;
  private InterpreterProcessLauncher interpreterProcessLauncher;
  private String host = null;
  private int port = -1;
  private final String interpreterDir;
  private final String localRepoDir;
  private final String interpreterSettingName;
  private final String interpreterGroupId;
  private final boolean isUserImpersonated;
  private String errorMessage;

  private Map<String, String> env;

  public RemoteInterpreterManagedProcess(
      String intpRunner,
      int intpEventServerPort,
      String intpEventServerHost,
      String interpreterPortRange,
      String intpDir,
      String localRepoDir,
      Map<String, String> env,
      int connectTimeout,
      String interpreterSettingName,
      String interpreterGroupId,
      boolean isUserImpersonated) {
    super(connectTimeout, intpEventServerHost, intpEventServerPort);
    this.interpreterRunner = intpRunner;
    this.interpreterPortRange = interpreterPortRange;
    this.env = env;
    this.interpreterDir = intpDir;
    this.localRepoDir = localRepoDir;
    this.interpreterSettingName = interpreterSettingName;
    this.interpreterGroupId = interpreterGroupId;
    this.isUserImpersonated = isUserImpersonated;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public void start(String userName) throws IOException {
    // start server process
    CommandLine cmdLine = CommandLine.parse(interpreterRunner);
    cmdLine.addArgument("-d", false);
    cmdLine.addArgument(interpreterDir, false);
    cmdLine.addArgument("-c", false);
    cmdLine.addArgument(intpEventServerHost, false);
    cmdLine.addArgument("-p", false);
    cmdLine.addArgument(String.valueOf(intpEventServerPort), false);
    cmdLine.addArgument("-r", false);
    cmdLine.addArgument(interpreterPortRange, false);
    cmdLine.addArgument("-i", false);
    cmdLine.addArgument(interpreterGroupId, false);
    if (isUserImpersonated && !userName.equals("anonymous")) {
      cmdLine.addArgument("-u", false);
      cmdLine.addArgument(userName, false);
    }
    cmdLine.addArgument("-l", false);
    cmdLine.addArgument(localRepoDir, false);
    cmdLine.addArgument("-g", false);
    cmdLine.addArgument(interpreterSettingName, false);

    interpreterProcessLauncher = new InterpreterProcessLauncher(cmdLine, env);
    interpreterProcessLauncher.launch();
    interpreterProcessLauncher.waitForReady(getConnectTimeout());
    if (interpreterProcessLauncher.isLaunchTimeout()) {
      throw new IOException(String.format("Interpreter Process creation is time out in %d seconds",
              getConnectTimeout()/1000) + "\n" + "You can increase timeout threshold via " +
              "setting zeppelin.interpreter.connect.timeout of this interpreter.\n" +
              interpreterProcessLauncher.getErrorMessage());
    }

    if (!interpreterProcessLauncher.isRunning()) {
      throw new IOException("Fail to launch interpreter process:\n" +
              interpreterProcessLauncher.getErrorMessage());
    } else {
      String launchOutput = interpreterProcessLauncher.getProcessLaunchOutput();
      Matcher m = YARN_APP_PATTER.matcher(launchOutput);
      if (m.find()) {
        String appId = m.group(1);
        LOGGER.info("Detected yarn app: " + appId + ", add it to YarnAppMonitor");
        YarnAppMonitor.get().addYarnApp(ConverterUtils.toApplicationId(appId), this);
      }
    }
  }

  public void stop() {
    if (isRunning()) {
      LOGGER.info("Kill interpreter process for interpreter group: {}", getInterpreterGroupId());
      try {
        callRemoteFunction(client -> {
          client.shutdown();
          return null;
        });
      } catch (Exception e) {
        LOGGER.warn("ignore the exception when shutting down", e);
      }

      // Shutdown connection
      shutdown();
      this.interpreterProcessLauncher.stop();
      this.interpreterProcessLauncher = null;
      LOGGER.info("Remote process of interpreter group: {} is terminated", getInterpreterGroupId());
    }
  }

  @Override
  public void processStarted(int port, String host) {
    this.port = port;
    this.host = host;
    // for yarn cluster it may be transitioned from COMPLETED to RUNNING.
    interpreterProcessLauncher.onProcessRunning();
  }

  // called when remote interpreter process is stopped, e.g. YarnAppsMonitor will call this
  // after detecting yarn app is killed/failed.
  public void processStopped(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  @VisibleForTesting
  public Map<String, String> getEnv() {
    return env;
  }

  @VisibleForTesting
  public String getLocalRepoDir() {
    return localRepoDir;
  }

  @VisibleForTesting
  public String getInterpreterDir() {
    return interpreterDir;
  }

  public String getInterpreterSettingName() {
    return interpreterSettingName;
  }

  @Override
  public String getInterpreterGroupId() {
    return interpreterGroupId;
  }

  @VisibleForTesting
  public String getInterpreterRunner() {
    return interpreterRunner;
  }

  @VisibleForTesting
  public boolean isUserImpersonated() {
    return isUserImpersonated;
  }

  public boolean isRunning() {
    return interpreterProcessLauncher != null && interpreterProcessLauncher.isRunning()
            && errorMessage == null;
  }

  @Override
  public String getErrorMessage() {
    String interpreterProcessError = this.interpreterProcessLauncher != null
            ? this.interpreterProcessLauncher.getErrorMessage() : "";
    return errorMessage != null ? errorMessage : interpreterProcessError;
  }

  private class InterpreterProcessLauncher extends ProcessLauncher {

    public InterpreterProcessLauncher(CommandLine commandLine,
                                      Map<String, String> envs) {
      super(commandLine, envs);
    }

    @Override
    public void waitForReady(int timeout) {
      synchronized (this) {
        if (state != State.RUNNING) {
          try {
            wait(timeout);
          } catch (InterruptedException e) {
            LOGGER.error("Remote interpreter is not accessible", e);
          }
        }
      }
      this.stopCatchLaunchOutput();
      if (state == State.LAUNCHED) {
        onTimeout();
      }
    }

    @Override
    public void onProcessRunning() {
      super.onProcessRunning();
      synchronized(this) {
        notify();
      }
    }

    @Override
    public void onProcessComplete(int exitValue) {
      LOGGER.warn("Process is exited with exit value " + exitValue);
      if (env.getOrDefault("ZEPPELIN_SPARK_YARN_CLUSTER", "false").equals("false")) {
        // don't call notify in yarn-cluster mode
        synchronized (this) {
          notify();
        }
      }
      // For yarn-cluster mode, client process will exit with exit value 0
      // after submitting spark app. So don't move to TERMINATED state when exitValue is 0.
      if (exitValue != 0) {
        transition(State.TERMINATED);
      } else {
        transition(State.COMPLETED);
      }
    }

    @Override
    public void onProcessFailed(ExecuteException e) {
      super.onProcessFailed(e);
      synchronized (this) {
        notify();
      }
    }
  }
}
