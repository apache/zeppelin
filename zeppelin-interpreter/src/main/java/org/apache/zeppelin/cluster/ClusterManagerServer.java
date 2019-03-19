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
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.zeppelin.cluster.meta.ClusterMeta;
import org.apache.zeppelin.cluster.protocol.RaftServerMessagingProtocol;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterFactoryInterface;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.apache.zeppelin.interpreter.thrift.ClusterIntpProcParameters;
import org.apache.zeppelin.interpreter.thrift.ClusterManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.apache.zeppelin.cluster.meta.ClusterMetaType.ServerMeta;

/**
 * Cluster management server class instantiated in zeppelin-server
 * 1. Create a raft server
 * 2. Remotely create interpreter's thrift service
 */
public class ClusterManagerServer extends ClusterManager
    implements ClusterManagerService.Iface {
  private static Logger LOGGER = LoggerFactory.getLogger(ClusterManagerServer.class);

  private static ClusterManagerServer instance = null;

  // raft server
  protected RaftServer raftServer = null;

  protected MessagingService messagingService = null;

  // zeppelin cluster manager thrift service
  private TThreadPoolServer clusterManagerTserver = null;
  private ClusterManagerService.Processor<ClusterManagerServer> clusterManagerProcessor = null;

  // Find interpreter by note
  private InterpreterFactoryInterface interpreterFactory = null;

  // Connect to the interpreter process that has been created
  public static String CONNET_EXISTING_PROCESS = "CONNET_EXISTING_PROCESS";

  private ClusterManagerServer() {
    super();

    clusterManagerProcessor = new ClusterManagerService.Processor<>(this);

    deleteRaftSystemData();
  }

  public static ClusterManagerServer getInstance() {
    synchronized (ClusterManagerServer.class) {
      if (instance == null) {
        instance = new ClusterManagerServer();
      }
      return instance;
    }
  }

  public void start(InterpreterFactoryInterface interpreterFactory) {
    if (!zconf.isClusterMode()) {
      return;
    }

    this.interpreterFactory = interpreterFactory;

    initThread();

    // Instantiated raftServer monitoring class
    String clusterName = getClusterNodeName();
    clusterMonitor = new ClusterMonitor(this);
    clusterMonitor.start(ServerMeta, clusterName);

    super.start();
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

  protected void deleteRaftSystemData() {
    String zeppelinHome = zconf.getZeppelinHome();
    Path directory = new File(zeppelinHome, ".data").toPath();
    if (Files.exists(directory)) {
      try {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc)
              throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          }
        });
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
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

        RaftServer.Builder builder = RaftServer.builder(member.id())
            .withMembershipService(clusterService)
            .withProtocol(protocol)
            .withStorage(RaftStorage.builder()
                .withStorageLevel(StorageLevel.MEMORY)
                .withSerializer(storageSerializer)
                .withMaxSegmentSize(1024 * 1024)
                .build());

        raftServer = builder.build();
        raftServer.bootstrap(clusterMemberIds);

        LOGGER.info("RaftServer run() <<<");
      }
    }).start();

    // Cluster manager thrift thread
    new Thread(new Runnable() {
      @Override
      public void run() {
        LOGGER.info("TServerThread run() >>>");

        ZeppelinConfiguration zconf = new ZeppelinConfiguration();
        String portRange = zconf.getZeppelinServerRPCPortRange();

        try {
          TServerSocket serverTransport = RemoteInterpreterUtils.createTServerSocket(portRange);
          int tserverPort = serverTransport.getServerSocket().getLocalPort();

          clusterManagerTserver = new TThreadPoolServer(
              new TThreadPoolServer.Args(serverTransport).processor(clusterManagerProcessor));
          LOGGER.info("Starting raftServer manager Tserver on port {}", tserverPort);

          String nodeName = getClusterNodeName();
          HashMap<String, Object> meta = new HashMap<String, Object>();
          meta.put(ClusterMeta.NODE_NAME, nodeName);
          meta.put(ClusterMeta.SERVER_TSERVER_HOST, zeplServerHost);
          meta.put(ClusterMeta.SERVER_TSERVER_PORT, tserverPort);
          meta.put(ClusterMeta.SERVER_START_TIME, new Date());

          putClusterMeta(ServerMeta, nodeName, meta);
        } catch (UnknownHostException e) {
          LOGGER.error(e.getMessage());
        } catch (SocketException e) {
          LOGGER.error(e.getMessage());
        } catch (IOException e) {
          LOGGER.error(e.getMessage());
        }

        clusterManagerTserver.serve();

        LOGGER.info("TServerThread run() <<<");
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
      deleteClusterMeta(ServerMeta, getClusterNodeName());
      Thread.sleep(300);
      clusterMonitor.shutdown();
      // wait raft commit metadata
      Thread.sleep(300);
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage());
    }

    if (null != raftServer && raftServer.isRunning()) {
      try {
        raftServer.shutdown().get(3, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        LOGGER.error(e.getMessage());
      } catch (ExecutionException e) {
        LOGGER.error(e.getMessage());
      } catch (TimeoutException e) {
        LOGGER.error(e.getMessage());
      }
    }

    clusterManagerTserver.stop();

    super.shutdown();
  }

  public boolean openRemoteInterpreterProcess(
      String host, int port, final ClusterIntpProcParameters clusterIntpProcParameters)
      throws TException {
    LOGGER.info("host: {}, port: {}, clusterIntpProcParameters: {}",
        host, port, clusterIntpProcParameters);

    try (TTransport transport = new TSocket(host, port)) {
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      ClusterManagerService.Client client = new ClusterManagerService.Client(protocol);

      return client.createClusterInterpreterProcess(clusterIntpProcParameters);
    }
  }

  @Override
  public boolean createClusterInterpreterProcess(ClusterIntpProcParameters clusterIntpProcParameters) {
    // TODO: ZEPPELIN-3623

    return true;
  }

  // Obtain the server node whose resources are idle in the cluster
  public HashMap<String, Object> getIdleNodeMeta() {
    HashMap<String, Object> idleNodeMeta = null;
    HashMap<String, HashMap<String, Object>> clusterMeta = getClusterMeta(ServerMeta, "");

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
}
