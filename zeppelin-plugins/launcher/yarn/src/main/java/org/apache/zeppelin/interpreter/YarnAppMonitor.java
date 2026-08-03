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

package org.apache.zeppelin.interpreter;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterManagedProcess;
import org.apache.zeppelin.scheduler.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class launches a thread to check YARN application status regularly. */
public class YarnAppMonitor {

  private static final Logger LOGGER = LoggerFactory.getLogger(YarnAppMonitor.class);
  private static YarnAppMonitor instance;

  private ScheduledExecutorService executor;
  private YarnClient yarnClient;
  private ConcurrentHashMap<ApplicationId, RemoteInterpreterManagedProcess> apps;

  public static synchronized YarnAppMonitor get() {
    if (instance == null) {
      instance = new YarnAppMonitor();
    }
    return instance;
  }

  private YarnAppMonitor() {
    try {
      this.yarnClient = YarnClient.createYarnClient();
      YarnConfiguration yarnConf = new YarnConfiguration();
      yarnConf.setClassLoader(YarnAppMonitor.class.getClassLoader());
      // Disable the timeline service because this client only queries application state.
      yarnConf.set("yarn.timeline-service.enabled", "false");
      yarnClient.init(yarnConf);
      yarnClient.start();
      this.executor =
          Executors.newSingleThreadScheduledExecutor(
              new NamedThreadFactory("YarnAppsMonitor-Thread"));
      this.apps = new ConcurrentHashMap<>();
      int monitorInterval =
          ZeppelinConfiguration.getStaticInt(
              ConfVars.ZEPPELIN_INTERPRETER_YARN_MONITOR_INTERVAL_SECS);
      this.executor.scheduleAtFixedRate(
          this::checkApplications,
          monitorInterval,
          monitorInterval,
          TimeUnit.SECONDS);

      LOGGER.info("YarnAppMonitor is started");
    } catch (Throwable e) {
      LOGGER.warn("Fail to initialize YarnAppMonitor", e);
    }
  }

  private void checkApplications() {
    try {
      Iterator<Map.Entry<ApplicationId, RemoteInterpreterManagedProcess>> iter =
          apps.entrySet().iterator();
      while (iter.hasNext()) {
        Map.Entry<ApplicationId, RemoteInterpreterManagedProcess> entry = iter.next();
        ApplicationId appId = entry.getKey();
        RemoteInterpreterManagedProcess interpreterManagedProcess = entry.getValue();
        ApplicationReport appReport = yarnClient.getApplicationReport(appId);
        YarnApplicationState applicationState = appReport.getYarnApplicationState();
        if (applicationState == YarnApplicationState.FAILED
            || applicationState == YarnApplicationState.KILLED) {
          interpreterManagedProcess.processStopped(
              "Yarn diagnostics: " + appReport.getDiagnostics());
          iter.remove();
          LOGGER.info(
              "Remove {} from YarnAppMonitor, because its state is {}",
              appId,
              applicationState);
        } else if (applicationState == YarnApplicationState.FINISHED) {
          iter.remove();
          LOGGER.info(
              "Remove {} from YarnAppMonitor, because its state is {}",
              appId,
              applicationState);
        }
      }
    } catch (Exception e) {
      LOGGER.warn("Fail to check yarn app status", e);
    }
  }

  public void addYarnApp(
      ApplicationId appId, RemoteInterpreterManagedProcess interpreterManagedProcess) {
    LOGGER.info("Add {} to YarnAppMonitor", appId);
    this.apps.put(appId, interpreterManagedProcess);
  }
}
