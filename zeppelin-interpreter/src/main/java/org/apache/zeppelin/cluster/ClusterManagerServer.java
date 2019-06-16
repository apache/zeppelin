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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;
import io.atomix.cluster.*;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DefaultClusterMembershipService;
import io.atomix.cluster.impl.DefaultNodeDiscoveryService;
import io.atomix.cluster.messaging.BroadcastService;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.primitive.PrimitiveState;
import io.atomix.protocols.raft.RaftServer;
import io.atomix.protocols.raft.protocol.RaftServerProtocol;
import io.atomix.protocols.raft.storage.RaftStorage;
import io.atomix.storage.StorageLevel;
import io.atomix.utils.net.Address;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.cluster.event.ClusterEventListener;
import org.apache.zeppelin.cluster.meta.ClusterMeta;
import org.apache.zeppelin.cluster.protocol.RaftServerMessagingProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

import static org.apache.zeppelin.cluster.meta.ClusterMetaType.SERVER_META;

/**
 * Cluster management server class instantiated in zeppelin-server
 * 1. Create a raft server
 * 2. Remotely create interpreter's thrift service
 */
public class ClusterManagerServer extends ClusterManager {
  private static Logger LOGGER = LoggerFactory.getLogger(ClusterManagerServer.class);

  private static ClusterManagerServer instance = null;

  // raft server
  protected RaftServer raftServer = null;

  protected MessagingService messagingService = null;

  // Connect to the interpreter process that has been created
  public static String CONNET_EXISTING_PROCESS = "CONNET_EXISTING_PROCESS";

  private List<ClusterEventListener> clusterIntpEventListeners = new ArrayList<>();
  private List<ClusterEventListener> clusterNoteEventListeners = new ArrayList<>();
  private List<ClusterEventListener> clusterAuthEventListeners = new ArrayList<>();

  // zeppelin cluster event
  public static String CLUSTER_INTP_EVENT_TOPIC = "CLUSTER_INTP_EVENT_TOPIC";
  public static String CLUSTER_NOTE_EVENT_TOPIC = "CLUSTER_NOTE_EVENT_TOPIC";
  public static String CLUSTER_AUTH_EVENT_TOPIC = "CLUSTER_AUTH_EVENT_TOPIC";
  public static String CLUSTER_NB_AUTH_EVENT_TOPIC = "CLUSTER_NB_AUTH_EVENT_TOPIC";

  private ClusterManagerServer() {
    super();
  }

  public static ClusterManagerServer getInstance() {
    synchronized (ClusterManagerServer.class) {
      if (instance == null) {
        instance = new ClusterManagerServer();
      }
      return instance;
    }
  }

  public void start() {
    if (!zconf.isClusterMode()) {
      return;
    }

    initThread();

    // Instantiated raftServer monitoring class
    String clusterName = getClusterNodeName();
    clusterMonitor = new ClusterMonitor(this);
    clusterMonitor.start(SERVER_META, clusterName);

    super.start();
  }

  @VisibleForTesting
  public void initTestCluster(String clusterAddrList, String host, int port) {
    this.zeplServerHost = host;
    this.raftServerPort = port;

    // clear
    clusterNodes.clear();
    raftAddressMap.clear();
    clusterMemberIds.clear();

    String cluster[] = clusterAddrList.split(",");
    for (int i = 0; i < cluster.length; i++) {
      String[] parts = cluster[i].split(":");
      String clusterHost = parts[0];
      int clusterPort = Integer.valueOf(parts[1]);

      String memberId = clusterHost + ":" + clusterPort;
      Address address = Address.from(clusterHost, clusterPort);
      Node node = Node.builder().withId(memberId).withAddress(address).build();
      clusterNodes.add(node);
      raftAddressMap.put(MemberId.from(memberId), address);
      clusterMemberIds.add(MemberId.from(memberId));
    }
  }

  @Override
  public boolean raftInitialized() {
    if (null != raftServer && raftServer.isRunning()
        && null != raftClient && null != raftSessionClient
        && raftSessionClient.getState() == PrimitiveState.CONNECTED) {
      return true;
    }

    return false;
  }

  @Override
  public boolean isClusterLeader() {
    if (null == raftServer
        || !raftServer.isRunning()
        || !raftServer.isLeader()) {
      return false;
    }

    return true;
  }

  private void initThread() {
    // RaftServer Thread
    new Thread(new Runnable() {
      @Override
      public void run() {
        LOGGER.info("RaftServer run() >>>");

        Address address = Address.from(zeplServerHost, raftServerPort);
        Member member = Member.builder(MemberId.from(zeplServerHost + ":" + raftServerPort))
            .withAddress(address)
            .build();
        messagingService = NettyMessagingService.builder()
            .withAddress(address).build().start().join();
        RaftServerProtocol protocol = new RaftServerMessagingProtocol(
            messagingService, ClusterManager.protocolSerializer, raftAddressMap::get);

        BootstrapService bootstrapService = new BootstrapService() {
          @Override
          public MessagingService getMessagingService() {
            return messagingService;
          }

          @Override
          public BroadcastService getBroadcastService() {
            return new BroadcastServiceAdapter();
          }
        };

        ManagedClusterMembershipService clusterService = new DefaultClusterMembershipService(
            member,
            new DefaultNodeDiscoveryService(bootstrapService, member,
                new BootstrapDiscoveryProvider(clusterNodes)),
            bootstrapService,
            new MembershipConfig());

        File atomixDateDir = com.google.common.io.Files.createTempDir();
        atomixDateDir.deleteOnExit();

        RaftServer.Builder builder = RaftServer.builder(member.id())
            .withMembershipService(clusterService)
            .withProtocol(protocol)
            .withStorage(RaftStorage.builder()
                .withStorageLevel(StorageLevel.MEMORY)
                .withDirectory(atomixDateDir)
                .withSerializer(storageSerializer)
                .withMaxSegmentSize(1024 * 1024)
                .build());

        raftServer = builder.build();
        raftServer.bootstrap(clusterMemberIds);

        messagingService.registerHandler(CLUSTER_INTP_EVENT_TOPIC,
            subscribeClusterIntpEvent, MoreExecutors.directExecutor());
        messagingService.registerHandler(CLUSTER_NOTE_EVENT_TOPIC,
            subscribeClusterNoteEvent, MoreExecutors.directExecutor());
        messagingService.registerHandler(CLUSTER_AUTH_EVENT_TOPIC,
            subscribeClusterAuthEvent, MoreExecutors.directExecutor());

        LOGGER.info("RaftServer run() <<<");
      }
    }).start();
  }

  @Override
  public void shutdown() {
    if (!zconf.isClusterMode()) {
      return;
    }

    try {
      // delete local machine meta
      deleteClusterMeta(SERVER_META, getClusterNodeName());
      Thread.sleep(300);
      clusterMonitor.shutdown();
      // wait raft commit metadata
      Thread.sleep(300);
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
    }

    if (null != raftServer && raftServer.isRunning()) {
      try {
        raftServer.shutdown().get(3, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        LOGGER.error(e.getMessage(), e);
      } catch (ExecutionException e) {
        LOGGER.error(e.getMessage(), e);
      } catch (TimeoutException e) {
        LOGGER.error(e.getMessage(), e);
      }
    }

    super.shutdown();
  }

  // Obtain the server node whose resources are idle in the cluster
  public HashMap<String, Object> getIdleNodeMeta() {
    HashMap<String, Object> idleNodeMeta = null;
    HashMap<String, HashMap<String, Object>> clusterMeta = getClusterMeta(SERVER_META, "");

    long memoryIdle = 0;
    for (Map.Entry<String, HashMap<String, Object>> entry : clusterMeta.entrySet()) {
      HashMap<String, Object> meta = entry.getValue();
      // Check if the service or process is offline
      String status = (String) meta.get(ClusterMeta.STATUS);
      if (null == status || StringUtils.isEmpty(status)
          || status.equals(ClusterMeta.OFFLINE_STATUS)) {
        continue;
      }

      long memoryCapacity  = (long) meta.get(ClusterMeta.MEMORY_CAPACITY);
      long memoryUsed      = (long) meta.get(ClusterMeta.MEMORY_USED);
      long idle = memoryCapacity - memoryUsed;
      if (idle > memoryIdle) {
        memoryIdle = idle;
        idleNodeMeta = meta;
      }
    }

    return idleNodeMeta;
  }

  public void unicastClusterEvent(String host, int port, String topic, String msg) {
    LOGGER.info("send unicastClusterEvent message {}", msg);

    Address address = Address.from(host, port);
    CompletableFuture<byte[]> response = messagingService.sendAndReceive(address,
        topic, msg.getBytes(), Duration.ofSeconds(2));
    response.whenComplete((r, e) -> {
      if (null == e) {
        LOGGER.error(e.getMessage(), e);
      } else {
        LOGGER.info("unicastClusterEvent success! {}", msg);
      }
    });
  }

  public void broadcastClusterEvent(String topic, String msg) {
    LOGGER.info("send broadcastClusterEvent message {}", msg);

    for (Node node : clusterNodes) {
      if (StringUtils.equals(node.address().host(), zeplServerHost)
          && node.address().port() == raftServerPort) {
        // skip myself
        continue;
      }

      CompletableFuture<byte[]> response = messagingService.sendAndReceive(node.address(),
          topic, msg.getBytes(), Duration.ofSeconds(2));
      response.whenComplete((r, e) -> {
        if (null == e) {
          LOGGER.error(e.getMessage(), e);
        } else {
          LOGGER.info("broadcastClusterNoteEvent success! {}", msg);
        }
      });
    }
  }

  private BiFunction<Address, byte[], byte[]> subscribeClusterIntpEvent = (address, data) -> {
    String message = new String(data);
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("subscribeClusterIntpEvent() {}", message);
    }
    for (ClusterEventListener eventListener : clusterIntpEventListeners) {
      eventListener.onClusterEvent(message);
    }

    return null;
  };

  private BiFunction<Address, byte[], byte[]> subscribeClusterNoteEvent = (address, data) -> {
    String message = new String(data);
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("subscribeClusterNoteEvent() {}", message);
    }
    for (ClusterEventListener eventListener : clusterNoteEventListeners) {
      eventListener.onClusterEvent(message);
    }

    return null;
  };

  private BiFunction<Address, byte[], byte[]> subscribeClusterAuthEvent = (address, data) -> {
    String message = new String(data);
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("subscribeClusterAuthEvent() {}", message);
    }
    for (ClusterEventListener eventListener : clusterAuthEventListeners) {
      eventListener.onClusterEvent(message);
    }

    return null;
  };

  public void addClusterEventListeners(String topic, ClusterEventListener listener) {
    if (StringUtils.equals(topic, CLUSTER_INTP_EVENT_TOPIC)) {
      clusterIntpEventListeners.add(listener);
    } else if (StringUtils.equals(topic, CLUSTER_NOTE_EVENT_TOPIC)) {
      clusterNoteEventListeners.add(listener);
    } else if (StringUtils.equals(topic, CLUSTER_AUTH_EVENT_TOPIC)) {
      clusterAuthEventListeners.add(listener);
    } else {
      LOGGER.error("Unknow cluster event topic : {}", topic);
    }
  }
}
