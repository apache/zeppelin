package org.apache.zeppelin.cluster;
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

import org.apache.zeppelin.cluster.meta.ClusterMeta;
import org.apache.zeppelin.cluster.meta.ClusterMetaType;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ClusterManagerTest {
  private static Logger LOGGER = LoggerFactory.getLogger(ClusterManagerTest.class);

  private static ClusterManagerServer clusterManagerServer = null;
  private static ClusterManagerClient clusterManagerClient = null;

  private static ZeppelinConfiguration zconf = null;

  static String zServerHost;
  static int zServerPort;
  static final String metaKey = "ClusterManagerTestKey";

  @BeforeClass
  public static void initClusterEnv() throws IOException, InterruptedException {
    LOGGER.info("initClusterEnv >>>");

    zconf = ZeppelinConfiguration.create();

    // Set the cluster IP and port
    zServerHost = RemoteInterpreterUtils.findAvailableHostAddress();
    zServerPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
    zconf.setClusterAddress(zServerHost + ":" + zServerPort);

    // mock cluster manager server
    clusterManagerServer = ClusterManagerServer.getInstance();
    clusterManagerServer.start(null);

    // mock cluster manager client
    clusterManagerClient = ClusterManagerClient.getInstance();
    clusterManagerClient.start(metaKey);

    // Waiting for cluster startup
    int wait = 0;
    while(wait++ < 100) {
      if (clusterManagerServer.isClusterLeader()
          && clusterManagerServer.raftInitialized()
          && clusterManagerClient.raftInitialized()) {
        LOGGER.info("wait {}(ms) found cluster leader", wait*3000);
        break;
      }
      try {
        Thread.sleep(3000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    assertEquals(true, clusterManagerServer.isClusterLeader());
    LOGGER.info("initClusterEnv <<<");
  }

  @Test
  public void getServerMeta() {
    LOGGER.info("serverMeta >>>");

    // Get metadata for all services
    Object meta = clusterManagerClient.getClusterMeta(ClusterMetaType.ServerMeta, "");

    LOGGER.info(meta.toString());

    assertNotNull(meta);
    assertEquals(true, (meta instanceof HashMap));
    HashMap hashMap = (HashMap) meta;

    // Get metadata for the current service
    Object values = hashMap.get(zServerHost + ":" + zServerPort);
    assertEquals(true, (values instanceof HashMap));
    HashMap mapMetaValues = (HashMap) values;

    assertEquals(true, mapMetaValues.size()>0);

    LOGGER.info("serverMeta <<<");
  }

  @Test
  public void putIntpProcessMeta() {
    // mock IntpProcess Meta
    HashMap<String, Object> meta = new HashMap<>();
    meta.put(ClusterMeta.SERVER_HOST, zServerHost);
    meta.put(ClusterMeta.SERVER_PORT, zServerPort);
    meta.put(ClusterMeta.SERVER_TSERVER_HOST, "SERVER_TSERVER_HOST");
    meta.put(ClusterMeta.SERVER_TSERVER_PORT, "SERVER_TSERVER_PORT");
    meta.put(ClusterMeta.INTP_TSERVER_HOST, "INTP_TSERVER_HOST");
    meta.put(ClusterMeta.INTP_TSERVER_PORT, "INTP_TSERVER_PORT");
    meta.put(ClusterMeta.CPU_CAPACITY, "CPU_CAPACITY");
    meta.put(ClusterMeta.CPU_USED, "CPU_USED");
    meta.put(ClusterMeta.MEMORY_CAPACITY, "MEMORY_CAPACITY");
    meta.put(ClusterMeta.MEMORY_USED, "MEMORY_USED");

    // put IntpProcess Meta
    clusterManagerClient.putClusterMeta(ClusterMetaType.IntpProcessMeta, metaKey, meta);

    // get IntpProcess Meta
    HashMap<String, HashMap<String, Object>> check
        = clusterManagerClient.getClusterMeta(ClusterMetaType.IntpProcessMeta, metaKey);

    LOGGER.info(check.toString());

    assertNotNull(check);
    assertNotNull(check.get(metaKey));
    assertEquals(true, check.get(metaKey).size()>0);
  }
}
