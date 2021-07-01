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

import com.google.common.base.CharMatcher;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class FlinkInterpreterLauncher extends StandardInterpreterLauncher {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlinkInterpreterLauncher.class);


  public FlinkInterpreterLauncher(ZeppelinConfiguration zConf, RecoveryStorage recoveryStorage) {
    super(zConf, recoveryStorage);
  }

  @Override
  public Map<String, String> buildEnvFromProperties(InterpreterLaunchContext context)
          throws IOException {
    Map<String, String> envs = super.buildEnvFromProperties(context);

    String flinkHome = updateEnvsForFlinkHome(envs, context);

    if (!envs.containsKey("FLINK_CONF_DIR")) {
      envs.put("FLINK_CONF_DIR", flinkHome + "/conf");
    }
    envs.put("FLINK_LIB_DIR", flinkHome + "/lib");
    envs.put("FLINK_PLUGINS_DIR", flinkHome + "/plugins");

    normalizeConfiguration(context);

    String flinkExecutionMode = context.getProperties().getProperty("flink.execution.mode");
    // yarn application mode specific logic
    if ("yarn-application".equalsIgnoreCase(flinkExecutionMode)) {
      updateEnvsForYarnApplicationMode(envs, context);
    }

    String flinkAppJar = chooseFlinkAppJar(flinkHome);
    LOGGER.info("Choose FLINK_APP_JAR: {}", flinkAppJar);
    envs.put("FLINK_APP_JAR", flinkAppJar);

    if ("yarn".equalsIgnoreCase(flinkExecutionMode) ||
            "yarn-application".equalsIgnoreCase(flinkExecutionMode)) {
      boolean runAsLoginUser = Boolean.parseBoolean(context
              .getProperties()
              .getProperty("zeppelin.flink.run.asLoginUser", "true"));
      String userName = context.getUserName();
      if (runAsLoginUser && !"anonymous".equals(userName)) {
        envs.put("HADOOP_USER_NAME", userName);
      }
    }

    return envs;
  }

  // do mapping between configuration of different execution modes.
  private void normalizeConfiguration(InterpreterLaunchContext context) {
    Properties intpProperties = context.getProperties();
    setNewProperty(intpProperties, "flink.jm.memory", "jobmanager.memory.process.size", true);
    setNewProperty(intpProperties, "flink.tm.memory", "taskmanager.memory.process.size", true);
    setNewProperty(intpProperties, "flink.tm.slot", "taskmanager.numberOfTaskSlots", false);
    setNewProperty(intpProperties, "flink.yarn.appName", "yarn.application.name", false);
    setNewProperty(intpProperties, "flink.yarn.queue", "yarn.application.queue", false);
  }

  /**
   * flink.jm.memory and flink.tm.memory only support int value and the unit is mb. (e.g. 1024)
   * And you need to specify unit for jobmanager.memory.process.size and
   * taskmanager.memory.process.size, e.g. 1024 mb.
   * @param properties
   * @param oldKey
   * @param newKey
   * @param isMemoryProperty
   */
  private void setNewProperty(Properties properties,
                              String oldKey,
                              String newKey,
                              boolean isMemoryProperty) {
    String value = properties.getProperty(oldKey);
    if (StringUtils.isNotBlank(value) && !properties.containsKey(newKey)) {
      if (isMemoryProperty) {
        properties.put(newKey, value + "mb");
      } else {
        properties.put(newKey, value);
      }
    }
  }

  private String chooseFlinkAppJar(String flinkHome) throws IOException {
    File flinkLibFolder = new File(flinkHome, "lib");
    List<File> flinkDistFiles =
            Arrays.stream(flinkLibFolder.listFiles(file -> file.getName().contains("flink-dist_")))
                    .collect(Collectors.toList());
    if (flinkDistFiles.size() > 1) {
      throw new IOException("More than 1 flink-dist files: " +
              flinkDistFiles.stream()
                      .map(file -> file.getAbsolutePath())
                      .collect(Collectors.joining(",")));
    }
    String scalaVersion = "2.11";
    if (flinkDistFiles.get(0).getName().contains("2.12")) {
      scalaVersion = "2.12";
    }
    final String flinkScalaVersion = scalaVersion;
    File flinkInterpreterFolder =
            new File(ZeppelinConfiguration.create().getInterpreterDir(), "flink");
    List<File> flinkScalaJars =
            Arrays.stream(flinkInterpreterFolder
                    .listFiles(file -> file.getName().endsWith(".jar")))
            .filter(file -> file.getName().contains(flinkScalaVersion))
            .collect(Collectors.toList());
    if (flinkScalaJars.size() > 1) {
      throw new IOException("More than 1 flink scala files: " +
              flinkScalaJars.stream()
                      .map(file -> file.getAbsolutePath())
                      .collect(Collectors.joining(",")));
    }

    return flinkScalaJars.get(0).getAbsolutePath();
  }

  private String updateEnvsForFlinkHome(Map<String, String> envs,
                                      InterpreterLaunchContext context) throws IOException {
    String flinkHome = context.getProperties().getProperty("FLINK_HOME");
    if (StringUtils.isBlank(flinkHome)) {
      flinkHome = System.getenv("FLINK_HOME");
    }
    if (StringUtils.isBlank(flinkHome)) {
      throw new IOException("FLINK_HOME is not specified");
    }
    File flinkHomeFile = new File(flinkHome);
    if (!flinkHomeFile.exists()) {
      throw new IOException(String.format("FLINK_HOME '%s' doesn't exist", flinkHome));
    }
    if (!flinkHomeFile.isDirectory()) {
      throw new IOException(String.format("FLINK_HOME '%s' is a file, but should be directory",
              flinkHome));
    }
    return flinkHome;
  }

  private void updateEnvsForYarnApplicationMode(Map<String, String> envs,
                                                InterpreterLaunchContext context)
          throws IOException {

    envs.put("ZEPPELIN_FLINK_YARN_APPLICATION", "true");

    StringBuilder flinkYarnApplicationConfBuilder = new StringBuilder();
    // set yarn.ship-files
    List<String> yarnShipFiles = getYarnShipFiles(context);
    if (!yarnShipFiles.isEmpty()) {
      flinkYarnApplicationConfBuilder.append(
              " -D yarn.ship-files=" + yarnShipFiles.stream().collect(Collectors.joining(";")));
    }

    // set yarn.application.name
    String yarnAppName = context.getProperties().getProperty("flink.yarn.appName");
    if (StringUtils.isNotBlank(yarnAppName)) {
      // flink run command can not contains whitespace, so replace it with _
      flinkYarnApplicationConfBuilder.append(
              " -D yarn.application.name=" + yarnAppName.replaceAll(" ", "_") + "");
    }

    // add other yarn and python configuration.
    for (Map.Entry<Object, Object> entry : context.getProperties().entrySet()) {
      String key = entry.getKey().toString();
      String value = entry.getValue().toString();
      if (!key.equalsIgnoreCase("yarn.ship-files") &&
              !key.equalsIgnoreCase("flink.yarn.appName")) {
        if (CharMatcher.whitespace().matchesAnyOf(value)) {
          LOGGER.warn("flink configuration key {} is skipped because it contains white space",
                  key);
        } else {
          flinkYarnApplicationConfBuilder.append(" -D " + key + "=" + value);
        }
      }
    }
    envs.put("ZEPPELIN_FLINK_YARN_APPLICATION_CONF", flinkYarnApplicationConfBuilder.toString());
  }

  private List<String> getYarnShipFiles(InterpreterLaunchContext context) throws IOException {
    // Extract yarn.ship-files, add hive-site.xml automatically if hive is enabled
    // and HIVE_CONF_DIR is specified
    List<String> yarnShipFiles = new ArrayList<>();
    String hiveConfDirProperty = context.getProperties().getProperty("HIVE_CONF_DIR");
    if (StringUtils.isNotBlank(hiveConfDirProperty) &&
            Boolean.parseBoolean(context.getProperties()
                    .getProperty("zeppelin.flink.enableHive", "false"))) {
      File hiveSiteFile = new File(hiveConfDirProperty, "hive-site.xml");
      if (hiveSiteFile.isFile() && hiveSiteFile.exists()) {
        yarnShipFiles.add(hiveSiteFile.getAbsolutePath());
      } else {
        LOGGER.warn("Hive site file: {} doesn't exist or is not a directory", hiveSiteFile);
      }
    }
    if (context.getProperties().containsKey("yarn.ship-files")) {
      yarnShipFiles.add(context.getProperties().getProperty("yarn.ship-files"));
    }

    return yarnShipFiles;
  }
}
