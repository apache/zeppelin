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
import java.util.List;
import java.util.Map;
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
    if (!envs.containsKey("FLINK_CONF_DIR")) {
      envs.put("FLINK_CONF_DIR", flinkHome + "/conf");
    }
    envs.put("FLINK_LIB_DIR", flinkHome + "/lib");
    envs.put("FLINK_PLUGINS_DIR", flinkHome + "/plugins");

    // yarn application mode specific logic
    if (context.getProperties().getProperty("flink.execution.mode")
            .equalsIgnoreCase("yarn_application")) {
      envs.put("ZEPPELIN_FLINK_YARN_APPLICATION", "true");

      StringBuilder flinkYarnApplicationConfBuilder = new StringBuilder();

      // Extract yarn.ship-files, add hive-site.xml automatically if hive is enabled
      // and HIVE_CONF_DIR is specified
      String hiveConfDirProperty = context.getProperties().getProperty("HIVE_CONF_DIR");
      List<String> yarnShipFiles = new ArrayList<>();
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
      if (!yarnShipFiles.isEmpty()) {
        flinkYarnApplicationConfBuilder.append(
                " -D yarn.ship-files=" + yarnShipFiles.stream().collect(Collectors.joining(",")));
      }

      // specify yarn.application.name
      String yarnAppName = context.getProperties().getProperty("flink.yarn.appName");
      if (StringUtils.isNotBlank(yarnAppName)) {
        // flink run command can not contains whitespace, so replace it with _
        flinkYarnApplicationConfBuilder.append(
                " -D yarn.application.name=" + yarnAppName.replaceAll(" ", "_") + "");
      }

      // add other yarn and python configuration.
      for (Map.Entry<Object, Object> entry : context.getProperties().entrySet()) {
        if (!entry.getKey().toString().equalsIgnoreCase("yarn.ship-files") &&
            !entry.getKey().toString().equalsIgnoreCase("flink.yarn.appName")) {
          if (CharMatcher.whitespace().matchesAnyOf(entry.getValue().toString())) {
            LOGGER.warn("flink configuration key {} is skipped because it contains white space",
                    entry.getValue().toString());
          } else {
            flinkYarnApplicationConfBuilder.append(
                    " -D " + entry.getKey().toString() + "=" + entry.getValue().toString() + "");
          }
        }
      }
      envs.put("ZEPPELIN_FLINK_YANR_APPLICATION_CONF", flinkYarnApplicationConfBuilder.toString());
    }

    return envs;
  }
}
