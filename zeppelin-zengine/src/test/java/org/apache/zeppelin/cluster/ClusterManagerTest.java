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
import io.atomix.catalyst.transport.Address;
import io.atomix.catalyst.transport.netty.NettyTransport;
import io.atomix.copycat.client.ConnectionStrategies;
import io.atomix.copycat.client.CopycatClient;
import io.atomix.copycat.client.RecoveryStrategies;
import io.atomix.copycat.client.ServerSelectionStrategies;
import org.apache.thrift.TException;
import org.apache.zeppelin.cluster.meta.ClusterMeta;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterServer;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.apache.zeppelin.cluster.meta.ClusterMetaType.IntpProcessMeta;
import static org.apache.zeppelin.cluster.meta.ClusterMetaType.ServerMeta;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ClusterManagerTest {
  static ClusterManagerServer clusterManagerServer = null;
  static Address clusterMembers = null;
  static CopycatClient copycatClient = null;
  static String zServerHost;
  static int zServerPort;

  @BeforeClass
  public static void initClusterEnv() throws IOException, InterruptedException {
    System.out.println("initClusterEnv >>>");

    // Set the cluster IP and port
    zServerHost = RemoteInterpreterUtils.findAvailableHostAddress();
    zServerPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
    ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
    zConf.setClusterAddress(zServerHost + ":" + zServerPort);

    // mock cluster manager server
    clusterManagerServer = ClusterManagerServer.getInstance();
    clusterManagerServer.start(null);

    clusterMembers = new Address(zServerHost, zServerPort);

    copycatClient = CopycatClient.builder()
        .withTransport(new NettyTransport())
        .withConnectionStrategy(ConnectionStrategies.FIBONACCI_BACKOFF)
        .withRecoveryStrategy(RecoveryStrategies.RECOVER)
        .withServerSelectionStrategy(ServerSelectionStrategies.LEADER)
        .withSessionTimeout(Duration.ofSeconds(15))
        .build();

    copycatClient.onStateChange(state -> {
      if (state == CopycatClient.State.CONNECTED) {
        System.out.println("CopycatClient.State CONNECTED!");
      } else if (state == CopycatClient.State.CLOSED) {
        System.out.println("CopycatClient.State CLOSED!");
      } else if (state == CopycatClient.State.SUSPENDED) {
        System.out.println("CopycatClient.State SUSPENDED!");
      } else {
        System.out.println("unknown CopycatClient.State " + state);
      }
    });

    copycatClient.serializer().register(PutCommand.class, 1);
    copycatClient.serializer().register(GetQuery.class, 2);
    copycatClient.serializer().register(DeleteCommand.class, 3);

    copycatClient.connect(clusterMembers).join();

    // Waiting for cluster startup
    Thread.sleep(3000);
  }

  @Test
  public void serverMeta() throws InterruptedException, ExecutionException {
    System.out.println("serverMeta >>>");
    Object meta = null;
    try {
      meta = copycatClient.submit(new GetQuery(ServerMeta, ""))
          .get(10, TimeUnit.SECONDS);
      System.out.println("ServerMeta : " + meta);

      assertNotNull(meta);
      assertEquals(true, (meta instanceof HashMap));
      HashMap hashMap = (HashMap) meta;

      Object values = hashMap.get(zServerHost + ":" + zServerPort);
      assertEquals(true, (values instanceof HashMap));
      HashMap mapMetaValues = (HashMap) values;

      assertEquals(7, mapMetaValues.size());
      assertEquals(true, mapMetaValues.containsKey(ClusterMeta.SERVER_HOST));
      assertEquals(true, mapMetaValues.containsKey(ClusterMeta.SERVER_PORT));
      assertEquals(true, mapMetaValues.containsKey(ClusterMeta.MEMORY_USED));
      assertEquals(true, mapMetaValues.containsKey(ClusterMeta.MEMORY_CAPACITY));
      assertEquals(true, mapMetaValues.containsKey(ClusterMeta.CPU_CAPACITY));
      assertEquals(true, mapMetaValues.containsKey(ClusterMeta.CPU_USED));
      assertEquals(true, mapMetaValues.containsKey(ClusterMeta.HEARTBEAT));
    } catch (TimeoutException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void putClusterMeta() throws InterruptedException, ExecutionException {
    Map<String, Object> meta = new HashMap<>();
    meta.put(ClusterMeta.SERVER_HOST, "SERVER_HOST");
    meta.put(ClusterMeta.SERVER_PORT, "SERVER_PORT");
    meta.put(ClusterMeta.SERVER_TSERVER_HOST, "SERVER_TSERVER_HOST");
    meta.put(ClusterMeta.SERVER_TSERVER_PORT, "SERVER_TSERVER_PORT");
    meta.put(ClusterMeta.INTP_TSERVER_HOST, "INTP_TSERVER_HOST");
    meta.put(ClusterMeta.INTP_TSERVER_PORT, "INTP_TSERVER_PORT");
    meta.put(ClusterMeta.CPU_CAPACITY, "CPU_CAPACITY");
    meta.put(ClusterMeta.CPU_USED, "CPU_USED");
    meta.put(ClusterMeta.MEMORY_CAPACITY, "MEMORY_CAPACITY");
    meta.put(ClusterMeta.MEMORY_USED, "MEMORY_USED");
    meta.put(ClusterMeta.MEMORY_USED, "MEMORY_USED");

    PutCommand putCommand = new PutCommand(IntpProcessMeta, "test", meta);
    copycatClient.submit(putCommand);

    try {
      Object chkMeta = copycatClient.submit(new GetQuery(IntpProcessMeta, "test"))
          .get(3, TimeUnit.SECONDS);
      System.out.println("Cluster meta[" + IntpProcessMeta + "] : " + chkMeta);
      assertEquals(chkMeta, meta);
    } catch (TimeoutException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void deleteClusterMeta() throws InterruptedException, ExecutionException {
    putClusterMeta();

    DeleteCommand deleteCommand = new DeleteCommand(IntpProcessMeta, "test");
    CompletableFuture futures = copycatClient.submit(deleteCommand);
    CompletableFuture.allOf(futures)
        .thenRun(() -> System.out.println("deleteClusterMeta completed!"));

    try {
      Object verifyMeta = copycatClient.submit(new GetQuery(IntpProcessMeta, "test"))
          .get(3, TimeUnit.SECONDS);
      assertNull(verifyMeta);
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (ExecutionException e) {
      e.printStackTrace();
    } catch (TimeoutException e) {
      e.printStackTrace();
    }
  }
}
