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

package org.apache.zeppelin.interpreter.remote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * This class manages start / stop of remote interpreter process
 */
public abstract class RemoteInterpreterManagedProcess extends RemoteInterpreterProcess {
  private static final Logger LOGGER = LoggerFactory.getLogger(
      RemoteInterpreterManagedProcess.class);


  private final String interpreterPortRange;

  private String host = null;
  private int port = -1;
  private final String interpreterDir;
  private final String localRepoDir;
  private final String interpreterSettingName;
  private final String interpreterGroupId;
  private final boolean isUserImpersonated;
  private String errorMessage;

  private Map<String, String> env;

  public RemoteInterpreterManagedProcess(
      int intpEventServerPort,
      String intpEventServerHost,
      String interpreterPortRange,
      String intpDir,
      String localRepoDir,
      Map<String, String> env,
      int connectTimeout,
      int connectionPoolSize,
      String interpreterSettingName,
      String interpreterGroupId,
      boolean isUserImpersonated) {
    super(connectTimeout, connectionPoolSize, intpEventServerHost, intpEventServerPort);
    this.interpreterPortRange = interpreterPortRange;
    this.env = env;
    this.interpreterDir = intpDir;
    this.localRepoDir = localRepoDir;
    this.interpreterSettingName = interpreterSettingName;
    this.interpreterGroupId = interpreterGroupId;
    this.isUserImpersonated = isUserImpersonated;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public void stop() {
    LOGGER.info("Stop interpreter process for interpreter group: {}", getInterpreterGroupId());
    try {
      callRemoteFunction(client -> {
        client.shutdown();
        return null;
      });
      // Shutdown connection
      shutdown();
    } catch (Exception e) {
      LOGGER.warn("ignore the exception when shutting down", e);
    }
  }

  @Override
  public void processStarted(int port, String host) {
    this.port = port;
    this.host = host;
  }

  // called when remote interpreter process is stopped, e.g. YarnAppsMonitor will call this
  // after detecting yarn app is killed/failed.
  public void processStopped(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Map<String, String> getEnv() {
    return env;
  }

  public String getLocalRepoDir() {
    return localRepoDir;
  }

  public String getInterpreterDir() {
    return interpreterDir;
  }

  public String getIntpEventServerHost() {
    return intpEventServerHost;
  }

  public String getInterpreterPortRange() {
    return interpreterPortRange;
  }

  @Override
  public String getInterpreterSettingName() {
    return interpreterSettingName;
  }

  @Override
  public String getInterpreterGroupId() {
    return interpreterGroupId;
  }

  public boolean isUserImpersonated() {
    return isUserImpersonated;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }
}
