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


package org.apache.zeppelin.interpreter.launcher;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterRunner;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterRunningProcess;
import org.apache.zeppelin.interpreter.remote.SparkK8SRemoteInterpreterManagedProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Map;

/**
 * Interpreter Launcher which use shell script to launch Spark interpreter process,
 * on Kubernetes cluster.
 */
public class SparkK8SInterpreterLauncher extends SparkInterpreterLauncher {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkInterpreterLauncher.class);
  public static final String SPARK_KUBERNETES_DRIVER_LABEL_INTERPRETER_PROCESS_ID =
    "spark.kubernetes.driver.label.interpreter-processId";
  public static final String SPARK_APP_NAME = "spark.app.name";
  public static final String SPARK_METRICS_NAMESPACE = "spark.metrics.namespace";

  public SparkK8SInterpreterLauncher(ZeppelinConfiguration zConf, RecoveryStorage recoveryStorage) {
    super(zConf, recoveryStorage);
  }

  @Override
  public InterpreterClient launch(InterpreterLaunchContext context) {
    LOGGER.info("Launching Interpreter: " + context.getInterpreterSettingGroup());
    this.properties = context.getProperties();
    InterpreterOption option = context.getOption();
    InterpreterRunner runner = context.getRunner();
    String groupName = context.getInterpreterSettingGroup();

    int connectTimeout =
            zConf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT);
    if (option.isExistingProcess()) {
      return new RemoteInterpreterRunningProcess(
              context.getInterpreterSettingName(),
              connectTimeout,
              option.getHost(),
              option.getPort());
    } else {
      // create new remote process
      String localRepoPath = zConf.getInterpreterLocalRepoPath() + "/"
              + context.getInterpreterSettingId();

      String groupId = context.getInterpreterGroupId();
      String processIdLabel = generatePodLabelId(groupId);
      properties.put(SPARK_KUBERNETES_DRIVER_LABEL_INTERPRETER_PROCESS_ID, processIdLabel);
      groupId = formatId(groupId, 50);
      // add groupId to app name, this will be the prefix for driver pod name if it's not
      // explicitly specified
      String driverPodNamePrefix = properties.get(SPARK_APP_NAME) + "-" + groupId;
      properties.put(SPARK_APP_NAME, driverPodNamePrefix);
      // set same id for metrics namespace to be able to identify metrics of a specific app
      properties.put(SPARK_METRICS_NAMESPACE, driverPodNamePrefix);

      Map<String, String> env = super.buildEnvFromProperties(context);
      String sparkConf = buildSparkConf(localRepoPath, env);
      LOGGER.debug(sparkConf);
      env.put("ZEPPELIN_SPARK_CONF", sparkConf);
      env.put("ZEPPELIN_SPARK_K8_CLUSTER", "true");

      return new SparkK8SRemoteInterpreterManagedProcess(
              runner != null ? runner.getPath() : zConf.getInterpreterRemoteRunnerPath(),
              zConf.getCallbackPortRange(),
              zConf.getInterpreterDir() + "/" + groupName, localRepoPath,
              env, connectTimeout, processIdLabel, context.getInterpreterSettingName(),
              option.isUserImpersonate());
    }
  }

  private String buildSparkConf(String localRepoPath, Map<String, String> env) {
    StringBuilder sparkJarsBuilder = new StringBuilder();

    String interpreterLibPath = zConf.getZeppelinHome() + "/lib/interpreter";
    File interpreterLib = new File(interpreterLibPath);
    if (interpreterLib.isDirectory()) {
      for (File file : interpreterLib.listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          if (name.endsWith("jar")) {
            return true;
          }
          return false;
        }

      })) {
        if (sparkJarsBuilder.length() > 0) {
          sparkJarsBuilder.append(",");
        }
        sparkJarsBuilder.append(file.getPath());
      }
    }

    File localRepo = new File(localRepoPath);
    if (localRepo.isDirectory()) {
      for (File file : localRepo.listFiles()) {
        if (sparkJarsBuilder.length() > 0) {
          sparkJarsBuilder.append(",");
        }
        if (file.getName().endsWith("jar")) {
          sparkJarsBuilder.append(file.getPath());
        }
      }
    }

    StringBuilder sparkConfBuilder = new StringBuilder(env.get("ZEPPELIN_SPARK_CONF"));
    if (sparkJarsBuilder.length() > 0) {
      sparkConfBuilder.append(" --jars ").append(sparkJarsBuilder.toString());
    }
    sparkConfBuilder.append(" --files " + zConf.getConfDir() + "/log4j_k8_cluster" +
      ".properties");
    return sparkConfBuilder.toString();
  }

  /**
   * Id for spark submit must be formatted to contain only alfanumeric chars.
   * @param str
   * @param maxLength
   * @return
   */
  private String formatId(String str, int maxLength) {
    str = str.replaceAll("[^a-zA-Z0-9]", "-").toLowerCase();
    if (str.length() > maxLength) {
      str = str.substring(0, maxLength - 1);
    }
    return str;
  }

  private String generatePodLabelId(String interpreterGroupId ) {
    return formatId(interpreterGroupId + "_" + System.currentTimeMillis(), 64);
  }

}
