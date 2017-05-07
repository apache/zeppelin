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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.Token;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zeppelin.cluster.ClusterManager;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;

/**
 *
 */
public class Client extends ClusterManager {

  private static final Logger logger = LoggerFactory.getLogger(Client.class);
  public static final ScheduledExecutorService scheduledExecutorService =
      Executors.newScheduledThreadPool(1);

  private Configuration configuration;
  private YarnClient yarnClient;
  private boolean started;

  private List<java.nio.file.Path> interpreterRelatedPaths;

  /**
   * `id` is a unique key to figure out Application
   */
  private Map<String, ApplicationId> idApplicationIdMap;
  private Map<ApplicationId, YarnApplicationState> appStatusMap;

  public Client(ZeppelinConfiguration zeppelinConfiguration) {
    super(zeppelinConfiguration);

    this.started = false;
  }

  public synchronized void start() {
    if (!started) { // it will help when calling it multiple times from different threads
      logger.info("Start to initialize yarn client");
      this.configuration = new Configuration();
      this.yarnClient = YarnClient.createYarnClient();
      this.yarnClient.init(configuration);
      this.yarnClient.start();

      closeAllApplications();

      this.idApplicationIdMap = new ConcurrentHashMap<>();

      this.interpreterRelatedPaths = Lists.newArrayList(
          Paths.get(zeppelinConfiguration.getHome(), "zeppelin-interpreter", "target")
              .toAbsolutePath(),
          Paths.get(zeppelinConfiguration.getHome(), "zeppelin-interpreter", "target", "lib")
              .toAbsolutePath(),
          Paths.get(zeppelinConfiguration.getHome(), "lib", "interpreter").toAbsolutePath(),
          Paths.get(zeppelinConfiguration.getHome(), "zeppelin-zengine", "target"));

      this.started = true;
    }
  }

  public synchronized void stop() {
    if (started) {
      logger.info("Stop yarn client");

      closeAllApplications();

      scheduledExecutorService.shutdown();

      this.yarnClient.stop();
      this.started = false;
    }
  }

  @Override
  public RemoteInterpreterProcess createInterpreter(String id, String name, String groupName,
      int connectTimeout, RemoteInterpreterProcessListener listener,
      ApplicationEventListener appListener)
      throws InterpreterException {
    if (!started) {
      start();
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
      classpathStrings.add("${SPARK_HOME}/jars/*");

      String classpathEnv =
          Joiner.on(ApplicationConstants.CLASS_PATH_SEPARATOR).join(classpathStrings);

      logger.debug("classpath: {}", classpathEnv);

      env.put("CLASSPATH", classpathEnv);
      env.put("SPARK_HOME", "/Users/jl/local/src/spark-2.1.0-bin-hadoop2.7");

      Map<String, LocalResource> localResources = new HashMap<>();

      FileSystem fileSystem = FileSystem.get(configuration);

      java.nio.file.Path interpreterDir = getInterpreterRelativePath(groupName);
      List<java.nio.file.Path> interpreterPaths = getPathsFromDirPath(interpreterDir);
      /*if (interpreterSetting.getGroup().equals("spark")) {
        interpreterDir = getInterpreterRelativePath("spark/dep");
        interpreterPaths.addAll(getPathsFromDirPath(interpreterDir));
      }*/
      for (java.nio.file.Path p : interpreterRelatedPaths) {
        interpreterPaths.addAll(getPathsFromDirPath(p));
      }

      addLocalResource(fileSystem, String.valueOf(applicationId.getId()), localResources,
          interpreterPaths);

      List<String> vargs = Lists.newArrayList();

      vargs.add(ApplicationConstants.Environment.JAVA_HOME.$$() + "/bin/java");

      vargs.add("-Xmx1024m");

      vargs.add(YarnRemoteInterpreterServer.class.getName());

      vargs.add("1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/interpreter.stdout");
      vargs.add("2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/interpreter.stderr");

      String command = Joiner.on(" ").join(vargs);
      logger.debug("command: {}", command);

      List<String> commands = Lists.newArrayList(command);

      ContainerLaunchContext amContainer =
          ContainerLaunchContext.newInstance(localResources, env, commands, null, null, null);

      Resource capability = Resource.newInstance(1024, 1);
      appContext.setResource(capability);

      appContext.setAMContainerSpec(amContainer);

      Priority pri = Priority.newInstance(0);
      appContext.setPriority(pri);

      appContext.setQueue("default");

      appContext.setApplicationType("ZEPPELIN INTERPRETER");

      return new RemoteInterpreterYarnProcess(connectTimeout, listener, appListener, yarnClient,
          appContext);
    } catch (YarnException | IOException e) {
      throw new InterpreterException(e);
    }
  }

  private java.nio.file.Path getInterpreterRelativePath(String dirName) {
    return Paths.get(zeppelinConfiguration.getInterpreterDir(), dirName);
  }

  public void releaseResource(String id) {
    if (!started) {
      start();
    }
    ApplicationId applicationId = idApplicationIdMap.get(id);
    try {
      ApplicationReport applicationReport = yarnClient.getApplicationReport(applicationId);
      logApplicationReport(applicationReport);
      yarnClient.killApplication(applicationId);
    } catch (YarnException | IOException e) {
      logger.info("Got error while releasing resource. Resource: {}, applicationId: {}", id,
          applicationId);
    }


  }

  private void closeAllApplications() {
    if (null != idApplicationIdMap && !idApplicationIdMap.isEmpty()) {
      for (ApplicationId applicationId : idApplicationIdMap.values()) {
        try {
          yarnClient.killApplication(applicationId);
        } catch (YarnException | IOException e) {
          logger.debug("You might check the status of applicationId: {}", applicationId);
        }
      }
    }
  }

  private void logApplicationReport(ApplicationReport applicationReport) {
    logger.info("client token", getClientToken(applicationReport));
    logger.info("diagnostics", applicationReport.getDiagnostics());
    logger.info("ApplicationMaster host", applicationReport.getHost());
    logger.info("ApplicationMaster RPC port", String.valueOf(applicationReport.getRpcPort()));
    logger.info("queue", applicationReport.getQueue());
    logger.info("start time", String.valueOf(applicationReport.getStartTime()));
    logger.info("final status", applicationReport.getFinalApplicationStatus().toString());
    logger.info("tracking URL", applicationReport.getTrackingUrl());
    logger.info("user", applicationReport.getUser());
  }

  private String getClientToken(ApplicationReport applicationReport) {
    Token token = applicationReport.getClientToAMToken();
    if (null != token) {
      return token.toString();
    } else {
      return "";
    }
  }

  private List<java.nio.file.Path> getPathsFromDirPath(java.nio.file.Path dirPath) {
    if (null == dirPath || Files.notExists(dirPath) || !Files.isDirectory(dirPath)) {
      return Lists.newArrayList();
    }

    try {
      DirectoryStream<java.nio.file.Path> directoryStream =
          Files.newDirectoryStream(dirPath, new DirectoryStream.Filter<java.nio.file.Path>() {
            @Override
            public boolean accept(java.nio.file.Path entry) throws IOException {
              String filename = entry.toString();
              return filename.endsWith(".jar") || filename.endsWith(".zip");
            }
          });
      return Lists.newArrayList(directoryStream);
    } catch (IOException e) {
      logger.error("Cannot read directory: {}", dirPath.toString(), e);
      return Lists.newArrayList();
    }
  }

  private void addLocalResource(FileSystem fs, String appId,
      Map<String, LocalResource> localResourceMap, List<java.nio.file.Path> paths) {
    for (java.nio.file.Path path : paths) {
      String resourcePath = appId + Path.SEPARATOR + path.getFileName().toString();
      Path dst = new Path(fs.getHomeDirectory(), resourcePath);
      try {
        if (Files.exists(path) && !fs.exists(dst)) {
          fs.copyFromLocalFile(new Path(path.toUri()), dst);
          FileStatus fileStatus = fs.getFileStatus(dst);
          LocalResourceType localResourceType = LocalResourceType.FILE;
          String filename = path.getFileName().toString();
          if (filename.endsWith(".zip")) {
            localResourceType = LocalResourceType.ARCHIVE;
          }
          LocalResource resource = LocalResource
              .newInstance(ConverterUtils.getYarnUrlFromPath(dst), localResourceType,
                  LocalResourceVisibility.APPLICATION, fileStatus.getLen(),
                  fileStatus.getModificationTime());
          localResourceMap.put(filename, resource);
        }
      } catch (IOException e) {
        logger.error("Error while copying resources into hdfs", e);
      }
    }
  }
}
