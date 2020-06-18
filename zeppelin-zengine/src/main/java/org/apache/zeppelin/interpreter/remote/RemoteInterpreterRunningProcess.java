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

import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class connects to existing process
 */
public class RemoteInterpreterRunningProcess extends RemoteInterpreterProcess {
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteInterpreterRunningProcess.class);

  private final String host;
  private final int port;
  private final String interpreterSettingName;
  private final String interpreterGroupId;
  private final boolean isRecovery;

  public RemoteInterpreterRunningProcess(
      String interpreterSettingName,
      String interpreterGroupId,
      int connectTimeout,
      String intpEventServerHost,
      int intpEventServerPort,
      String host,
      int port,
      boolean isRecovery) {
    super(connectTimeout, intpEventServerHost, intpEventServerPort);
    this.interpreterSettingName = interpreterSettingName;
    this.interpreterGroupId = interpreterGroupId;
    this.host = host;
    this.port = port;
    this.isRecovery = isRecovery;
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
  public String getInterpreterSettingName() {
    return interpreterSettingName;
  }

  @Override
  public String getInterpreterGroupId() {
    return interpreterGroupId;
  }

  @Override
  public void start(String userName) {
    // assume process is externally managed. nothing to do
  }

  @Override
  public void stop() {
    // assume process is externally managed. nothing to do. But will kill it
    // when you want to force stop it. ENV ZEPPELIN_FORCE_STOP control that.
    if (System.getenv("ZEPPELIN_FORCE_STOP") != null || isRecovery) {
      if (isRunning()) {
        LOGGER.info("Kill interpreter process of interpreter group: {}", interpreterGroupId);
        try {
          callRemoteFunction(client -> {
            client.shutdown();
            return null;
          });
        } catch (Exception e) {
          LOGGER.warn("ignore the exception when shutting down interpreter process.", e);
        }

        // Shutdown connection
        shutdown();
        LOGGER.info("Remote process of interpreter group: {} is terminated.", getInterpreterGroupId());
      }
    }
  }

  @Override
  public boolean isRunning() {
    return RemoteInterpreterUtils.checkIfRemoteEndpointAccessible(getHost(), getPort());
  }

  @Override
  public void processStarted(int port, String host) {
    // assume process is externally managed. nothing to do
  }

  @Override
  public String getErrorMessage() {
    return null;
  }
}
