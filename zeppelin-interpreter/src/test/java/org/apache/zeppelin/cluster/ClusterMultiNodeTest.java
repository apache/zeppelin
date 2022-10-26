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

import org.apache.zeppelin.cluster.meta.ClusterMetaType;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ClusterMultiNodeTest {
  private static Logger LOGGER = LoggerFactory.getLogger(ClusterMultiNodeTest.class);

  private static List<ClusterManagerServer> clusterServers = new ArrayList<>();
  private static ClusterManagerClient clusterClient = null;
  private static ZeppelinConfiguration zconf;

  static final String metaKey = "ClusterMultiNodeTestKey";

  @BeforeClass
  public static void startCluster() throws IOException, InterruptedException {
    LOGGER.info("startCluster >>>");

    String clusterAddrList = "";
    String zServerHost = RemoteInterpreterUtils.findAvailableHostAddress();
    for (int i = 0; i < 3; i ++) {
      // Set the cluster IP and port
      int zServerPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
      clusterAddrList += zServerHost + ":" + zServerPort;
      if (i != 2) {
        clusterAddrList += ",";
      }
    }
    zconf = ZeppelinConfiguration.create();
    zconf.setClusterAddress(clusterAddrList);

    // mock cluster manager server
    String cluster[] = clusterAddrList.split(",");
    try {
      for (int i = 0; i < 3; i ++) {
        String[] parts = cluster[i].split(":");
        String clusterHost = parts[0];
        int clusterPort = Integer.valueOf(parts[1]);

        Class<ClusterManagerServer> clazz = ClusterManagerServer.class;
        Constructor<ClusterManagerServer> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        ClusterManagerServer clusterServer = constructor.newInstance();
        clusterServer.initTestCluster(clusterAddrList, clusterHost, clusterPort);

        clusterServers.add(clusterServer);
      }
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }

    for (ClusterManagerServer clusterServer : clusterServers) {
      clusterServer.start();
    }

    // mock cluster manager client
    clusterClient = ClusterManagerClient.getInstance(zconf);
    clusterClient.start(metaKey);

    // Waiting for cluster startup
    int wait = 0;
    while(wait++ < 100) {
      if (clusterIsStartup() && clusterClient.raftInitialized()) {
        LOGGER.info("wait {}(ms) found cluster leader", wait*3000);
        break;
      }
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        LOGGER.error(e.getMessage(), e);
      }
    }

    Thread.sleep(3000);
    assertEquals(true, clusterIsStartup());
    LOGGER.info("startCluster <<<");

    getClusterServerMeta();
  }

  @AfterClass
  public static void stopCluster() {
    LOGGER.info("stopCluster >>>");
    if (null != clusterClient) {
      clusterClient.shutdown();
    }
    for (ClusterManagerServer clusterServer : clusterServers) {
      clusterServer.shutdown();
    }
    ZeppelinConfiguration.reset();
    LOGGER.info("stopCluster <<<");
  }

  static boolean clusterIsStartup() {
    boolean foundLeader = false;
    for (ClusterManagerServer clusterServer : clusterServers) {
      if (!clusterServer.raftInitialized()) {
        LOGGER.warn("clusterServer not Initialized!");
        return false;
      }
      if (clusterServer.isClusterLeader()) {
        foundLeader = true;
      }
    }

    if (!foundLeader) {
      LOGGER.warn("Can not found leader!");
      return false;
    }

    return true;
  }

  public static void getClusterServerMeta() {
    LOGGER.info("getClusterServerMeta >>>");
    // Get metadata for all services
    Map<String, Map<String, Object>> srvMeta = clusterClient.getClusterMeta(ClusterMetaType.SERVER_META, "");
    LOGGER.info(srvMeta.toString());

    Map<String, Map<String, Object>> intpMeta = clusterClient.getClusterMeta(ClusterMetaType.INTP_PROCESS_META, "");
    LOGGER.info(intpMeta.toString());

    assertNotNull(srvMeta);
    assertTrue(srvMeta instanceof Map);

    assertEquals(3, srvMeta.size());
    LOGGER.info("getClusterServerMeta <<<");
  }
}
