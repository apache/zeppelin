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

import org.apache.zeppelin.cluster.ClusterManagerClient;
import org.apache.zeppelin.cluster.ClusterManagerServer;
import org.apache.zeppelin.cluster.meta.ClusterMeta;
import org.apache.zeppelin.cluster.meta.ClusterMetaType;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;

import static org.apache.zeppelin.cluster.meta.ClusterMeta.OFFLINE_STATUS;
import static org.apache.zeppelin.cluster.meta.ClusterMeta.ONLINE_STATUS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ClusterMockTest {
  private static Logger LOGGER = LoggerFactory.getLogger(ClusterMockTest.class);

  private static ClusterManagerServer clusterServer = null;
  private static ClusterManagerClient clusterClient = null;

  protected static ZeppelinConfiguration zconf = null;

  static String zServerHost;
  static int zServerPort;
  static final String metaKey = "ClusterMockKey";

  public static void startCluster() throws IOException, InterruptedException {
    LOGGER.info("startCluster >>>");

    zconf = ZeppelinConfiguration.create();

    // Set the cluster IP and port
    zServerHost = RemoteInterpreterUtils.findAvailableHostAddress();
    zServerPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
    zconf.setClusterAddress(zServerHost + ":" + zServerPort);

    // mock cluster manager server
    clusterServer = ClusterManagerServer.getInstance();
    clusterServer.start();

    // mock cluster manager client
    clusterClient = ClusterManagerClient.getInstance();
    clusterClient.start(metaKey);

    // Waiting for cluster startup
    int wait = 0;
    while (wait++ < 100) {
      if (clusterServer.isClusterLeader()
          && clusterServer.raftInitialized()
          && clusterClient.raftInitialized()) {
        LOGGER.info("wait {}(ms) found cluster leader", wait * 3000);
        break;
      }
      try {
        Thread.sleep(3000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    assertEquals(true, clusterServer.isClusterLeader());

    LOGGER.info("startCluster <<<");
  }

  public static void stopCluster() {
    LOGGER.info("stopCluster >>>");
    if (null != clusterClient) {
      clusterClient.shutdown();
    }
    if (null != clusterClient) {
      clusterServer.shutdown();
    }
    LOGGER.info("stopCluster <<<");
  }

  public void getServerMeta() {
    LOGGER.info("serverMeta >>>");

    // Get metadata for all services
    Object meta = clusterClient.getClusterMeta(ClusterMetaType.SERVER_META, "");

    LOGGER.info(meta.toString());

    assertNotNull(meta);
    assertEquals(true, (meta instanceof HashMap));
    HashMap hashMap = (HashMap) meta;

    // Get metadata for the current service
    Object values = hashMap.get(zServerHost + ":" + zServerPort);
    assertEquals(true, (values instanceof HashMap));
    HashMap mapMetaValues = (HashMap) values;

    assertEquals(true, mapMetaValues.size() > 0);

    LOGGER.info("serverMeta <<<");
  }

  public void mockIntpProcessMeta(String metaKey, boolean online) {
    // mock IntpProcess Meta
    HashMap<String, Object> meta = new HashMap<>();
    meta.put(ClusterMeta.SERVER_HOST, "SERVER_HOST");
    meta.put(ClusterMeta.SERVER_PORT, 0);
    meta.put(ClusterMeta.INTP_TSERVER_HOST, "INTP_TSERVER_HOST");
    meta.put(ClusterMeta.INTP_TSERVER_PORT, 0);
    meta.put(ClusterMeta.CPU_CAPACITY, "CPU_CAPACITY");
    meta.put(ClusterMeta.CPU_USED, "CPU_USED");
    meta.put(ClusterMeta.MEMORY_CAPACITY, "MEMORY_CAPACITY");
    meta.put(ClusterMeta.MEMORY_USED, "MEMORY_USED");
    if (online) {
      meta.put(ClusterMeta.STATUS, ONLINE_STATUS);
    } else {
      meta.put(ClusterMeta.STATUS, OFFLINE_STATUS);
    }
    // put IntpProcess Meta
    clusterClient.putClusterMeta(ClusterMetaType.INTP_PROCESS_META, metaKey, meta);

    // get IntpProcess Meta
    HashMap<String, HashMap<String, Object>> check
        = clusterClient.getClusterMeta(ClusterMetaType.INTP_PROCESS_META, metaKey);

    LOGGER.info(check.toString());

    assertNotNull(check);
    assertNotNull(check.get(metaKey));
    assertEquals(true, check.get(metaKey).size() == 9);
  }
}
