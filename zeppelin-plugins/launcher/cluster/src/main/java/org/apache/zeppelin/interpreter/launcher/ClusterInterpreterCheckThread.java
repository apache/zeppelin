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

import org.apache.zeppelin.cluster.ClusterCallback;
import org.apache.zeppelin.cluster.ClusterManagerServer;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

import static org.apache.zeppelin.cluster.meta.ClusterMeta.INTP_TSERVER_HOST;
import static org.apache.zeppelin.cluster.meta.ClusterMeta.INTP_TSERVER_PORT;

// Metadata registered in the cluster by the interpreter process,
// Keep the interpreter process started
public class ClusterInterpreterCheckThread extends Thread {
  private static final Logger LOGGER
      = LoggerFactory.getLogger(ClusterInterpreterCheckThread.class);

  private InterpreterClient intpProcess;
  private String intpGroupId;
  private int connectTimeout;

  ClusterInterpreterCheckThread(InterpreterClient intpProcess,
                                String intpGroupId,
                                int connectTimeout) {
    this.intpProcess = intpProcess;
    this.intpGroupId = intpGroupId;
    this.connectTimeout = connectTimeout;
  }

  @Override
  public void run() {
    LOGGER.info("ClusterInterpreterCheckThread run() >>>");

    ClusterManagerServer clusterServer = ClusterManagerServer.getInstance(
            ZeppelinConfiguration.create());

    clusterServer.getIntpProcessStatus(intpGroupId, connectTimeout,
        new ClusterCallback<HashMap<String, Object>>() {
          @Override
          public InterpreterClient online(HashMap<String, Object> result) {
            String intpTSrvHost = (String) result.get(INTP_TSERVER_HOST);
            int intpTSrvPort = (int) result.get(INTP_TSERVER_PORT);
            LOGGER.info("Found cluster interpreter {}:{}", intpTSrvHost, intpTSrvPort);

            if (intpProcess instanceof DockerInterpreterProcess) {
              ((DockerInterpreterProcess) intpProcess).processStarted(intpTSrvPort, intpTSrvHost);
            } else if (intpProcess instanceof ClusterInterpreterProcess) {
              ((ClusterInterpreterProcess) intpProcess).processStarted(intpTSrvPort, intpTSrvHost);
            } else {
              LOGGER.error("Unknown type !");
            }

            return null;
          }

          @Override
          public void offline() {
            LOGGER.error("Can not found cluster interpreter!");
          }
        });

    LOGGER.info("ClusterInterpreterCheckThread run() <<<");
  }
}
