/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter.launcher;

import java.io.IOException;
import java.util.Map;

import org.apache.zeppelin.interpreter.remote.ExecRemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;

public class ClusterInterpreterProcess extends ExecRemoteInterpreterProcess {

  public ClusterInterpreterProcess(
      String intpRunner,
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

    super(intpEventServerPort,
      intpEventServerHost,
      interpreterPortRange,
      intpDir,
      localRepoDir,
      env,
      connectTimeout,
      connectionPoolSize,
      interpreterSettingName,
      interpreterGroupId,
      isUserImpersonated,
      intpRunner);
  }

  @Override
  public void start(String userName) throws IOException {
    super.start(userName);
  }

  @Override
  public boolean isRunning() {
    if (RemoteInterpreterUtils.checkIfRemoteEndpointAccessible(getHost(), getPort())) {
      return true;
    }
    return false;
  }

  @Override
  public String getErrorMessage() {
    return null;
  }
}
