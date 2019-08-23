/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.submarine.job.thread;

import com.google.common.io.Resources;
import com.hubspot.jinjava.Jinjava;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.submarine.commons.SubmarineConstants;
import org.apache.zeppelin.submarine.commons.SubmarineUI;
import org.apache.zeppelin.submarine.commons.SubmarineUtils;
import org.apache.zeppelin.submarine.job.SubmarineJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TensorboardRunThread extends Thread {
  private Logger LOGGER = LoggerFactory.getLogger(TensorboardRunThread.class);

  private SubmarineJob submarineJob;

  private AtomicBoolean running = new AtomicBoolean(false);

  private Lock lockRunning = new ReentrantLock();

  public TensorboardRunThread(SubmarineJob submarineJob) {
    this.submarineJob = submarineJob;
  }

  public void run() {
    SubmarineUI submarineUI = submarineJob.getSubmarineUI();

    boolean tryLock = lockRunning.tryLock();

    try {
      Properties properties = submarineJob.getProperties();
      String tensorboardName = SubmarineUtils.getTensorboardName(submarineJob.getUserName());
      if (true == running.get()) {
        String message = String.format("tensorboard %s already running.", tensorboardName);
        submarineUI.outputLog("WARN", message);
        LOGGER.warn(message);
        return;
      }
      running.set(true);

      HashMap jinjaParams = SubmarineUtils.propertiesToJinjaParams(
          properties, submarineJob, false);
      // update jobName -> tensorboardName
      jinjaParams.put(SubmarineConstants.JOB_NAME, tensorboardName);

      URL urlTemplate = Resources.getResource(SubmarineJob.SUBMARINE_TENSORBOARD_JINJA);
      String template = Resources.toString(urlTemplate, Charsets.UTF_8);
      Jinjava jinjava = new Jinjava();
      String submarineCmd = jinjava.render(template, jinjaParams);
      // If the first line is a newline, delete the newline
      int firstLineIsNewline = submarineCmd.indexOf("\n");
      if (firstLineIsNewline == 0) {
        submarineCmd = submarineCmd.replaceFirst("\n", "");
      }
      StringBuffer sbLogs = new StringBuffer(submarineCmd);
      submarineUI.outputLog("Submarine submit command", sbLogs.toString());

      long timeout = Long.valueOf(properties.getProperty(SubmarineJob.TIMEOUT_PROPERTY,
          SubmarineJob.defaultTimeout));
      CommandLine cmdLine = CommandLine.parse(SubmarineJob.shell);
      cmdLine.addArgument(submarineCmd, false);
      DefaultExecutor executor = new DefaultExecutor();
      ExecuteWatchdog watchDog = new ExecuteWatchdog(timeout);
      executor.setWatchdog(watchDog);
      StringBuffer sbLogOutput = new StringBuffer();
      executor.setStreamHandler(new PumpStreamHandler(new LogOutputStream() {
        @Override
        protected void processLine(String line, int level) {
          line = line.trim();
          if (!StringUtils.isEmpty(line)) {
            sbLogOutput.append(line + "\n");
          }
        }
      }));

      if (Boolean.valueOf(properties.getProperty(SubmarineJob.DIRECTORY_USER_HOME))) {
        executor.setWorkingDirectory(new File(System.getProperty("user.home")));
      }

      Map<String, String> env = new HashMap<>();
      String launchMode = (String) jinjaParams.get(SubmarineConstants.INTERPRETER_LAUNCH_MODE);
      if (StringUtils.equals(launchMode, "yarn")) {
        // Set environment variables in the container
        String javaHome, hadoopHome, hadoopConf;
        javaHome = (String) jinjaParams.get(SubmarineConstants.DOCKER_JAVA_HOME);
        hadoopHome = (String) jinjaParams.get(SubmarineConstants.DOCKER_HADOOP_HDFS_HOME);
        hadoopConf = (String) jinjaParams.get(SubmarineConstants.SUBMARINE_HADOOP_CONF_DIR);
        env.put("JAVA_HOME", javaHome);
        env.put("HADOOP_HOME", hadoopHome);
        env.put("HADOOP_HDFS_HOME", hadoopHome);
        env.put("HADOOP_CONF_DIR", hadoopConf);
        env.put("YARN_CONF_DIR", hadoopConf);
        env.put("CLASSPATH", "`$HADOOP_HDFS_HOME/bin/hadoop classpath --glob`");
      }

      LOGGER.info("Execute EVN: {}, Command: {} ", env.toString(), submarineCmd);

      AtomicBoolean cmdLineRunning = new AtomicBoolean(true);
      executor.execute(cmdLine, env, new DefaultExecuteResultHandler() {
        @Override
        public void onProcessComplete(int exitValue) {
          String message = String.format(
              "jobName %s ProcessComplete exit value is : %d", tensorboardName, exitValue);
          LOGGER.info(message);
          submarineUI.outputLog("TENSORBOARD RUN COMPLETE", message);
          cmdLineRunning.set(false);
        }
        @Override
        public void onProcessFailed(ExecuteException e) {
          String message = String.format(
              "jobName %s ProcessFailed exit value is : %d, exception is : %s",
              tensorboardName, e.getExitValue(), e.getMessage());
          LOGGER.error(message);
          submarineUI.outputLog("TENSORBOARD RUN FAILED", message);
          cmdLineRunning.set(false);
        }
      });
      int loopCount = 100;
      while ((loopCount-- > 0) && cmdLineRunning.get() && running.get()) {
        Thread.sleep(1000);
      }
      if (watchDog.isWatching()) {
        watchDog.destroyProcess();
        Thread.sleep(1000);
      }
      if (watchDog.isWatching()) {
        watchDog.killedProcess();
      }

      // Check if it has been submitted to YARN
      Map<String, Object> jobState = submarineJob.getJobStateByYarn(tensorboardName);
      loopCount = 50;
      while ((loopCount-- > 0) && !jobState.containsKey("state") && running.get()) {
        Thread.sleep(3000);
        jobState = submarineJob.getJobStateByYarn(tensorboardName);
      }

      if (!jobState.containsKey("state")) {
        String message
            = String.format("tensorboard %s was not submitted to YARN!", tensorboardName);
        LOGGER.error(message);
        submarineUI.outputLog("JOR RUN FAILED", message);
      }
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
      submarineUI.outputLog("Exception", e.getMessage());
    } finally {
      running.set(false);
      lockRunning.unlock();
    }
  }

  public void stopRunning() {
    try {
      running.set(false);

      // If can not get the lock, the thread is executed.
      boolean tryLock = lockRunning.tryLock();
      int loop = 0;
      while (false == tryLock && loop++ < 100) {
        LOGGER.warn("Can not get the TensorboardRunThread lock [{}] !", loop);
        Thread.sleep(500);
        tryLock = lockRunning.tryLock();
      }
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      lockRunning.unlock();
    }
  }
}
