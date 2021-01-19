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
package org.apache.zeppelin.cluster;

import org.apache.zeppelin.cluster.meta.ClusterMeta;
import org.apache.zeppelin.cluster.meta.ClusterMetaType;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ClusterSingleNodeTest {
  private static Logger LOGGER = LoggerFactory.getLogger(ClusterSingleNodeTest.class);
  private static ZeppelinConfiguration zconf;

  private static ClusterManagerServer clusterServer = null;
  private static ClusterManagerClient clusterClient = null;

  static String zServerHost;
  static int zServerPort;
  static final String metaKey = "ClusterSingleNodeTestKey";

  @BeforeClass
  public static void startCluster() throws IOException, InterruptedException {
    LOGGER.info("startCluster >>>");

    zconf = ZeppelinConfiguration.create("zeppelin-site-test.xml");

    // Set the cluster IP and port
    zServerHost = RemoteInterpreterUtils.findAvailableHostAddress();
    zServerPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
    zconf.setClusterAddress(zServerHost + ":" + zServerPort);

    clusterServer = ClusterManagerServer.getInstance(zconf);
    clusterServer.start();

    // mock cluster manager client
    clusterClient = ClusterManagerClient.getInstance(zconf);
    clusterClient.start(metaKey);

    // Waiting for cluster startup
    int wait = 0;
    while(wait++ < 100) {
      if (clusterServer.isClusterLeader()
          && clusterServer.raftInitialized()
          && clusterClient.raftInitialized()) {
        LOGGER.info("wait {}(ms) found cluster leader", wait*3000);
        break;
      }
      try {
        Thread.sleep(3000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    Thread.sleep(3000);
    assertEquals(true, clusterServer.isClusterLeader());
    LOGGER.info("startCluster <<<");
  }

  @AfterClass
  public static void stopCluster() {
    if (null != clusterClient) {
      clusterClient.shutdown();
    }
    if (null != clusterClient) {
      clusterServer.shutdown();
    }
    ZeppelinConfiguration.reset();
    LOGGER.info("stopCluster");
  }

  @Test
  public void getServerMeta() {
    LOGGER.info("getServerMeta >>>");

    // Get metadata for all services
    Object meta = clusterClient.getClusterMeta(ClusterMetaType.SERVER_META, "");
    LOGGER.info(meta.toString());

    Object intpMeta = clusterClient.getClusterMeta(ClusterMetaType.INTP_PROCESS_META, "");
    LOGGER.info(intpMeta.toString());

    assertNotNull(meta);
    assertEquals(true, (meta instanceof HashMap));
    HashMap hashMap = (HashMap) meta;

    // Get metadata for the current service
    Object values = hashMap.get(clusterClient.getClusterNodeName());
    assertEquals(true, (values instanceof HashMap));
    HashMap mapMetaValues = (HashMap) values;

    assertEquals(true, mapMetaValues.size()>0);

    LOGGER.info("getServerMeta <<<");
  }

  @Test
  public void putIntpProcessMeta() {
    // mock IntpProcess Meta
    HashMap<String, Object> meta = new HashMap<>();
    meta.put(ClusterMeta.SERVER_HOST, zServerHost);
    meta.put(ClusterMeta.SERVER_PORT, zServerPort);
    meta.put(ClusterMeta.INTP_TSERVER_HOST, "INTP_TSERVER_HOST");
    meta.put(ClusterMeta.INTP_TSERVER_PORT, "INTP_TSERVER_PORT");
    meta.put(ClusterMeta.CPU_CAPACITY, "CPU_CAPACITY");
    meta.put(ClusterMeta.CPU_USED, "CPU_USED");
    meta.put(ClusterMeta.MEMORY_CAPACITY, "MEMORY_CAPACITY");
    meta.put(ClusterMeta.MEMORY_USED, "MEMORY_USED");

    // put IntpProcess Meta
    clusterClient.putClusterMeta(ClusterMetaType.INTP_PROCESS_META, metaKey, meta);

    // get IntpProcess Meta
    HashMap<String, HashMap<String, Object>> check
        = clusterClient.getClusterMeta(ClusterMetaType.INTP_PROCESS_META, metaKey);

    LOGGER.info(check.toString());

    assertNotNull(check);
    assertNotNull(check.get(metaKey));
    assertEquals(true, check.get(metaKey).size()>0);
  }
}
