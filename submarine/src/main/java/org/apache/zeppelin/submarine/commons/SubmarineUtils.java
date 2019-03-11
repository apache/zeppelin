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

package org.apache.zeppelin.submarine.commons;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.fs.Path;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.submarine.job.SubmarineJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.zeppelin.submarine.commons.SubmarineConstants.CHECKPOINT_PATH;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.DOCKER_CONTAINER_NETWORK;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.DOCKER_CONTAINER_TIME_ZONE;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.DOCKER_HADOOP_HDFS_HOME;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.DOCKER_JAVA_HOME;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.HADOOP_YARN_SUBMARINE_JAR;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.INPUT_PATH;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.INTERPRETER_LAUNCH_MODE;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.JOB_NAME;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.MACHINELEARING_DISTRIBUTED_ENABLE;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.PS_LAUNCH_CMD;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.SUBMARINE_ALGORITHM_HDFS_FILES;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.SUBMARINE_ALGORITHM_HDFS_PATH;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.SUBMARINE_HADOOP_CONF_DIR;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.SUBMARINE_HADOOP_HOME;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.SUBMARINE_HADOOP_KEYTAB;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.SUBMARINE_HADOOP_PRINCIPAL;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.SUBMARINE_YARN_QUEUE;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.TF_CHECKPOINT_PATH;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.TF_PARAMETER_SERVICES_CPU;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.TF_PARAMETER_SERVICES_DOCKER_IMAGE;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.TF_PARAMETER_SERVICES_GPU;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.TF_PARAMETER_SERVICES_MEMORY;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.TF_PARAMETER_SERVICES_NUM;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.TF_TENSORBOARD_ENABLE;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.TF_WORKER_SERVICES_CPU;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.TF_WORKER_SERVICES_DOCKER_IMAGE;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.TF_WORKER_SERVICES_GPU;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.TF_WORKER_SERVICES_MEMORY;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.TF_WORKER_SERVICES_NUM;
import static org.apache.zeppelin.submarine.commons.SubmarineConstants.WORKER_LAUNCH_CMD;

public class SubmarineUtils {
  private static Logger LOGGER = LoggerFactory.getLogger(SubmarineUI.class);

  public static String unifyKey(String key) {
    key = key.replace(".", "_").toUpperCase();
    return key;
  }

  // 1. yarn application match the pattern [a-z][a-z0-9-]*
  // 2. yarn limit appName can not be greater than 30 characters
  public static String getJobName(String userName, String noteId)
      throws RuntimeException {
    userName = userName.toLowerCase();
    userName = userName.replace("_", "-");
    userName = userName.replace(".", "-");

    noteId = noteId.toLowerCase();
    noteId = noteId.replace("_", "-");
    noteId = noteId.replace(".", "-");

    String jobName = userName + "-" + noteId;

    String yarnAppPatternString = "[a-z][a-z0-9-]*";
    Pattern pattern = Pattern.compile(yarnAppPatternString);
    Matcher matcher = pattern.matcher(jobName);
    boolean matches = matcher.matches();
    if (false == matches) {
      throw new RuntimeException("Job Name(`noteName`-`noteId`) " +
          "does not matcher the `[a-z][a-z0-9-]*` Pattern!");
    }

    if (jobName.length() > 30) {
      throw new RuntimeException("Job Name can not be greater than 30 characters");
    }

    return jobName;
  }

  // 1. Yarn application match the pattern [a-z][a-z0-9-]*
  // 2. Yarn registry dns Hostname can not be greater than 64 characters,
  //    The name needs to be short.
  public static String getTensorboardName(String user) {
    return user.toLowerCase() + "-tb";
  }

  public static String getAgulObjValue(InterpreterContext context, String name) {
    String value = "";
    AngularObject angularObject = context.getAngularObjectRegistry()
        .get(name, context.getNoteId(), context.getParagraphId());
    if (null != angularObject && null != angularObject.get()) {
      value = angularObject.get().toString();
    }
    return value;
  }

  public static void setAgulObjValue(InterpreterContext context, String name, Object value) {
    AngularObject angularObject = context.getAngularObjectRegistry()
        .add(name, value, context.getNoteId(), context.getParagraphId(), true);
  }

  public static void removeAgulObjValue(InterpreterContext context, String name) {
    context.getAngularObjectRegistry().remove(name, context.getNoteId(),
        context.getParagraphId(), true);
  }

  private static String getProperty(Properties properties, String key,
                                    boolean outputLog, StringBuffer sbMessage) {
    String value = properties.getProperty(key, "");
    if (StringUtils.isEmpty(value) && outputLog) {
      sbMessage.append("EXECUTE_SUBMARINE_ERROR: " +
          "Please set the submarine interpreter properties : ");
      sbMessage.append(key).append("\n");
    }

    return value;
  }

  // Convert properties to Map and check that the variable cannot be empty
  public static HashMap propertiesToJinjaParams(Properties properties, SubmarineJob submarineJob,
                                                boolean outLog)
      throws IOException {
    StringBuffer sbMessage = new StringBuffer();
    String noteId = submarineJob.getNoteId();

    // Check user-set job variables
    String machinelearingDistributed = getProperty(properties, MACHINELEARING_DISTRIBUTED_ENABLE,
        outLog, sbMessage);
    String inputPath = getProperty(properties, INPUT_PATH, outLog, sbMessage);
    String checkPointPath = getProperty(properties, CHECKPOINT_PATH, outLog, sbMessage);
    String psLaunchCmd = "";
    if (StringUtils.equals(machinelearingDistributed, "true")) {
      psLaunchCmd = getProperty(properties, PS_LAUNCH_CMD, outLog, sbMessage);
    }

    String workerLaunchCmd = getProperty(properties, WORKER_LAUNCH_CMD, outLog, sbMessage);

    // Check interpretere set Properties
    String submarineHadoopHome;
    submarineHadoopHome = getProperty(properties, SUBMARINE_HADOOP_HOME, outLog, sbMessage);
    File file = new File(submarineHadoopHome);
    if (!file.exists()) {
      sbMessage.append(SUBMARINE_HADOOP_HOME + ": "
          + submarineHadoopHome + " is not a valid file path!\n");
    }

    String submarineJar = getProperty(properties, HADOOP_YARN_SUBMARINE_JAR, outLog, sbMessage);
    file = new File(submarineJar);
    if (!file.exists()) {
      sbMessage.append(HADOOP_YARN_SUBMARINE_JAR + ":"
          + submarineJar + " is not a valid file path!\n");
    }
    String submarineYarnQueue = getProperty(properties, SUBMARINE_YARN_QUEUE, outLog, sbMessage);
    String containerNetwork = getProperty(properties, DOCKER_CONTAINER_NETWORK, outLog, sbMessage);
    String parameterServicesImage = getProperty(properties, TF_PARAMETER_SERVICES_DOCKER_IMAGE,
        outLog, sbMessage);
    String parameterServicesNum = getProperty(properties, TF_PARAMETER_SERVICES_NUM,
        outLog, sbMessage);
    String parameterServicesGpu = getProperty(properties, TF_PARAMETER_SERVICES_GPU,
        outLog, sbMessage);
    String parameterServicesCpu = getProperty(properties, TF_PARAMETER_SERVICES_CPU,
        outLog, sbMessage);
    String parameterServicesMemory = getProperty(properties, TF_PARAMETER_SERVICES_MEMORY,
        outLog, sbMessage);
    String workerServicesImage = getProperty(properties, TF_WORKER_SERVICES_DOCKER_IMAGE,
        outLog, sbMessage);
    String workerServicesNum = getProperty(properties, TF_WORKER_SERVICES_NUM, outLog, sbMessage);
    String workerServicesGpu = getProperty(properties, TF_WORKER_SERVICES_GPU, outLog, sbMessage);
    String workerServicesCpu = getProperty(properties, TF_WORKER_SERVICES_CPU, outLog, sbMessage);
    String workerServicesMemory = getProperty(properties, TF_WORKER_SERVICES_MEMORY,
        outLog, sbMessage);
    String algorithmUploadPath = getProperty(properties, SUBMARINE_ALGORITHM_HDFS_PATH,
        outLog, sbMessage);
    String submarineHadoopKeytab = getProperty(properties, SUBMARINE_HADOOP_KEYTAB,
        outLog, sbMessage);
    file = new File(submarineHadoopKeytab);
    if (!file.exists()) {
      sbMessage.append(SUBMARINE_HADOOP_KEYTAB + ":"
          + submarineHadoopKeytab + " is not a valid file path!\n");
    }
    String submarineHadoopPrincipal = getProperty(properties, SUBMARINE_HADOOP_PRINCIPAL,
        outLog, sbMessage);
    String dockerHadoopHdfsHome = getProperty(properties, DOCKER_HADOOP_HDFS_HOME,
        outLog, sbMessage);
    String dockerJavaHome = getProperty(properties, DOCKER_JAVA_HOME, outLog, sbMessage);
    String intpLaunchMode = getProperty(properties, INTERPRETER_LAUNCH_MODE, outLog, sbMessage);
    if (StringUtils.isEmpty(intpLaunchMode)) {
      intpLaunchMode = "local"; // default
    }
    String tensorboardEnable = getProperty(properties, TF_TENSORBOARD_ENABLE, outLog, sbMessage);
    if (StringUtils.isEmpty(tensorboardEnable)) {
      tensorboardEnable = "false"; // default
    }

    // check
    String tensorboardCheckpoint = getProperty(properties, TF_CHECKPOINT_PATH, outLog, sbMessage);
    if (StringUtils.equals(tensorboardEnable, "true")
        && StringUtils.isEmpty(tensorboardCheckpoint)) {
      sbMessage.append("Tensorboard checkpoint path cannot be empty!\n");
    }
    String userTensorboardCheckpoint = submarineJob.getUserTensorboardPath();
    Path chkpntPath = new Path(userTensorboardCheckpoint);
    if (chkpntPath.depth() <= 3) {
      sbMessage.append("Checkpoint path depth must be greater than 3!\n");
    }

    String sumbarineHadoopConfDir = getProperty(properties, SUBMARINE_HADOOP_CONF_DIR,
        outLog, sbMessage);

    String dockerContainerTimezone = getProperty(properties, DOCKER_CONTAINER_TIME_ZONE,
        outLog, sbMessage);

    String notePath = algorithmUploadPath + File.separator + noteId;
    List<String> arrayHdfsFiles = new ArrayList<>();
    List<Path> hdfsFiles = submarineJob.getHdfsClient().list(new Path(notePath + "/*"));
    if (hdfsFiles.size() == 0) {
      sbMessage.append("EXECUTE_SUBMARINE_ERROR: The " + notePath
          + " file directory was is empty in HDFS!\n");
    } else {
      if (outLog) {
        StringBuffer sbCommitFiles = new StringBuffer();
        sbCommitFiles.append("INFO: You commit total of " + hdfsFiles.size()
            + " algorithm files.\n");
        for (int i = 0; i < hdfsFiles.size(); i++) {
          String filePath = hdfsFiles.get(i).toUri().toString();
          arrayHdfsFiles.add(filePath);
          sbCommitFiles.append("INFO: [" + hdfsFiles.get(i).getName() + "] -> " + filePath + "\n");
        }
        submarineJob.getSubmarineUI().outputLog("Execution information",
            sbCommitFiles.toString());
      }
    }

    // Found null variable, throw exception
    if (!StringUtils.isEmpty(sbMessage.toString()) && outLog) {
      throw new RuntimeException(sbMessage.toString());
    }

    // Save user-set variables and interpreter configuration parameters
    String jobName = SubmarineUtils.getJobName(submarineJob.getUserName(),
        submarineJob.getNoteId());
    HashMap<String, Object> mapParams = new HashMap();
    mapParams.put(unifyKey(INTERPRETER_LAUNCH_MODE), intpLaunchMode);
    mapParams.put(unifyKey(SUBMARINE_HADOOP_HOME), submarineHadoopHome);
    mapParams.put(unifyKey(SUBMARINE_HADOOP_CONF_DIR), sumbarineHadoopConfDir);
    mapParams.put(unifyKey(DOCKER_HADOOP_HDFS_HOME), dockerHadoopHdfsHome);
    mapParams.put(unifyKey(DOCKER_JAVA_HOME), dockerJavaHome);
    mapParams.put(unifyKey(DOCKER_CONTAINER_TIME_ZONE), dockerContainerTimezone);
    mapParams.put(unifyKey(HADOOP_YARN_SUBMARINE_JAR), submarineJar);
    mapParams.put(unifyKey(JOB_NAME), jobName);
    mapParams.put(unifyKey(DOCKER_CONTAINER_NETWORK), containerNetwork);
    mapParams.put(unifyKey(SUBMARINE_YARN_QUEUE), submarineYarnQueue);
    mapParams.put(unifyKey(SUBMARINE_HADOOP_KEYTAB), submarineHadoopKeytab);
    mapParams.put(unifyKey(SUBMARINE_HADOOP_PRINCIPAL), submarineHadoopPrincipal);
    mapParams.put(unifyKey(MACHINELEARING_DISTRIBUTED_ENABLE), machinelearingDistributed);
    mapParams.put(unifyKey(SUBMARINE_ALGORITHM_HDFS_PATH), notePath);
    mapParams.put(unifyKey(SUBMARINE_ALGORITHM_HDFS_FILES), arrayHdfsFiles);
    mapParams.put(unifyKey(INPUT_PATH), inputPath);
    mapParams.put(unifyKey(CHECKPOINT_PATH), checkPointPath);
    mapParams.put(unifyKey(PS_LAUNCH_CMD), psLaunchCmd);
    mapParams.put(unifyKey(WORKER_LAUNCH_CMD), workerLaunchCmd);
    mapParams.put(unifyKey(TF_PARAMETER_SERVICES_DOCKER_IMAGE), parameterServicesImage);
    mapParams.put(unifyKey(TF_PARAMETER_SERVICES_NUM), parameterServicesNum);
    mapParams.put(unifyKey(TF_PARAMETER_SERVICES_GPU), parameterServicesGpu);
    mapParams.put(unifyKey(TF_PARAMETER_SERVICES_CPU), parameterServicesCpu);
    mapParams.put(unifyKey(TF_PARAMETER_SERVICES_MEMORY), parameterServicesMemory);
    mapParams.put(unifyKey(TF_WORKER_SERVICES_DOCKER_IMAGE), workerServicesImage);
    mapParams.put(unifyKey(TF_WORKER_SERVICES_NUM), workerServicesNum);
    mapParams.put(unifyKey(TF_WORKER_SERVICES_GPU), workerServicesGpu);
    mapParams.put(unifyKey(TF_WORKER_SERVICES_CPU), workerServicesCpu);
    mapParams.put(unifyKey(TF_WORKER_SERVICES_MEMORY), workerServicesMemory);
    mapParams.put(unifyKey(TF_TENSORBOARD_ENABLE), tensorboardEnable);
    mapParams.put(unifyKey(TF_CHECKPOINT_PATH), userTensorboardCheckpoint);

    return mapParams;
  }
}
