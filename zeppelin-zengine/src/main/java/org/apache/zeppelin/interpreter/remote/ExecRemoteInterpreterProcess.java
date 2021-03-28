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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.ExecuteException;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.zeppelin.interpreter.YarnAppMonitor;
import org.apache.zeppelin.interpreter.util.ProcessLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public class ExecRemoteInterpreterProcess extends RemoteInterpreterManagedProcess {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExecRemoteInterpreterProcess.class);

  private static final Pattern YARN_APP_PATTER = Pattern.compile("Submitted application (\\w+)");

  private final String interpreterRunner;
  private InterpreterProcessLauncher interpreterProcessLauncher;

  public ExecRemoteInterpreterProcess(
      int intpEventServerPort,
      String intpEventServerHost,
      String interpreterPortRange,
      String intpDir,
      String localRepoDir,
      Map<String, String> env,
      int connectTimeout,
      int connectionPoolSize,
      String interpreterSettingName,
      String interpreterGroupId,
      boolean isUserImpersonated,
      String intpRunner) {
    super(intpEventServerPort, intpEventServerHost, interpreterPortRange, intpDir, localRepoDir, env, connectTimeout,
        connectionPoolSize, interpreterSettingName, interpreterGroupId, isUserImpersonated);
    this.interpreterRunner = intpRunner;
  }

  @Override
  public void start(String userName) throws IOException {
    // start server process
    CommandLine cmdLine = CommandLine.parse(interpreterRunner);
    cmdLine.addArgument("-d", false);
    cmdLine.addArgument(getInterpreterDir(), false);
    cmdLine.addArgument("-c", false);
    cmdLine.addArgument(getIntpEventServerHost(), false);
    cmdLine.addArgument("-p", false);
    cmdLine.addArgument(String.valueOf(intpEventServerPort), false);
    cmdLine.addArgument("-r", false);
    cmdLine.addArgument(getInterpreterPortRange(), false);
    cmdLine.addArgument("-i", false);
    cmdLine.addArgument(getInterpreterGroupId(), false);
    if (isUserImpersonated() && !userName.equals("anonymous")) {
      cmdLine.addArgument("-u", false);
      cmdLine.addArgument(userName, false);
    }
    cmdLine.addArgument("-l", false);
    cmdLine.addArgument(getLocalRepoDir(), false);
    cmdLine.addArgument("-g", false);
    cmdLine.addArgument(getInterpreterSettingName(), false);

    interpreterProcessLauncher = new InterpreterProcessLauncher(cmdLine, getEnv());
    interpreterProcessLauncher.launch();
    interpreterProcessLauncher.waitForReady(getConnectTimeout());
    if (interpreterProcessLauncher.isLaunchTimeout()) {
      throw new IOException(
          String.format("Interpreter Process creation is time out in %d seconds", getConnectTimeout() / 1000) + "\n"
              + "You can increase timeout threshold via "
              + "setting zeppelin.interpreter.connect.timeout of this interpreter.\n"
              + interpreterProcessLauncher.getErrorMessage());
    }

    if (!interpreterProcessLauncher.isRunning()) {
      throw new IOException("Fail to launch interpreter process:\n" + interpreterProcessLauncher.getErrorMessage());
    } else {
      String launchOutput = interpreterProcessLauncher.getProcessLaunchOutput();
      Matcher m = YARN_APP_PATTER.matcher(launchOutput);
      if (m.find()) {
        String appId = m.group(1);
        LOGGER.info("Detected yarn app: {}, add it to YarnAppMonitor", appId);
        YarnAppMonitor.get().addYarnApp(ConverterUtils.toApplicationId(appId), this);
      }
    }
  }

  @Override
  public void processStarted(int port, String host) {
    super.processStarted(port, host);
    // for yarn cluster it may be transitioned from COMPLETED to RUNNING.
    interpreterProcessLauncher.onProcessRunning();
  }

  @Override
  public void stop() {
    if (isRunning()) {
      super.stop();
      // wait for a clean shutdown
      this.interpreterProcessLauncher.waitForShutdown(RemoteInterpreterServer.DEFAULT_SHUTDOWN_TIMEOUT + 500);
      // kill process
      this.interpreterProcessLauncher.stop();
      this.interpreterProcessLauncher = null;
      LOGGER.info("Remote exec process of interpreter group: {} is terminated", getInterpreterGroupId());
    } else {
      // Shutdown connection
      shutdown();
      LOGGER.warn("Try to stop a not running interpreter process of interpreter group: {}", getInterpreterGroupId());
    }
  }

  @VisibleForTesting
  public String getInterpreterRunner() {
    return interpreterRunner;
  }

  @Override
  public boolean isRunning() {
    return interpreterProcessLauncher != null && interpreterProcessLauncher.isRunning();
  }

  @Override
  public String getErrorMessage() {
    return this.interpreterProcessLauncher != null
        ? this.interpreterProcessLauncher.getErrorMessage()
        : "";
  }

  private class InterpreterProcessLauncher extends ProcessLauncher {

    public InterpreterProcessLauncher(CommandLine commandLine, Map<String, String> envs) {
      super(commandLine, envs);
    }

    public void waitForShutdown(int timeout) {
      synchronized (this) {
        long startTime = System.currentTimeMillis();
        long timeoutTime = startTime + timeout;
        while (state == State.RUNNING && !Thread.currentThread().isInterrupted()) {
          long timetoTimeout = timeoutTime - System.currentTimeMillis();
          if (timetoTimeout <= 0) {
            LOGGER.warn("Shutdown timeout reached");
            break;
          }
          try {
            wait(timetoTimeout);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("waitForShutdown interrupted", e);
          }
        }
      }
    }

    @Override
    public void waitForReady(int timeout) {
      synchronized (this) {
        long startTime = System.currentTimeMillis();
        long timeoutTime = startTime + timeout;
        // RUNNING means interpreter process notify zeppelin-server (onProcessRunning is called)
        // it is in RUNNING state.
        // TERMINATED means the launcher fail to launch interpreter process.
        while (state != State.RUNNING && state != State.TERMINATED
                && !Thread.currentThread().isInterrupted()) {
          long timetoTimeout = timeoutTime - System.currentTimeMillis();
          if (timetoTimeout <= 0) {
            LOGGER.warn("Ready timeout reached");
            break;
          }
          try {
            wait(timetoTimeout);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("waitForReady interrupted", e);
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
      synchronized (this) {
        notifyAll();
      }
    }

    @Override
    public void onProcessComplete(int exitValue) {
      LOGGER.warn("Process is exited with exit value {}", exitValue);
      if (isSparkYarnClusterMode()) {
        // don't call notify in yarn-cluster mode
        synchronized (this) {
          notifyAll();
        }
      } else if (isFlinkYarnApplicationMode() && exitValue == 0) {
        // Don't update transition state when flink launcher process exist
        // in yarn application mode.
        synchronized (this) {
          notifyAll();
        }
        return;
      }
      // For yarn-cluster mode, client process will exit with exit value 0
      // after submitting spark app. So don't move to TERMINATED state when exitValue
      // is 0.
      if (exitValue != 0) {
        transition(State.TERMINATED);
      } else {
        transition(State.COMPLETED);
      }
    }

    private boolean isSparkYarnClusterMode() {
      return Boolean.parseBoolean(
              getEnv().getOrDefault("ZEPPELIN_SPARK_YARN_CLUSTER", "false"));
    }

    private boolean isFlinkYarnApplicationMode() {
      return Boolean.parseBoolean(
              getEnv().getOrDefault("ZEPPELIN_FLINK_YARN_APPLICATION", "false"));
    }

    @Override
    public void onProcessFailed(ExecuteException e) {
      super.onProcessFailed(e);
      synchronized (this) {
        notifyAll();
      }
    }
  }
}
