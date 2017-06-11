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
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.Token;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zeppelin.cluster.ClusterManager;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.InterpreterException;
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

  /**
   * `id` is a unique key to figure out Application
   */
  private Map<String, ApplicationId> idApplicationIdMap;

  public Client() {
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
      Map<String, String> env, Properties properties, int connectTimeout,
      RemoteInterpreterProcessListener listener, ApplicationEventListener appListener,
      String homeDir, String interpreterDir)
      throws InterpreterException {
    if (!started) {
      start();
    }

    return new RemoteInterpreterYarnProcess(connectTimeout, listener, appListener, yarnClient,
        homeDir, interpreterDir, configuration, name, groupName, env, properties);
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
}
