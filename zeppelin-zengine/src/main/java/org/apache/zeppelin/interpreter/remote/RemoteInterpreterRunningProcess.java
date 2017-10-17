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

import org.apache.zeppelin.helium.ApplicationEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class connects to existing process
 */
public class RemoteInterpreterRunningProcess extends RemoteInterpreterProcess {
  private final Logger logger = LoggerFactory.getLogger(RemoteInterpreterRunningProcess.class);
  private final String host;
  private final int port;

  public RemoteInterpreterRunningProcess(
      int connectTimeout,
      String host,
      int port
  ) {
    super(connectTimeout);
    this.host = host;
    this.port = port;
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
  public void start(String userName, Boolean isUserImpersonate) {
    // assume process is externally managed. nothing to do
  }

  @Override
  public void stop() {
    // assume process is externally managed. nothing to do
  }

  @Override
  public boolean isRunning() {
    return RemoteInterpreterUtils.checkIfRemoteEndpointAccessible(getHost(), getPort());
  }
}
