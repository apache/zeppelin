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

import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
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
public class SparkInterpreterLauncher extends StandardInterpreterLauncher {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkInterpreterLauncher.class);

  public SparkInterpreterLauncher(ZeppelinConfiguration zConf, RecoveryStorage recoveryStorage) {
    super(zConf, recoveryStorage);
  }

  @Override
  public Map<String, String> buildEnvFromProperties(InterpreterLaunchContext context) throws IOException {
    Map<String, String> env = super.buildEnvFromProperties(context);
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
      sparkProperties.setProperty("spark.yarn.submit.waitAppCompletion", "false");
    }

    StringBuilder sparkConfBuilder = new StringBuilder();
    if (sparkMaster != null) {
      sparkConfBuilder.append(" --master " + sparkMaster);
    }
    if (isYarnMode() && getDeployMode().equals("cluster")) {
      if (sparkProperties.containsKey("spark.files")) {
        sparkProperties.put("spark.files", sparkProperties.getProperty("spark.files") + "," +
            zConf.getConfDir() + "/log4j_yarn_cluster.properties");
      } else {
        sparkProperties.put("spark.files", zConf.getConfDir() + "/log4j_yarn_cluster.properties");
      }
      sparkProperties.put("spark.yarn.maxAppAttempts", "1");
    }


    if (isYarnMode()
        && getDeployMode().equals("cluster")) {
      try {
        List<String> additionalJars = new ArrayList();
        Path localRepoPath =
                Paths.get(zConf.getInterpreterLocalRepoPath(), context.getInterpreterSettingId());
        if (Files.exists(localRepoPath) && Files.isDirectory(localRepoPath)) {
          List<String> localRepoJars = StreamSupport.stream(
                  Files.newDirectoryStream(localRepoPath, entry -> Files.isRegularFile(entry))
                          .spliterator(),
                  false)
                  .map(jar -> jar.toAbsolutePath().toString()).collect(Collectors.toList());
          additionalJars.addAll(localRepoJars);
        }

        String scalaVersion = detectSparkScalaVersion(properties.getProperty("SPARK_HOME"));
        Path scalaFolder =  Paths.get(zConf.getZeppelinHome(), "/interpreter/spark/scala-" + scalaVersion);
        List<String> scalaJars = StreamSupport.stream(
                Files.newDirectoryStream(scalaFolder, entry -> Files.isRegularFile(entry))
                        .spliterator(),
                false)
                .map(jar -> jar.toAbsolutePath().toString()).collect(Collectors.toList());
        additionalJars.addAll(scalaJars);

        if (sparkProperties.containsKey("spark.jars")) {
          sparkProperties.put("spark.jars", sparkProperties.getProperty("spark.jars") + "," +
                  StringUtils.join(additionalJars, ","));
        } else {
          sparkProperties.put("spark.jars", StringUtils.join(additionalJars, ","));
        }
      } catch (Exception e) {
        throw new IOException("Cannot make a list of additional jars from localRepo: {}", e);
      }
    }

    for (String name : sparkProperties.stringPropertyNames()) {
      sparkConfBuilder.append(" --conf " + name + "=" + sparkProperties.getProperty(name));
    }

    String useProxyUserEnv = System.getenv("ZEPPELIN_IMPERSONATE_SPARK_PROXY_USER");
    if (context.getOption().isUserImpersonate() && (StringUtils.isBlank(useProxyUserEnv) ||
            !useProxyUserEnv.equals("false"))) {
      sparkConfBuilder.append(" --proxy-user " + context.getUserName());
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

    String keytab = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_KERBEROS_KEYTAB);
    String principal =
        zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_KERBEROS_PRINCIPAL);

    if (!StringUtils.isBlank(keytab) && !StringUtils.isBlank(principal)) {
      env.put("ZEPPELIN_SERVER_KERBEROS_KEYTAB", keytab);
      env.put("ZEPPELIN_SERVER_KERBEROS_PRINCIPAL", principal);
      LOGGER.info("Run Spark under secure mode with keytab: " + keytab +
          ", principal: " + principal);
    } else {
      LOGGER.info("Run Spark under non-secure mode as no keytab and principal is specified");
    }
    LOGGER.debug("buildEnvFromProperties: " + env);
    return env;

  }

  private String detectSparkScalaVersion(String sparkHome) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(sparkHome + "/bin/spark-submit", "--version");
    File processOutputFile = File.createTempFile("zeppelin-spark", ".out");
    builder.redirectError(processOutputFile);
    Process process = builder.start();
    process.waitFor();
    String processOutput = IOUtils.toString(new FileInputStream(processOutputFile));
    Pattern pattern = Pattern.compile(".*Using Scala version (.*),.*");
    Matcher matcher = pattern.matcher(processOutput);
    if (matcher.find()) {
      String scalaVersion = matcher.group(1);
      if (scalaVersion.startsWith("2.10")) {
        return "2.10";
      } else if (scalaVersion.startsWith("2.11")) {
        return "2.11";
      } else if (scalaVersion.startsWith("2.12")) {
        return "2.12";
      } else {
        throw new Exception("Unsupported scala version: " + scalaVersion);
      }
    } else {
      return detectSparkScalaVersionByReplClass(sparkHome);
    }
  }

  private String detectSparkScalaVersionByReplClass(String sparkHome) throws Exception {
    File sparkLibFolder = new File(sparkHome + "/lib");
    if (sparkLibFolder.exists()) {
      // spark 1.6 if spark/lib exists
      File[] sparkAssemblyJars = new File(sparkHome + "/lib").listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.contains("spark-assembly");
        }
      });
      if (sparkAssemblyJars.length == 0) {
        throw new Exception("No spark assembly file found in SPARK_HOME: " + sparkHome);
      }
      if (sparkAssemblyJars.length > 1) {
        throw new Exception("Multiple spark assembly file found in SPARK_HOME: " + sparkHome);
      }
      URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{sparkAssemblyJars[0].toURI().toURL()});
      try {
        urlClassLoader.loadClass("org.apache.spark.repl.SparkCommandLine");
        return "2.10";
      } catch (ClassNotFoundException e) {
        return "2.11";
      }
    } else {
      // spark 2.x if spark/lib doesn't exists
      File sparkJarsFolder = new File(sparkHome + "/jars");
      boolean sparkRepl211Exists =
              Stream.of(sparkJarsFolder.listFiles()).anyMatch(file -> file.getName().contains("spark-repl_2.11"));
      if (sparkRepl211Exists) {
        return "2.11";
      } else {
        return "2.10";
      }
    }
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
    if (isYarnMode()) {
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
        mergeSparkProperty(sparkProperties, "spark.yarn.dist.archives",
                sparkRPath.getAbsolutePath() + "#sparkr");
      } else {
        LOGGER.warn("sparkr.zip is not found, SparkR may not work.");
      }
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
    if (value.contains("'") && value.contains("\"")) {
      throw new RuntimeException("Spark property value could not contain both \" and '");
    } else if (value.contains("'")) {
      return "\"" + value + "\"";
    } else {
      return "'" + value + "'";
    }
  }

}
