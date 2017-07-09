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

package org.apache.zeppelin.cluster.yarn;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.ApplicationConstants.Environment;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventPoller;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;

import static org.apache.hadoop.yarn.api.records.YarnApplicationState.FAILED;
import static org.apache.hadoop.yarn.api.records.YarnApplicationState.FINISHED;
import static org.apache.hadoop.yarn.api.records.YarnApplicationState.KILLED;
import static org.apache.hadoop.yarn.api.records.YarnApplicationState.RUNNING;
import static org.apache.zeppelin.cluster.yarn.YarnUtils.addLocalResource;
import static org.apache.zeppelin.cluster.yarn.YarnUtils.getPathsFromDirPath;
import static org.apache.zeppelin.cluster.Constants.ZEPPELIN_YARN_APPLICATION_TYPE_DEFAULT;
import static org.apache.zeppelin.cluster.Constants.ZEPPELIN_YARN_APPLICATION_TYPE_KEY;
import static org.apache.zeppelin.cluster.Constants.ZEPPELIN_YARN_MEMORY_DEFAULT;
import static org.apache.zeppelin.cluster.Constants.ZEPPELIN_YARN_MEMORY_KEY;
import static org.apache.zeppelin.cluster.Constants.ZEPPELIN_YARN_PRIORITY_DEFAULT;
import static org.apache.zeppelin.cluster.Constants.ZEPPELIN_YARN_PRIORITY_KEY;
import static org.apache.zeppelin.cluster.Constants.ZEPPELIN_YARN_QUEUE_DEFAULT;
import static org.apache.zeppelin.cluster.Constants.ZEPPELIN_YARN_QUEUE_KEY;
import static org.apache.zeppelin.cluster.Constants.ZEPPELIN_YARN_VCORES_DEFAULT;
import static org.apache.zeppelin.cluster.Constants.ZEPPELIN_YARN_VCORES_KEY;

/**
 *
 */
public class RemoteInterpreterYarnProcess extends RemoteInterpreterProcess {

  private static final Logger logger = LoggerFactory.getLogger(RemoteInterpreterYarnProcess.class);

  private final YarnClient yarnClient;
  private final String homeDir;
  private final String interpreterDir;
  private final Configuration configuration;
  private final String name;
  private final String group;
  private final Map<String, String> env;
  private final Properties properties;

  private CountDownLatch waitingInitialized;
  private List<Path> interpreterLibPaths;

  private ApplicationId applicationId;
  private boolean isRunning = false;
  private ScheduledFuture monitor;
  private YarnApplicationState oldState;

  private String host = null;
  private int port = -1;

  private String extraClasspath = null;

  RemoteInterpreterYarnProcess(int connectTimeout, RemoteInterpreterProcessListener listener,
      ApplicationEventListener appListener, YarnClient yarnClient, String homeDir,
      String interpreterDir, Configuration configuration, String name, String group,
      Map<String, String> env, Properties properties) {
    super(new RemoteInterpreterEventPoller(listener, appListener), connectTimeout);
    this.yarnClient = yarnClient;
    this.homeDir = homeDir;
    this.interpreterDir = interpreterDir;
    this.configuration = configuration;
    this.name = name;
    this.group = group;
    this.env = env;
    this.properties = properties;

    this.waitingInitialized = new CountDownLatch(1);
    this.interpreterLibPaths = Lists.newArrayList(
        Paths.get(homeDir, "zeppelin-interpreter", "target"),
        Paths.get(homeDir, "zeppelin-interpreter", "target", "lib"),
        Paths.get(homeDir, "zeppelin-cluster", "common", "target"),
        Paths.get(homeDir, "zeppelin-cluster", "common", "target", "lib"),
        Paths.get(homeDir, "zeppelin-cluster", "yarn", "target"),
        Paths.get(homeDir, "zeppelin-cluster", "yarn", "target", "lib"),
        Paths.get(homeDir, "conf", "yarn", "log4j.properties"),
        Paths.get(homeDir, "lib", "interpreter"),
        Paths.get(homeDir, "lib", "cluster", "common"),
        Paths.get(homeDir, "lib", "cluster", "yarn"));
  }

  @Override
  public String getHost() {
    return host;
  }

  private void setHost(String host) {
    this.host = host;
  }

  @Override
  public int getPort() {
    return port;
  }

  private void setPort(int port) {
    this.port = port;
  }

  @Override
  public void start(String userName, Boolean isUserImpersonate) {
    if (isUserImpersonate) {

    }
    try {
      YarnClientApplication app = yarnClient.createApplication();
      ApplicationSubmissionContext appContext = app.getApplicationSubmissionContext();

      // put this info idApplicationIdMap
      ApplicationId applicationId = appContext.getApplicationId();

      appContext.setKeepContainersAcrossApplicationAttempts(false);
      appContext.setApplicationName(name);

      Map<String, String> env = Maps.newHashMap();

      ArrayList<String> classpathStrings = Lists.newArrayList(configuration
          .getStrings(YarnConfiguration.YARN_APPLICATION_CLASSPATH,
              YarnConfiguration.DEFAULT_YARN_CROSS_PLATFORM_APPLICATION_CLASSPATH));
      classpathStrings.add(0, "./*");
      classpathStrings.add(0, ApplicationConstants.Environment.CLASSPATH.$$());
      classpathStrings.add("./log4j.properties");
      if (isHadoopConfSet()) {
        classpathStrings.add(System.getenv("HADOOP_CONF_DIR"));
      }
      if (null != extraClasspath) {
        classpathStrings.add(extraClasspath);
      }

      String classpathEnv =
          Joiner.on(ApplicationConstants.CLASS_PATH_SEPARATOR).join(classpathStrings);

      logger.debug("classpath: {}", classpathEnv);

      env.putAll(this.env);
      env.put("CLASSPATH", classpathEnv);

      if (isHadoopConfSet()) {
        env.put("HADOOP_CONF_DIR", System.getenv("HADOOP_CONF_DIR"));
      }

      Map<String, LocalResource> localResources = new HashMap<>();

      FileSystem fileSystem = FileSystem.get(configuration);

      Path interpreterDir = getInterpreterRelativePath();
      List<Path> interpreterPaths = getPathsFromDirPath(interpreterDir);

      if (isSparkInterpreter()) {
        // For pyspark
        interpreterDir = isSparkHomeSet() ? Paths.get(this.env.get("SPARK_HOME"), "python", "lib")
            : getInterpreterRelativePath("pyspark");
        List<Path> pythonLibPath = getPathsFromDirPath(interpreterDir);
        interpreterPaths.addAll(pythonLibPath);

        // Set PYSPARK_ARCHIVES_PATH
        List<String> pythonLibPaths = new ArrayList<>();
        for (Path p : pythonLibPath) {
          String pathFilenameString = p.getFileName().toString();
          if (pathFilenameString.endsWith(".zip")) {
            pythonLibPaths.add(pathFilenameString);
          }
        }
        env.put("PYSPARK_ARCHIVES_PATH", Joiner.on(",").join(pythonLibPaths));

        interpreterDir = isSparkHomeSet() ? Paths.get(this.env.get("SPARK_HOME"), "jars")
            : getInterpreterRelativePath("dep");
        List<Path> jarPaths = getPathsFromDirPath(interpreterDir);
        interpreterPaths.addAll(jarPaths);

        String dstPath = "hdfs:///user/" + userName + "/.zeppelin/spark_" + name + "_jars.zip";
        if (!fileSystem.exists(new org.apache.hadoop.fs.Path(dstPath))) {
          Path jarsArchive = Files.createTempFile("spark_jars", ".zip");
          try (ZipOutputStream jarsStream = new ZipOutputStream(
              new FileOutputStream(jarsArchive.toFile()))) {
            jarsStream.setLevel(0);

            for (Path p : jarPaths) {
              jarsStream.putNextEntry(new ZipEntry(p.getFileName().toString()));
              Files.copy(p, jarsStream);
              jarsStream.closeEntry();
            }
          }

          fileSystem.copyFromLocalFile(new org.apache.hadoop.fs.Path(jarsArchive.toUri()),
              new org.apache.hadoop.fs.Path(dstPath));
          Files.deleteIfExists(jarsArchive);
        }
        properties.setProperty("spark.yarn.archive", dstPath);
      } else if (isPythonInterpreter()) {
        interpreterDir = getInterpreterRelativePath();
        List<Path> pythonLibPath = getPathsFromDirPath(interpreterDir);
        interpreterPaths.addAll(pythonLibPath);

        // set PYTHONPATH
        List<String> pythonLibPaths = new ArrayList<>();
        for (Path p : pythonLibPath) {
          String pathFilenameString = p.getFileName().toString();
          if (pathFilenameString.endsWith(".zip")) {
            pythonLibPaths.add(Environment.PWD.$$() + File.separator + pathFilenameString);
          }
        }
        env.put("PYTHONPATH", Joiner.on(File.pathSeparator).join(pythonLibPaths));
      }

      // For pyspark and python
      List<Path> zeppelinPythonLibPaths = getPathsFromDirPath(
          Paths.get(this.interpreterDir, "lib", "python"));
      interpreterPaths.addAll(zeppelinPythonLibPaths);
      List<String> distributedZeppelinPythonLibPaths = Lists.newArrayList();
      for (Path p : zeppelinPythonLibPaths) {
        if (p.getFileName().toString().endsWith(".py")) {
          distributedZeppelinPythonLibPaths.add(
              "python" + File.separator + p.getFileName().toString());
        }
      }

      if (isSparkInterpreter()) {
        String pyFiles =
            (env.containsKey("PYSPARK_ARCHIVES_PATH") ? env.get("PYSPARK_ARCHIVES_PATH") + "," : "")
                + Joiner.on(",").join(distributedZeppelinPythonLibPaths);
        properties.setProperty("spark.submit.pyFiles", pyFiles);
      } else if (isPythonInterpreter()) {
        List<String> pwdDistributedZeppelinPythonLibPaths = Lists.newArrayList();
        for (String p : distributedZeppelinPythonLibPaths) {
          pwdDistributedZeppelinPythonLibPaths.add(Environment.PWD.$$() + File.separator + p);
        }
        String path = env.get("PYTHONPATH") + File.pathSeparator + Joiner.on(File.pathSeparator)
            .join(pwdDistributedZeppelinPythonLibPaths);
        env.put("PYTHONPATH", "./" + File.pathSeparator + "./python" + File.pathSeparator + path);
      }

      for (Path p : interpreterLibPaths) {
        interpreterPaths.addAll(getPathsFromDirPath(p));
      }

      Map<String, String> systemEnv = System.getenv();
      if (systemEnv.containsKey("HADOOP_CONF_DIR")) {
        interpreterDir = Paths.get(systemEnv.get("HADOOP_CONF_DIR"));
        interpreterPaths.addAll(getPathsFromDirPath(interpreterDir));
      }

      addLocalResource(fileSystem, String.valueOf(applicationId.getId()), localResources,
          interpreterPaths);

      List<String> vargs = Lists.newArrayList();

      if (null != System.getenv("JAVA_HOME")) {
        vargs.add(ApplicationConstants.Environment.JAVA_HOME.$$() + "/bin/java");
      } else {
        vargs.add("java");
      }

      int memory;
      int defaultMemory = Integer.valueOf(ZEPPELIN_YARN_MEMORY_DEFAULT);

      try {
        memory = Integer.valueOf(properties.getProperty(ZEPPELIN_YARN_MEMORY_KEY));
      } catch (NumberFormatException e) {
        memory = defaultMemory;
      }

      // For spark
      if (isSparkInterpreter()) {
        // Assume that spark.master is "local" without any setting. It, however, doesn't guarantee
        // spark works without "spark.master"
        String master = properties
            .getProperty("master", properties.getProperty("spark.master", "local[*]"));
        String deployMode = properties.getProperty("spark.submit.deployMode", "client");

        if (master.contains("yarn") && (master.contains("client") || deployMode
            .contains("client")) && properties.containsKey("spark.yarn.am.memory")) {
          memory = convertSparkMemoryFormat(properties.getProperty("spark.yarn.am.memory"));
        } else if (properties.containsKey("spark.driver.memory")) {
          memory = convertSparkMemoryFormat(properties.getProperty("spark.driver.memory"));
        }

        if (memory < defaultMemory) {
          memory = defaultMemory;
        }
      }

      vargs.add("-Xmx" + memory + "m");

      vargs.add("-Dlog4j.configuration=log4j.properties");

      vargs.add(YarnRemoteInterpreterServer.class.getName());

      vargs.add("1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/interpreter.stdout");
      vargs.add("2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/interpreter.stderr");

      String command = Joiner.on(" ").join(vargs);
      logger.debug("command: {}", command);

      List<String> commands = Lists.newArrayList(command);

      ContainerLaunchContext amContainer =
          ContainerLaunchContext.newInstance(localResources, env, commands, null, null, null);

      int vCores;
      int vCoresDefault = Integer.valueOf(ZEPPELIN_YARN_VCORES_DEFAULT);

      try {
        vCores = Integer.valueOf(properties.getProperty(ZEPPELIN_YARN_VCORES_KEY));
      } catch (NumberFormatException e) {
        vCores = vCoresDefault;
      }

      Resource capability = Resource.newInstance(memory, vCores);
      appContext.setResource(capability);

      appContext.setAMContainerSpec(amContainer);

      int priority;
      int priorityDefault = Integer.valueOf(ZEPPELIN_YARN_PRIORITY_DEFAULT);

      try {
        priority = Integer.valueOf(properties.getProperty(ZEPPELIN_YARN_PRIORITY_KEY));
      } catch (NumberFormatException e) {
        priority = priorityDefault;
      }

      Priority pri = Priority.newInstance(priority);
      appContext.setPriority(pri);

      appContext
          .setQueue(properties.getProperty(ZEPPELIN_YARN_QUEUE_KEY, ZEPPELIN_YARN_QUEUE_DEFAULT));

      appContext.setApplicationType(properties
          .getProperty(ZEPPELIN_YARN_APPLICATION_TYPE_KEY, ZEPPELIN_YARN_APPLICATION_TYPE_DEFAULT));

      this.applicationId = appContext.getApplicationId();

      yarnClient.submitApplication(appContext);
      monitor = Client.scheduledExecutorService
          .scheduleAtFixedRate(new ApplicationMonitor(), 1, 1, TimeUnit.SECONDS);

      waitingInitialized.await(5, TimeUnit.MINUTES);
      if (oldState != RUNNING) {
        stop();
        ApplicationAttemptId applicationAttemptId =
            yarnClient.getApplicationReport(applicationId).getCurrentApplicationAttemptId();
        logger.error(yarnClient.getApplicationAttemptReport(applicationAttemptId).getDiagnostics());
        throw new InterpreterException("Failed to initialize yarn application: " + applicationId);
      }

    } catch (YarnException | IOException | InterruptedException e) {
      stop();
      throw new InterpreterException(e);
    }
  }

  private boolean isSparkInterpreter() {
    return "spark".equals(group);
  }

  private boolean isPythonInterpreter() {
    return "python".equals(group);
  }

  private boolean isSparkHomeSet() {
    return this.env.containsKey("SPARK_HOME");
  }

  private boolean isHadoopConfSet() {
    return null != System.getenv("HADOOP_CONF_DIR");
  }

  private int convertSparkMemoryFormat(String memoryFormat) {
    int memory = Integer.valueOf(memoryFormat.substring(0, memoryFormat.length() - 1));
    String unit = "" + memoryFormat.charAt(memoryFormat.length() - 1);

    switch (unit) {
      case "k":
        return memory / 1024;
      case "m":
        return memory;
      case "g":
        return memory * 1024;
      case "t":
        return memory * 1024 * 1024;
      default:
        return 0;
    }
  }

  private Path getInterpreterRelativePath(String... dirNames) {
    return Paths.get(this.interpreterDir, dirNames);
  }

  @Override
  public void stop() {
    logger.info("called stop");
    isRunning = false;
    if (null != oldState && oldState != FINISHED && oldState != FAILED && oldState != KILLED) {
      try {
        yarnClient.killApplication(applicationId);
      } catch (YarnException | IOException e) {
        logger.debug("error while killing application: {}", applicationId);
      }
    }
    oldState = null;
    monitor.cancel(false);
    waitingInitialized = new CountDownLatch(1);
  }

  @Override
  public boolean isRunning() {
    return isRunning;
  }

  private void setRunning(boolean running) {
    this.isRunning = running;
  }

  // For Testing
  // It should be called before calling start()
  void setExtraClasspath(String extraClasspath) {
    this.extraClasspath = extraClasspath;
  }

  private class ApplicationMonitor implements Runnable {

    @Override
    public void run() {
      try {
        ApplicationReport applicationReport = yarnClient.getApplicationReport(applicationId);
        YarnApplicationState curState = applicationReport.getYarnApplicationState();
        switch (curState) {
          case NEW:
          case NEW_SAVING:
          case SUBMITTED:
          case ACCEPTED:
            if (null == oldState || !oldState.equals(curState)) {
              logger.info("new application added. applicationId: {}", applicationId);
              setRunning(false);
              setHost("N/A");
              setPort(-1);
            }
            oldState = curState;
            break;
          case RUNNING:
            if (!RUNNING.equals(oldState)) {
              String host = applicationReport.getHost();
              int port = applicationReport.getRpcPort();
              logger
                  .info("applicationId {} started. Host: {}, port: {}", applicationId, host
                      , port);
              oldState = curState;
              setHost(host);
              setPort(port);
              setRunning(true);
              waitingInitialized.countDown();
            }
            break;
          case FINISHED:
          case FAILED:
          case KILLED:
            if (!curState.equals(oldState)) {
              logger.info("applicationId {} {} with final Status {}", applicationId,
                  curState.toString().toLowerCase(),
                  applicationReport.getFinalApplicationStatus());
              oldState = curState;
              waitingInitialized.countDown();
              stop();
              //TODO(jl): Handle it!!
            }
            break;
        }
      } catch (YarnException | IOException e) {
        logger.debug("Error occurs while fetching status of {}", applicationId, e);
      }
    }
  }
}
