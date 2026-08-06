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

import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.zeppelin.interpreter.YarnAppMonitor;
import org.apache.zeppelin.interpreter.remote.ProcessLaunchObserver;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterManagedProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Detects an application submitted by a process launcher and registers it for YARN monitoring. */
public class YarnProcessLaunchObserver implements ProcessLaunchObserver {

  private static final Logger LOGGER = LoggerFactory.getLogger(YarnProcessLaunchObserver.class);
  private static final Pattern YARN_APP_PATTERN = Pattern.compile("Submitted application (\\w+)");

  private final BiConsumer<ApplicationId, RemoteInterpreterManagedProcess> appConsumer;

  public YarnProcessLaunchObserver() {
    this((appId, process) -> YarnAppMonitor.get().addYarnApp(appId, process));
  }

  YarnProcessLaunchObserver(
      BiConsumer<ApplicationId, RemoteInterpreterManagedProcess> appConsumer) {
    this.appConsumer = appConsumer;
  }

  @Override
  public void onProcessLaunch(
      String launchOutput, RemoteInterpreterManagedProcess interpreterProcess) {
    Matcher matcher = YARN_APP_PATTERN.matcher(launchOutput);
    if (matcher.find()) {
      String appId = matcher.group(1);
      LOGGER.info("Detected yarn app: {}, add it to YarnAppMonitor", appId);
      appConsumer.accept(ApplicationId.fromString(appId), interpreterProcess);
    }
  }
}
