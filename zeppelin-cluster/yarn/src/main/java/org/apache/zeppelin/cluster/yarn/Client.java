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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.Token;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zeppelin.cluster.ApplicationCallbackHandler;
import org.apache.zeppelin.cluster.ClusterManager;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterSetting;

/**
 *
 */
public class Client extends ClusterManager {

  private static final Logger logger = LoggerFactory.getLogger(Client.class);
  private static final ScheduledExecutorService scheduledExecutorService =
      Executors.newScheduledThreadPool(1);

  private Configuration configuration;
  private YarnClient yarnClient;
  private boolean started;

  private String interpreterLibDir;

  /**
   * `id` is a unique key to figure out Application
   */
  private Map<String, ApplicationId> idApplicationIdMap;
  private Map<ApplicationId, YarnApplicationState> appStatusMap;

  public Client(ZeppelinConfiguration zeppelinConfiguration,
      ApplicationCallbackHandler applicationHandler) {
    super(zeppelinConfiguration, applicationHandler);

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

      this.scheduledExecutorService
          .scheduleAtFixedRate(new ApplicationMonitor(), 1, 1, TimeUnit.SECONDS);

      this.interpreterLibDir = Joiner.on(Path.SEPARATOR).join(new String[] {
          zeppelinConfiguration.getBasePath(), "zeppelin-interpreter", "target"
      });

      try {
        Files.newDirectoryStream(Paths.get(zeppelinConfiguration.getBasePath(), "zeppelin-interpreter", "target"),"*.jar");
      } catch (IOException e) {
        e.printStackTrace();
      }

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
  public RemoteInterpreterProcess createInterpreter(String id,
      InterpreterSetting interpreterSetting, int connectTimeout,
      RemoteInterpreterProcessListener listener,
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
      appContext.setApplicationName(interpreterSetting.getName());

      Map<String, LocalResource> localResources = new HashMap<>();

      FileSystem fileSystem = FileSystem.get(configuration);

      String interpreterDir = getInterpreterRelativeDir(interpreterSetting.getGroup());
      String interpreterLib = getInterpreterRelativeDir("lib");

      return new RemoteInterpreterYarnProcess(connectTimeout, listener, appListener, yarnClient,
          appContext);
    } catch (YarnException | IOException e) {
      throw new InterpreterException(e);
    }
  }

  private String getInterpreterRelativeDir(String dirName) {
    return Joiner.on(Path.SEPARATOR).join(
        new String[]{zeppelinConfiguration.getInterpreterDir(), dirName});
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

  private class ApplicationMonitor implements Runnable {

    @Override
    public void run() {
      ArrayList<String> removedIds = new ArrayList<>();
      for (Map.Entry<String, ApplicationId> entry : idApplicationIdMap.entrySet()) {
        String id = entry.getKey();
        ApplicationId applicationId = entry.getValue();
        try {
          ApplicationReport applicationReport = yarnClient.getApplicationReport(applicationId);
          YarnApplicationState curState = applicationReport.getYarnApplicationState();
          YarnApplicationState oldState = appStatusMap.get(applicationId);
          switch (curState) {
              case NEW:
              case NEW_SAVING:
              case SUBMITTED:
              case ACCEPTED:
                if (null == oldState) {
                  logger.info("new application added. Id: {}, applicationId: {}", id,
                      applicationId);
                }
                appStatusMap.put(applicationId, curState);
                break;
              case RUNNING:
                if (!YarnApplicationState.RUNNING.equals(oldState)) {
                  logger.info("id {} started with applicationId {}", id, applicationId);
                  appStatusMap.put(applicationId, curState);
                  applicationCallbackHandler
                      .onStarted(applicationReport.getHost(), applicationReport.getRpcPort());
                }
                break;
              case FINISHED:
                if (!YarnApplicationState.FINISHED.equals(oldState)) {
                  logger.info("id {}, applicationId {} finished with final Status {}", id,
                      applicationId, applicationReport.getFinalApplicationStatus());
                  applicationCallbackHandler.onTerminated(id);
                  removedIds.add(id);
                }
                break;
              case FAILED:
                if (!YarnApplicationState.FAILED.equals(oldState)) {
                  logger
                      .info("id {}, applicationId {} failed with final Status {}", id,
                          applicationId, applicationReport.getFinalApplicationStatus());
                  applicationCallbackHandler.onTerminated(id);
                  removedIds.add(id);
                }
                break;
              case KILLED:
                if (!YarnApplicationState.KILLED.equals(oldState)) {
                  logger
                      .info("id {}, applicationId {} killed with final Status {}", id,
                          applicationId, applicationReport.getFinalApplicationStatus());
                  applicationCallbackHandler.onTerminated(id);
                  removedIds.add(id);
                }
                break;
          }
        } catch (YarnException | IOException e) {
          logger.debug("Error occurs while fetching status of {}", applicationId, e);
        }

        for (String removedId : removedIds) {
          idApplicationIdMap.remove(removedId);
        }
      }
    }
  }
}
