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
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ClusterMultiNodeTest {
  private static Logger LOGGER = LoggerFactory.getLogger(ClusterMultiNodeTest.class);

  private static List<ClusterManagerServer> clusterServers = new ArrayList<>();
  private static ClusterManagerClient clusterClient = null;

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
    ZeppelinConfiguration zconf = ZeppelinConfiguration.create();
    zconf.setClusterAddress(clusterAddrList);

    // mock cluster manager server
    String cluster[] = clusterAddrList.split(",");
    try {
      for (int i = 0; i < 3; i ++) {
        String[] parts = cluster[i].split(":");
        String clusterHost = parts[0];
        int clusterPort = Integer.valueOf(parts[1]);

        Class clazz = ClusterManagerServer.class;
        Constructor constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        ClusterManagerServer clusterServer = (ClusterManagerServer) constructor.newInstance();
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
    clusterClient = ClusterManagerClient.getInstance();
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
    Object srvMeta = clusterClient.getClusterMeta(ClusterMetaType.SERVER_META, "");
    LOGGER.info(srvMeta.toString());

    Object intpMeta = clusterClient.getClusterMeta(ClusterMetaType.INTP_PROCESS_META, "");
    LOGGER.info(intpMeta.toString());

    assertNotNull(srvMeta);
    assertEquals(true, (srvMeta instanceof HashMap));
    HashMap hashMap = (HashMap) srvMeta;

    assertEquals(hashMap.size(), 3);
    LOGGER.info("getClusterServerMeta <<<");
  }
}
