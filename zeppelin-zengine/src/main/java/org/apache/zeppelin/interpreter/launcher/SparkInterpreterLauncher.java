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

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Spark specific launcher.
 */
public class SparkInterpreterLauncher extends ShellScriptLauncher {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkInterpreterLauncher.class);

  public SparkInterpreterLauncher(ZeppelinConfiguration zConf) {
    super(zConf);
  }

  @Override
  protected Map<String, String> buildEnvFromProperties() {
    Map<String, String> env = new HashMap<String, String>();
    Properties sparkProperties = new Properties();
    String sparkMaster = getSparkMaster(properties);
    for (String key : properties.stringPropertyNames()) {
      if (RemoteInterpreterUtils.isEnvString(key)) {
        env.put(key, properties.getProperty(key));
      }
      if (isSparkConf(key, properties.getProperty(key))) {
        sparkProperties.setProperty(key, toShellFormat(properties.getProperty(key)));
      }
    }

    setupPropertiesForPySpark(sparkProperties);
    setupPropertiesForSparkR(sparkProperties);
    if (isYarnMode() && getDeployMode().equals("cluster")) {
      env.put("ZEPPELIN_SPARK_YARN_CLUSTER", "true");
    }

    StringBuilder sparkConfBuilder = new StringBuilder();
    if (sparkMaster != null) {
      sparkConfBuilder.append(" --master " + sparkMaster);
    }
    if (isYarnMode() && getDeployMode().equals("cluster")) {
      sparkConfBuilder.append(" --files " + zConf.getConfDir() + "/log4j_yarn_cluster.properties");
    }
    for (String name : sparkProperties.stringPropertyNames()) {
      sparkConfBuilder.append(" --conf " + name + "=" + sparkProperties.getProperty(name));
    }

    env.put("ZEPPELIN_SPARK_CONF", sparkConfBuilder.toString());

    // set these env in the order of
    // 1. interpreter-setting
    // 2. zeppelin-env.sh
    // It is encouraged to set env in interpreter setting, but just for backward compatability,
    // we also fallback to zeppelin-env.sh if it is not specified in interpreter setting.
    for (String envName : new String[]{"SPARK_HOME", "SPARK_CONF_DIR", "HADOOP_CONF_DIR"})  {
      String envValue = getEnv(envName);
      if (envValue != null) {
        env.put(envName, envValue);
      }
    }
    LOGGER.debug("buildEnvFromProperties: " + env);
    return env;

  }


  /**
   * get environmental variable in the following order
   *
   * 1. interpreter setting
   * 2. zeppelin-env.sh
   *
   */
  private String getEnv(String envName) {
    String env = properties.getProperty(envName);
    if (env == null) {
      env = System.getenv(envName);
    }
    return env;
  }

  private boolean isSparkConf(String key, String value) {
    return !StringUtils.isEmpty(key) && key.startsWith("spark.") && !StringUtils.isEmpty(value);
  }

  private void setupPropertiesForPySpark(Properties sparkProperties) {
    if (isYarnMode()) {
      sparkProperties.setProperty("spark.yarn.isPython", "true");
    }
  }

  private void mergeSparkProperty(Properties sparkProperties, String propertyName,
                                  String propertyValue) {
    if (sparkProperties.containsKey(propertyName)) {
      String oldPropertyValue = sparkProperties.getProperty(propertyName);
      sparkProperties.setProperty(propertyName, oldPropertyValue + "," + propertyValue);
    } else {
      sparkProperties.setProperty(propertyName, propertyValue);
    }
  }

  private void setupPropertiesForSparkR(Properties sparkProperties) {
    String sparkHome = getEnv("SPARK_HOME");
    File sparkRBasePath = null;
    if (sparkHome == null) {
      if (!getSparkMaster(properties).startsWith("local")) {
        throw new RuntimeException("SPARK_HOME is not specified in interpreter-setting" +
            " for non-local mode, if you specify it in zeppelin-env.sh, please move that into " +
            " interpreter setting");
      }
      String zeppelinHome = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME);
      sparkRBasePath = new File(zeppelinHome,
          "interpreter" + File.separator + "spark" + File.separator + "R");
    } else {
      sparkRBasePath = new File(sparkHome, "R" + File.separator + "lib");
    }

    File sparkRPath = new File(sparkRBasePath, "sparkr.zip");
    if (sparkRPath.exists() && sparkRPath.isFile()) {
      mergeSparkProperty(sparkProperties, "spark.yarn.dist.archives", sparkRPath.getAbsolutePath());
    } else {
      LOGGER.warn("sparkr.zip is not found, SparkR may not work.");
    }
  }

  /**
   * Order to look for spark master
   * 1. master in interpreter setting
   * 2. spark.master interpreter setting
   * 3. use local[*]
   * @param properties
   * @return
   */
  private String getSparkMaster(Properties properties) {
    String master = properties.getProperty("master");
    if (master == null) {
      master = properties.getProperty("spark.master");
      if (master == null) {
        master = "local[*]";
      }
    }
    return master;
  }

  private String getDeployMode() {
    String master = getSparkMaster(properties);
    if (master.equals("yarn-client")) {
      return "client";
    } else if (master.equals("yarn-cluster")) {
      return "cluster";
    } else if (master.startsWith("local")) {
      return "client";
    } else {
      String deployMode = properties.getProperty("spark.submit.deployMode");
      if (deployMode == null) {
        throw new RuntimeException("master is set as yarn, but spark.submit.deployMode " +
            "is not specified");
      }
      if (!deployMode.equals("client") && !deployMode.equals("cluster")) {
        throw new RuntimeException("Invalid value for spark.submit.deployMode: " + deployMode);
      }
      return deployMode;
    }
  }

  private boolean isYarnMode() {
    return getSparkMaster(properties).startsWith("yarn");
  }

  private String toShellFormat(String value) {
    if (value.contains("\'") && value.contains("\"")) {
      throw new RuntimeException("Spark property value could not contain both \" and '");
    } else if (value.contains("\'")) {
      return "\"" + value + "\"";
    } else {
      return "\'" + value + "\'";
    }
  }

}
