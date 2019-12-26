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

package org.apache.zeppelin.submarine;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.display.ui.OptionInput.ParamOption;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.submarine.commons.SubmarineCommand;
import org.apache.zeppelin.submarine.commons.SubmarineConstants;
import org.apache.zeppelin.submarine.job.SubmarineJob;
import org.apache.zeppelin.submarine.commons.SubmarineUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Properties;

import static org.apache.zeppelin.submarine.commons.SubmarineCommand.CLEAN_RUNTIME_CACHE;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.CHECKPOINT_PATH;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.CLEAN_CHECKPOINT;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.COMMAND_CLEAN;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.COMMAND_JOB_RUN;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.COMMAND_JOB_SHOW;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.COMMAND_TYPE;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.COMMAND_USAGE;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.INPUT_PATH;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.MACHINELEARNING_DISTRIBUTED_ENABLE;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.OPERATION_TYPE;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.PS_LAUNCH_CMD;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.SUBMARINE_ALGORITHM_HDFS_PATH;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.TF_CHECKPOINT_PATH;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.USERNAME_SYMBOL;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.WORKER_LAUNCH_CMD;
import static org.apache.zeppelin.submarine.commons.SubmarineUtils.unifyKey;

/**
 * SubmarineInterpreter of Hadoop Submarine implementation.
 * Support for Hadoop Submarine cli. All the commands documented here
 * https://github.com/apache/hadoop/tree/trunk/hadoop-submarine/hadoop-submarine-core
 * /src/site/markdown/QuickStart.md is supported.
 */
public class SubmarineInterpreter extends Interpreter {
  private Logger LOGGER = LoggerFactory.getLogger(SubmarineInterpreter.class);

  // Number of submarines executed in parallel for each interpreter instance
  protected int concurrentExecutedMax = 1;

  private boolean needUpdateConfig = true;
  private String currentReplName = "";

  private SubmarineContext submarineContext = null;

  public SubmarineInterpreter(Properties properties) {
    super(properties);

    String concurrentMax = getProperty(SubmarineConstants.SUBMARINE_CONCURRENT_MAX, "1");
    concurrentExecutedMax = Integer.parseInt(concurrentMax);

    submarineContext = SubmarineContext.getInstance();
  }

  @Override
  public void open() {
    LOGGER.info("SubmarineInterpreter open()");
  }

  @Override
  public void close() {
    submarineContext.stopAllSubmarineJob();
  }

  private void setParagraphConfig(InterpreterContext context) {
    context.getConfig().put("editorHide", true);
    context.getConfig().put("title", false);
  }

  @Override
  public InterpreterResult interpret(String script, InterpreterContext context) {
    try {
      setParagraphConfig(context);

      // algorithm & checkpoint path support replaces ${username} with real user name
      String algorithmPath = properties.getProperty(SUBMARINE_ALGORITHM_HDFS_PATH, "");
      if (algorithmPath.contains(USERNAME_SYMBOL)) {
        algorithmPath = algorithmPath.replace(USERNAME_SYMBOL, userName);
        properties.setProperty(SUBMARINE_ALGORITHM_HDFS_PATH, algorithmPath);
      }
      String checkpointPath = properties.getProperty(TF_CHECKPOINT_PATH, "");
      if (checkpointPath.contains(USERNAME_SYMBOL)) {
        checkpointPath = checkpointPath.replace(USERNAME_SYMBOL, userName);
        properties.setProperty(TF_CHECKPOINT_PATH, checkpointPath);
      }

      SubmarineJob submarineJob = submarineContext.addOrGetSubmarineJob(properties, context);

      LOGGER.debug("Run shell command '" + script + "'");
      String command = "", operation = "", cleanCheckpoint = "";
      String inputPath = "", chkPntPath = "", psLaunchCmd = "", workerLaunchCmd = "";
      String noteId = context.getNoteId();
      String noteName = context.getNoteName();

      if (script.equalsIgnoreCase(COMMAND_CLEAN)) {
        // Clean Registry Angular Object
        command = CLEAN_RUNTIME_CACHE.getCommand();
      } else {
        operation = SubmarineUtils.getAgulObjValue(context, OPERATION_TYPE);
        if (!StringUtils.isEmpty(operation)) {
          SubmarineUtils.removeAgulObjValue(context, OPERATION_TYPE);
          command = operation;
        } else {
          command = SubmarineUtils.getAgulObjValue(context, COMMAND_TYPE);
        }
      }

      String distributed = this.properties.getProperty(MACHINELEARNING_DISTRIBUTED_ENABLE, "false");
      SubmarineUtils.setAgulObjValue(context, unifyKey(MACHINELEARNING_DISTRIBUTED_ENABLE),
          distributed);

      inputPath = SubmarineUtils.getAgulObjValue(context, INPUT_PATH);
      cleanCheckpoint = SubmarineUtils.getAgulObjValue(context, CLEAN_CHECKPOINT);
      chkPntPath = submarineJob.getJobDefaultCheckpointPath();
      SubmarineUtils.setAgulObjValue(context, CHECKPOINT_PATH, chkPntPath);
      psLaunchCmd = SubmarineUtils.getAgulObjValue(context, PS_LAUNCH_CMD);
      workerLaunchCmd = SubmarineUtils.getAgulObjValue(context, WORKER_LAUNCH_CMD);
      properties.put(INPUT_PATH, inputPath != null ? inputPath : "");
      properties.put(CHECKPOINT_PATH, chkPntPath != null ? chkPntPath : "");
      properties.put(PS_LAUNCH_CMD, psLaunchCmd != null ? psLaunchCmd : "");
      properties.put(WORKER_LAUNCH_CMD, workerLaunchCmd != null ? workerLaunchCmd : "");

      SubmarineCommand submarineCmd = SubmarineCommand.fromCommand(command);
      switch (submarineCmd) {
        case USAGE:
          submarineJob.showUsage();
          break;
        case JOB_RUN:
          if (StringUtils.equals(cleanCheckpoint, "true")) {
            submarineJob.cleanJobDefaultCheckpointPath();
          }
          submarineJob.runJob();
          break;
        case JOB_STOP:
          String jobName = SubmarineUtils.getJobName(userName, noteId);
          submarineJob.deleteJob(jobName);
          break;
        case TENSORBOARD_RUN:
          submarineJob.runTensorBoard();
          break;
        case TENSORBOARD_STOP:
          String user = context.getAuthenticationInfo().getUser();
          String tensorboardName = SubmarineUtils.getTensorboardName(user);
          submarineJob.deleteJob(tensorboardName);
          break;
        case OLD_UI:
          createOldGUI(context);
          break;
        case CLEAN_RUNTIME_CACHE:
          submarineJob.cleanRuntimeCache();
          break;
        default:
          submarineJob.onDashboard();
          break;
      }
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
      return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage());
    }

    return new InterpreterResult(InterpreterResult.Code.SUCCESS);
  }

  @Override
  public void cancel(InterpreterContext context) {
    SubmarineJob submarineJob = submarineContext.addOrGetSubmarineJob(properties, context);
    String userName = context.getAuthenticationInfo().getUser();
    String noteId = context.getNoteId();
    String jobName = SubmarineUtils.getJobName(userName, noteId);
    submarineJob.deleteJob(jobName);
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    String schedulerName = SubmarineInterpreter.class.getName() + this.hashCode();
    if (concurrentExecutedMax > 1) {
      return SchedulerFactory.singleton().createOrGetParallelScheduler(schedulerName,
          concurrentExecutedMax);
    } else {
      return SchedulerFactory.singleton().createOrGetFIFOScheduler(schedulerName);
    }
  }

  @Override
  public List<InterpreterCompletion> completion(
      String buf, int cursor, InterpreterContext intpContext) {
    return null;
  }

  public void setPythonWorkDir(String noteId, File pythonWorkDir) {
    SubmarineJob submarineJob = submarineContext.getSubmarineJob(noteId);
    if (null != submarineJob) {
      submarineJob.setPythonWorkDir(pythonWorkDir);
    }
  }

  private String createOldGUI(InterpreterContext context) {
    // submarine command - Format
    ParamOption[] commandOptions = new ParamOption[4];
    commandOptions[0] = new ParamOption(COMMAND_JOB_RUN, COMMAND_JOB_RUN);
    commandOptions[1] = new ParamOption(COMMAND_JOB_SHOW, COMMAND_JOB_SHOW);
    commandOptions[2] = new ParamOption(COMMAND_USAGE, COMMAND_USAGE);
    String command = (String) context.getGui().
        select("Submarine Command", commandOptions, "");

    String distributed = this.properties.getProperty(MACHINELEARNING_DISTRIBUTED_ENABLE, "false");

    if (command.equals(COMMAND_JOB_RUN)) {
      String inputPath = (String) context.getGui().textbox("Input Path(input_path)");
      String checkpoinkPath = (String) context.getGui().textbox("Checkpoint Path(checkpoint_path)");
      if (distributed.equals("true")) {
        String psLaunchCmd = (String) context.getGui().textbox("PS Launch Command");
      }
      String workerLaunchCmd = (String) context.getGui().textbox("Worker Launch Command");
    }

    /* Active
    ParamOption[] auditOptions = new ParamOption[1];
    auditOptions[0] = new ParamOption("Active", "Active command");
    List<Object> flags = intpContext.getGui().checkbox("Active", Arrays.asList(""), auditOptions);
    boolean activeChecked = flags.contains("Active");
    intpContext.getResourcePool().put(intpContext.getNoteId(),
        intpContext.getParagraphId(), "Active", activeChecked);*/

    return command;
  }

  @VisibleForTesting
  public SubmarineContext getSubmarineContext() {
    return submarineContext;
  }
}
