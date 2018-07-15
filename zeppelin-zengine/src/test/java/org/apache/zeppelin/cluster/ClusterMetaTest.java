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
import io.atomix.copycat.server.CopycatServer;
import io.atomix.copycat.server.storage.Storage;
import io.atomix.copycat.server.storage.StorageLevel;
import org.apache.zeppelin.cluster.meta.ClusterMeta;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ClusterMetaTest {
  static CopycatServer copycatServer = null;
  static CopycatClient copycatClient = null;

  @BeforeClass
  public static void initClusterEnv() throws IOException, InterruptedException {
    System.out.println("initClusterEnv >>>");

    // Set the cluster IP and port
    String zServerHost = RemoteInterpreterUtils.findAvailableHostAddress();
    int zServerPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
    Address clusterMembers = new Address(zServerHost, zServerPort);

    // mock cluster manager server
    copycatServer = CopycatServer.builder(clusterMembers)
        .withStateMachine(ClusterStateMachine::new)
        .withTransport(new NettyTransport())
        .withStorage(Storage.builder()
            .withDirectory("copycat-logs")
            .withStorageLevel(StorageLevel.MEMORY)
            .withMaxSegmentSize(1024 * 1024 * 32)
            .withMinorCompactionInterval(Duration.ofMinutes(1))
            .withMajorCompactionInterval(Duration.ofMinutes(15))
            .build())
        .build();

    // Registering the putCommand and GetQuery command classes
    copycatServer.serializer().register(PutCommand.class, 1);
    copycatServer.serializer().register(GetQuery.class, 2);
    copycatServer.serializer().register(DeleteCommand.class, 3);

    copycatServer.onStateChange(state -> {
      if (state == CopycatServer.State.CANDIDATE) {
        System.out.println("CopycatServer.State CANDIDATE!");
      } else if (state == CopycatServer.State.FOLLOWER) {
        System.out.println("CopycatServer.State FOLLOWER!");
      } else if (state == CopycatServer.State.INACTIVE) {
        System.out.println("CopycatServer.State INACTIVE!");
      } else if (state == CopycatServer.State.LEADER) {
        System.out.println("CopycatServer.State LEADER!");
      } else if (state == CopycatServer.State.PASSIVE) {
        System.out.println("CopycatServer.State PASSIVE!");
      } else {
        System.out.println("unknown CopycatServer.State!");
      }
    });

    copycatServer.cluster().onJoin(member -> {
      System.out.println(member.address() + " joined the cluster.");
    });

    copycatServer.cluster().onLeave(member -> {
      System.out.println(member.address() + " left the cluster.");
    });

    copycatServer.bootstrap().join();

    // initialization Raft client
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
      Object verifyMeta = copycatClient.submit(new GetQuery(IntpProcessMeta, "test"))
          .get(3, TimeUnit.SECONDS);
      System.out.println("Cluster meta[" + IntpProcessMeta + "] : " + verifyMeta);
      assertEquals(verifyMeta, meta);
    } catch (TimeoutException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void deleteClusterMeta() throws InterruptedException, ExecutionException {
    System.out.println("test deleteClusterMeta");
    putClusterMeta();

    DeleteCommand deleteCommand = new DeleteCommand(IntpProcessMeta, "test");
    CompletableFuture futures = copycatClient.submit(deleteCommand);
    CompletableFuture.allOf(futures)
        .thenRun(() -> System.out.println("deleteClusterMeta completed!"));

    try {
      Object verifyMeta = copycatClient.submit(new GetQuery(IntpProcessMeta, "test"))
          .get(3, TimeUnit.SECONDS);
      System.out.println("Cluster meta[" + IntpProcessMeta + "] : " + verifyMeta);
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
