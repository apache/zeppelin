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

package org.apache.zeppelin.submarine.job;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.fs.Path;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.submarine.hadoop.HdfsClient;
import org.apache.zeppelin.submarine.job.thread.JobRunThread;
import org.apache.zeppelin.submarine.commons.SubmarineCommand;
import org.apache.zeppelin.submarine.commons.SubmarineConstants;
import org.apache.zeppelin.submarine.commons.SubmarineUI;
import org.apache.zeppelin.submarine.commons.SubmarineUtils;
import org.apache.zeppelin.submarine.job.thread.TensorboardRunThread;
import org.apache.zeppelin.submarine.hadoop.FinalApplicationStatus;
import org.apache.zeppelin.submarine.hadoop.YarnApplicationState;
import org.apache.zeppelin.submarine.hadoop.YarnClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.zeppelin.submarine.commons.SubmarineConstants.JOB_STATUS;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.TENSORBOARD_URL;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.TF_TENSORBOARD_ENABLE;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.YARN_APPLICATION_FINAL_STATUS;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.YARN_APPLICATION_ID;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.YARN_APPLICATION_NAME;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.YARN_APPLICATION_STATUS;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.YARN_APPLICATION_URL;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.YARN_APP_ELAPSED_TIME;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.YARN_APP_FINAL_STATUS_NAME;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.YARN_APP_FINISHED_TIME;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.YARN_APP_LAUNCHTIME_NAME;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.YARN_APP_LAUNCH_TIME;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.YARN_APP_STARTEDTIME_NAME;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.YARN_APP_STARTED_TIME;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.YARN_APP_STATE_NAME;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.YARN_TENSORBOARD_URL;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.YARN_WEB_HTTP_ADDRESS;
import static org.apache.zeppelin.submarine.job.SubmarineJobStatus.EXECUTE_SUBMARINE;

public class SubmarineJob extends Thread {

  private Logger LOGGER = LoggerFactory.getLogger(SubmarineJob.class);

  private AtomicBoolean running = new AtomicBoolean(true);

  private static final long SYNC_SUBMARINE_RUNTIME_CYCLE = 3000;

  private YarnClient yarnClient = null;

  private SubmarineUI submarineUI = null;

  private Properties properties = null;

  private HdfsClient hdfsClient = null;

  private File pythonWorkDir = null;

  private String noteId = null;
  private String noteName = null;
  private String userName = null;
  private String applicationId = null;
  private YarnApplicationState yarnApplicationState = null;
  private FinalApplicationStatus finalApplicationStatus = null;
  private long startTime = 0;
  private long launchTime = 0;
  private long finishTime = 0;
  private float progress = 0; // [0 ~ 100]
  private SubmarineJobStatus currentJobStatus = EXECUTE_SUBMARINE;

  private InterpreterContext intpContext = null;

  JobRunThread jobRunThread = null;
  TensorboardRunThread tensorboardRunThread = null;

  public static final String DIRECTORY_USER_HOME = "shell.working.directory.userName.home";
  private static final boolean isWindows = System.getProperty("os.name").startsWith("Windows");
  public static final String shell = isWindows ? "cmd /c" : "bash -c";
  public static final String TIMEOUT_PROPERTY = "submarine.command.timeout.millisecond";
  public static final String defaultTimeout = "100000";

  public static final String SUBMARINE_JOBRUN_TF_JINJA
      = "jinja_templates/submarine-job-run-tf.jinja";
  public static final String SUBMARINE_COMMAND_JINJA
      = "jinja_templates/submarine-command.jinja";
  public static final String SUBMARINE_TENSORBOARD_JINJA
      = "jinja_templates/submarine-tensorboard.jinja";

  public SubmarineJob(InterpreterContext context, Properties properties) {
    this.intpContext = context;
    this.properties = properties;
    this.noteId = context.getNoteId();
    this.noteName = context.getNoteName();
    this.userName = context.getAuthenticationInfo().getUser();
    this.yarnClient = new YarnClient(properties);
    this.hdfsClient = new HdfsClient(properties);
    this.submarineUI = new SubmarineUI(intpContext);

    this.start();
  }

  // 1. Synchronize submarine runtime state
  @Override
  public void run() {
    while (running.get()) {
      String jobName = SubmarineUtils.getJobName(userName, noteId);
      updateJobStateByYarn(jobName);

      getTensorboardStatus();

      try {
        Thread.sleep(SYNC_SUBMARINE_RUNTIME_CYCLE);
      } catch (InterruptedException e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
  }

  @VisibleForTesting
  public boolean getRunning() {
    return running.get();
  }

  // Stop SubmarineJob
  public void stopRunning() {
    running.set(false);

    // stop JobRunThread
    if (null != jobRunThread && jobRunThread.isAlive()) {
      jobRunThread.stopRunning();
    }

    // stop TensorboardRunThread
    if (null != tensorboardRunThread && tensorboardRunThread.isAlive()) {
      tensorboardRunThread.stopRunning();
    }
  }

  public String getUserTensorboardPath() {
    String tfCheckpointPath = properties.getProperty(SubmarineConstants.TF_CHECKPOINT_PATH, "");
    return tfCheckpointPath;
  }

  public String getJobDefaultCheckpointPath() {
    String userTensorboardPath = getUserTensorboardPath();
    return userTensorboardPath + "/" + noteId;
  }

  public void cleanJobDefaultCheckpointPath() {
    String jobCheckpointPath = getJobDefaultCheckpointPath();
    Path notePath = new Path(jobCheckpointPath);
    if (notePath.depth() <= 3) {
      submarineUI.outputLog("ERROR", "Checkpoint path depth must be greater than 3");
      return;
    }
    try {
      String message = "Clean up the checkpoint directory: " + jobCheckpointPath;
      submarineUI.outputLog("", message);
      hdfsClient.delete(notePath);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  public Properties getProperties() {
    return properties;
  }

  public HdfsClient getHdfsClient() {
    return hdfsClient;
  }

  public SubmarineUI getSubmarineUI() {
    return submarineUI;
  }

  public void setPythonWorkDir(File pythonWorkDir) {
    this.pythonWorkDir = pythonWorkDir;
  }

  public File getPythonWorkDir() {
    return this.pythonWorkDir;
  }

  public void onDashboard() {
    submarineUI.createSubmarineUI(SubmarineCommand.DASHBOARD);
  }

  public void runJob() {
    // Need to display the UI when the page is reloaded, don't create it in the thread
    submarineUI.createSubmarineUI(SubmarineCommand.JOB_RUN);
    submarineUI.createLogHeadUI();

    // Check if job already exists
    String jobName = SubmarineUtils.getJobName(userName, noteId);
    Map<String, Object> mapAppStatus = getJobStateByYarn(jobName);
    if (mapAppStatus.size() == 0) {
      if (null == jobRunThread || !jobRunThread.isAlive()) {
        jobRunThread = new JobRunThread(this);
        jobRunThread.start();
      } else {
        submarineUI.outputLog("INFO", "JOB " + jobName + " being start up.");
      }
    } else {
      submarineUI.outputLog("INFO", "JOB " + jobName + " already running.");
    }
  }

  public void deleteJob(String serviceName) {
    submarineUI.createSubmarineUI(SubmarineCommand.JOB_STOP);
    yarnClient.deleteService(serviceName);
  }

  public void runTensorBoard() {
    submarineUI.createSubmarineUI(SubmarineCommand.TENSORBOARD_RUN);
    submarineUI.createLogHeadUI();

    String tensorboardName = SubmarineUtils.getTensorboardName(userName);
    Map<String, Object> mapAppStatus = getJobStateByYarn(tensorboardName);
    if (mapAppStatus.size() == 0) {
      if (null == tensorboardRunThread || !tensorboardRunThread.isAlive()) {
        tensorboardRunThread = new TensorboardRunThread(this);
        tensorboardRunThread.start();
      } else {
        submarineUI.outputLog("INFO", "Tensorboard being start up.");
      }
    } else {
      submarineUI.outputLog("INFO", "Tensorboard already running.");
    }
  }

  // Check if tensorboard already exists
  public boolean getTensorboardStatus() {
    String enableTensorboard = properties.getProperty(TF_TENSORBOARD_ENABLE, "false");
    boolean tensorboardExist = false;
    if (StringUtils.equals(enableTensorboard, "true")) {
      String tensorboardName = SubmarineUtils.getTensorboardName(userName);

      // create tensorboard link of YARN
      Map<String, Object> mapAppStatus = getJobStateByYarn(tensorboardName);
      String appId = "";
      if (mapAppStatus.containsKey(YARN_APPLICATION_ID)) {
        appId = mapAppStatus.get(YARN_APPLICATION_ID).toString();
        StringBuffer sbUrl = new StringBuffer();
        String yarnBaseUrl = properties.getProperty(YARN_WEB_HTTP_ADDRESS, "");
        sbUrl.append(yarnBaseUrl).append("/ui2/#/yarn-app/").append(appId);
        sbUrl.append("/components?service=").append(tensorboardName);
        SubmarineUtils.setAgulObjValue(intpContext, YARN_TENSORBOARD_URL, sbUrl.toString());

        // Detection tensorboard Container export port
        List<Map<String, Object>> listExportPorts = yarnClient.getAppExportPorts(tensorboardName);
        for (Map<String, Object> exportPorts : listExportPorts) {
          if (exportPorts.containsKey(YarnClient.HOST_IP)
              && exportPorts.containsKey(YarnClient.HOST_PORT)
              && exportPorts.containsKey(YarnClient.CONTAINER_PORT)) {
            String intpAppHostIp = (String) exportPorts.get(YarnClient.HOST_IP);
            String intpAppHostPort = (String) exportPorts.get(YarnClient.HOST_PORT);
            String intpAppContainerPort = (String) exportPorts.get(YarnClient.CONTAINER_PORT);
            if (StringUtils.equals("6006", intpAppContainerPort)) {
              tensorboardExist = true;

              if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Detection tensorboard Container hostIp:{}, hostPort:{}, " +
                    "containerPort:{}.", intpAppHostIp, intpAppHostPort, intpAppContainerPort);
              }

              // show tensorboard link button
              String tensorboardUrl = "http://" + intpAppHostIp + ":" + intpAppHostPort;
              SubmarineUtils.setAgulObjValue(intpContext, TENSORBOARD_URL, tensorboardUrl);
              break;
            }
          }
        }
      } else {
        SubmarineUtils.removeAgulObjValue(intpContext, YARN_TENSORBOARD_URL);
      }

      if (false == tensorboardExist) {
        SubmarineUtils.removeAgulObjValue(intpContext, TENSORBOARD_URL);
      }
    }

    return tensorboardExist;
  }

  public void showUsage() {
    submarineUI.createSubmarineUI(SubmarineCommand.USAGE);
  }

  public void cleanRuntimeCache() {
    intpContext.getAngularObjectRegistry().removeAll(noteId, intpContext.getParagraphId());
    submarineUI.createSubmarineUI(SubmarineCommand.DASHBOARD);
  }

  public String getNoteId() {
    return noteId;
  }

  public String getUserName() {
    return this.userName;
  }

  // from state to state
  public void setCurrentJobState(SubmarineJobStatus toStatus) {
    SubmarineUtils.setAgulObjValue(intpContext, JOB_STATUS,
        toStatus.getStatus());
    currentJobStatus = toStatus;
  }

  public Map<String, Object> getJobStateByYarn(String jobName) {
    Map<String, Object> mapAppStatus = new HashMap<>();
    Map<String, Object> mapStatus = yarnClient.getAppServices(jobName);

    if (mapStatus.containsKey(YARN_APPLICATION_ID)
        && mapStatus.containsKey(YARN_APPLICATION_NAME)) {
      String appId = mapStatus.get(YARN_APPLICATION_ID).toString();
      mapAppStatus = yarnClient.getClusterApps(appId);

      mapAppStatus.putAll(mapStatus);
    }

    return mapAppStatus;
  }

  public void updateJobStateByYarn(String appName) {
    Map<String, Object> mapAppStatus = getJobStateByYarn(appName);

    if (mapAppStatus.size() == 0) {
      SubmarineUtils.removeAgulObjValue(intpContext, YARN_APPLICATION_ID);
      SubmarineUtils.removeAgulObjValue(intpContext, YARN_APPLICATION_STATUS);
      SubmarineUtils.removeAgulObjValue(intpContext, YARN_APPLICATION_URL);
      SubmarineUtils.removeAgulObjValue(intpContext, YARN_APP_STARTED_TIME);
      SubmarineUtils.removeAgulObjValue(intpContext, YARN_APP_LAUNCH_TIME);
      SubmarineUtils.removeAgulObjValue(intpContext, YARN_APP_FINISHED_TIME);
      SubmarineUtils.removeAgulObjValue(intpContext, YARN_APP_ELAPSED_TIME);

      // TODO(Xun Liu) Not wait job run ???
      SubmarineUtils.removeAgulObjValue(intpContext, JOB_STATUS);
    } else {
      String state = "", finalStatus = "", appId = "";
      if (mapAppStatus.containsKey(YARN_APPLICATION_ID)) {
        appId = mapAppStatus.get(YARN_APPLICATION_ID).toString();
      }
      if (mapAppStatus.containsKey(YARN_APP_STATE_NAME)) {
        state = mapAppStatus.get(YARN_APP_STATE_NAME).toString();
        SubmarineUtils.setAgulObjValue(intpContext, YARN_APPLICATION_STATUS, state);
      }
      if (mapAppStatus.containsKey(YARN_APP_FINAL_STATUS_NAME)) {
        finalStatus = mapAppStatus.get(YARN_APP_FINAL_STATUS_NAME).toString();
        SubmarineUtils.setAgulObjValue(intpContext,
            YARN_APPLICATION_FINAL_STATUS, finalStatus);
      }
      SubmarineJobStatus jobStatus = convertYarnState(state, finalStatus);
      setCurrentJobState(jobStatus);
      try {
        if (mapAppStatus.containsKey(YARN_APP_STARTEDTIME_NAME)) {
          String startedTime = mapAppStatus.get(YARN_APP_STARTEDTIME_NAME).toString();
          long lStartedTime = Long.parseLong(startedTime);
          if (lStartedTime > 0) {
            Date startedDate = new Date(lStartedTime);
            SubmarineUtils.setAgulObjValue(intpContext, YARN_APP_STARTED_TIME,
                startedDate.toString());
          }
        }
        if (mapAppStatus.containsKey(YARN_APP_LAUNCHTIME_NAME)) {
          String launchTime = mapAppStatus.get(YARN_APP_LAUNCHTIME_NAME).toString();
          long lLaunchTime = Long.parseLong(launchTime);
          if (lLaunchTime > 0) {
            Date launchDate = new Date(lLaunchTime);
            SubmarineUtils.setAgulObjValue(intpContext, YARN_APP_LAUNCH_TIME,
                launchDate.toString());
          }
        }
        if (mapAppStatus.containsKey("finishedTime")) {
          String finishedTime = mapAppStatus.get("finishedTime").toString();
          long lFinishedTime = Long.parseLong(finishedTime);
          if (lFinishedTime > 0) {
            Date finishedDate = new Date(lFinishedTime);
            SubmarineUtils.setAgulObjValue(intpContext, YARN_APP_FINISHED_TIME,
                finishedDate.toString());
          }
        }
        if (mapAppStatus.containsKey("elapsedTime")) {
          String elapsedTime = mapAppStatus.get("elapsedTime").toString();
          long lElapsedTime = Long.parseLong(elapsedTime);
          if (lElapsedTime > 0) {
            String finishedDate = org.apache.hadoop.util.StringUtils.formatTime(lElapsedTime);
            SubmarineUtils.setAgulObjValue(intpContext, YARN_APP_ELAPSED_TIME, finishedDate);
          }
        }
      } catch (NumberFormatException e) {
        LOGGER.error(e.getMessage());
      }

      // create YARN UI link
      StringBuffer sbUrl = new StringBuffer();
      String yarnBaseUrl = properties.getProperty(YARN_WEB_HTTP_ADDRESS, "");
      sbUrl.append(yarnBaseUrl).append("/ui2/#/yarn-app/").append(appId);
      sbUrl.append("/components?service=").append(appName);

      SubmarineUtils.setAgulObjValue(intpContext, YARN_APPLICATION_ID, appId);

      SubmarineUtils.setAgulObjValue(intpContext, YARN_APPLICATION_URL, sbUrl.toString());
    }
  }

  private SubmarineJobStatus convertYarnState(String status, String finalStatus) {
    SubmarineJobStatus submarineJobStatus = SubmarineJobStatus.UNKNOWN;
    switch (status) {
      case "NEW":
        submarineJobStatus = SubmarineJobStatus.YARN_NEW;
        break;
      case "NEW_SAVING":
        submarineJobStatus = SubmarineJobStatus.YARN_NEW_SAVING;
        break;
      case "SUBMITTED":
        submarineJobStatus = SubmarineJobStatus.YARN_SUBMITTED;
        break;
      case "ACCEPTED":
        submarineJobStatus = SubmarineJobStatus.YARN_ACCEPTED;
        break;
      case "RUNNING":
        submarineJobStatus = SubmarineJobStatus.YARN_RUNNING;
        break;
      case "FINISHED":
        submarineJobStatus = SubmarineJobStatus.YARN_FINISHED;
        break;
      case "FAILED":
        submarineJobStatus = SubmarineJobStatus.YARN_FAILED;
        break;
      case "KILLED":
        submarineJobStatus = SubmarineJobStatus.YARN_KILLED;
        break;
      case "STOPPED":
        submarineJobStatus = SubmarineJobStatus.YARN_STOPPED;
    }
    switch (finalStatus) {
      case "NEW":
        submarineJobStatus = SubmarineJobStatus.YARN_NEW;
        break;
      case "NEW_SAVING":
        submarineJobStatus = SubmarineJobStatus.YARN_NEW_SAVING;
        break;
      case "SUBMITTED":
        submarineJobStatus = SubmarineJobStatus.YARN_SUBMITTED;
        break;
      case "ACCEPTED":
        submarineJobStatus = SubmarineJobStatus.YARN_ACCEPTED;
        break;
      case "RUNNING":
        submarineJobStatus = SubmarineJobStatus.YARN_RUNNING;
        break;
      case "FINISHED":
        submarineJobStatus = SubmarineJobStatus.YARN_FINISHED;
        break;
      case "FAILED":
        submarineJobStatus = SubmarineJobStatus.YARN_FAILED;
        break;
      case "KILLED":
        submarineJobStatus = SubmarineJobStatus.YARN_KILLED;
        break;
      case "STOPPED":
        submarineJobStatus = SubmarineJobStatus.YARN_STOPPED;
        break;
      default: // UNDEFINED
        break;
    }

    return submarineJobStatus;
  }

  public InterpreterContext getIntpContext() {
    return intpContext;
  }

  public void setIntpContext(InterpreterContext intpContext) {
    this.intpContext = intpContext;
    this.submarineUI = new SubmarineUI(intpContext);
  }
}
