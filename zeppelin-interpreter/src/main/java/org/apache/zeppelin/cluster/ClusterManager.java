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

import com.google.common.collect.Maps;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.primitive.operation.OperationType;
import io.atomix.primitive.operation.PrimitiveOperation;
import io.atomix.primitive.operation.impl.DefaultOperationId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.primitive.session.SessionClient;
import io.atomix.primitive.session.SessionId;
import io.atomix.protocols.raft.RaftClient;
import io.atomix.protocols.raft.RaftError;
import io.atomix.protocols.raft.ReadConsistency;
import io.atomix.protocols.raft.cluster.RaftMember;
import io.atomix.protocols.raft.cluster.impl.DefaultRaftMember;
import io.atomix.protocols.raft.protocol.CloseSessionRequest;
import io.atomix.protocols.raft.protocol.CloseSessionResponse;
import io.atomix.protocols.raft.protocol.KeepAliveRequest;
import io.atomix.protocols.raft.protocol.KeepAliveResponse;
import io.atomix.protocols.raft.protocol.QueryRequest;
import io.atomix.protocols.raft.protocol.QueryResponse;
import io.atomix.protocols.raft.protocol.CommandRequest;
import io.atomix.protocols.raft.protocol.CommandResponse;
import io.atomix.protocols.raft.protocol.MetadataRequest;
import io.atomix.protocols.raft.protocol.MetadataResponse;
import io.atomix.protocols.raft.protocol.JoinRequest;
import io.atomix.protocols.raft.protocol.JoinResponse;
import io.atomix.protocols.raft.protocol.LeaveRequest;
import io.atomix.protocols.raft.protocol.LeaveResponse;
import io.atomix.protocols.raft.protocol.ConfigureRequest;
import io.atomix.protocols.raft.protocol.ConfigureResponse;
import io.atomix.protocols.raft.protocol.ReconfigureRequest;
import io.atomix.protocols.raft.protocol.ReconfigureResponse;
import io.atomix.protocols.raft.protocol.InstallRequest;
import io.atomix.protocols.raft.protocol.InstallResponse;
import io.atomix.protocols.raft.protocol.PollRequest;
import io.atomix.protocols.raft.protocol.PollResponse;
import io.atomix.protocols.raft.protocol.VoteRequest;
import io.atomix.protocols.raft.protocol.VoteResponse;
import io.atomix.protocols.raft.protocol.AppendRequest;
import io.atomix.protocols.raft.protocol.AppendResponse;
import io.atomix.protocols.raft.protocol.PublishRequest;
import io.atomix.protocols.raft.protocol.ResetRequest;
import io.atomix.protocols.raft.protocol.RaftResponse;
import io.atomix.protocols.raft.storage.log.entry.CloseSessionEntry;
import io.atomix.protocols.raft.storage.log.entry.CommandEntry;
import io.atomix.protocols.raft.storage.log.entry.ConfigurationEntry;
import io.atomix.protocols.raft.storage.log.entry.InitializeEntry;
import io.atomix.protocols.raft.storage.log.entry.KeepAliveEntry;
import io.atomix.protocols.raft.storage.log.entry.MetadataEntry;
import io.atomix.protocols.raft.storage.log.entry.OpenSessionEntry;
import io.atomix.protocols.raft.storage.log.entry.QueryEntry;
import io.atomix.protocols.raft.protocol.OpenSessionRequest;
import io.atomix.protocols.raft.protocol.OpenSessionResponse;
import io.atomix.protocols.raft.protocol.RaftClientProtocol;
import io.atomix.protocols.raft.session.CommunicationStrategy;
import io.atomix.protocols.raft.storage.system.Configuration;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Serializer;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.cluster.meta.ClusterMeta;
import org.apache.zeppelin.cluster.meta.ClusterMetaEntity;
import org.apache.zeppelin.cluster.meta.ClusterMetaOperation;
import org.apache.zeppelin.cluster.meta.ClusterMetaType;
import org.apache.zeppelin.cluster.protocol.LocalRaftProtocolFactory;
import org.apache.zeppelin.cluster.protocol.RaftClientMessagingProtocol;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.launcher.InterpreterClient;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Instant;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static io.atomix.primitive.operation.PrimitiveOperation.operation;
import static org.apache.zeppelin.cluster.meta.ClusterMeta.INTP_TSERVER_HOST;
import static org.apache.zeppelin.cluster.meta.ClusterMeta.INTP_TSERVER_PORT;
import static org.apache.zeppelin.cluster.meta.ClusterMeta.ONLINE_STATUS;
import static org.apache.zeppelin.cluster.meta.ClusterMeta.STATUS;
import static org.apache.zeppelin.cluster.meta.ClusterMetaOperation.DELETE_OPERATION;
import static org.apache.zeppelin.cluster.meta.ClusterMetaOperation.PUT_OPERATION;
import static org.apache.zeppelin.cluster.meta.ClusterMetaOperation.GET_OPERATION;
import static org.apache.zeppelin.cluster.meta.ClusterMetaType.INTP_PROCESS_META;

/**
 * The base class for cluster management, including the following implementations
 * 1. RaftClient as the raft client
 * 2. Threading to provide retry after cluster metadata submission failure
 * 3. Cluster monitoring
 */
public abstract class ClusterManager {
  private static Logger LOGGER = LoggerFactory.getLogger(ClusterManager.class);

  public ZeppelinConfiguration zConf;

  protected Collection<Node> clusterNodes = new ArrayList<>();

  protected int raftServerPort = 0;

  protected RaftClient raftClient = null;
  protected SessionClient raftSessionClient = null;
  protected Map<MemberId, Address> raftAddressMap = new ConcurrentHashMap<>();
  protected LocalRaftProtocolFactory protocolFactory
      = new LocalRaftProtocolFactory(protocolSerializer);
  protected List<MemberId> clusterMemberIds = new ArrayList<MemberId>();

  protected AtomicBoolean running = new AtomicBoolean(true);

  // Write data through the queue to prevent failure due to network exceptions
  private ConcurrentLinkedQueue<ClusterMetaEntity> clusterMetaQueue
      = new ConcurrentLinkedQueue<>();

  // zeppelin server host & port
  protected String zeplServerHost = "";

  protected ClusterMonitor clusterMonitor = null;

  protected boolean isTest = false;

  public ClusterManager(ZeppelinConfiguration zConf) {
    try {
      this.zConf = zConf;
      zeplServerHost = RemoteInterpreterUtils.findAvailableHostAddress();
      String clusterAddr = this.zConf.getClusterAddress();
      if (!StringUtils.isEmpty(clusterAddr)) {
        String cluster[] = clusterAddr.split(",");

        for (int i = 0; i < cluster.length; i++) {
          String[] parts = cluster[i].split(":");
          String clusterHost = parts[0];
          int clusterPort = Integer.valueOf(parts[1]);
          if (zeplServerHost.equalsIgnoreCase(clusterHost)) {
            raftServerPort = clusterPort;
          }

          String memberId = clusterHost + ":" + clusterPort;
          Address address = Address.from(clusterHost, clusterPort);
          Node node = Node.builder().withId(memberId).withAddress(address).build();
          clusterNodes.add(node);
          raftAddressMap.put(MemberId.from(memberId), address);
          clusterMemberIds.add(MemberId.from(memberId));
        }
      } else {
        throw new RuntimeException("No zeppelin.cluster.addr specified in zeppelin-site.xml");
      }
    } catch (UnknownHostException e) {
      LOGGER.error(e.getMessage());
    } catch (SocketException e) {
      LOGGER.error(e.getMessage());
    }
  }

  // Check if the raft environment is initialized
  public abstract boolean raftInitialized();
  // Is it a cluster leader
  public abstract boolean isClusterLeader();

  public AtomicBoolean getRunning() {
    return running;
  }

  private SessionClient createProxy(RaftClient client) {
    return client.sessionBuilder(ClusterPrimitiveType.PRIMITIVE_NAME,
        ClusterPrimitiveType.INSTANCE, new ServiceConfig())
        .withReadConsistency(ReadConsistency.SEQUENTIAL)
        .withCommunicationStrategy(CommunicationStrategy.LEADER)
        .build()
        .connect()
        .join();
  }

  public void start() {
    if (!zConf.isClusterMode()) {
      return;
    }

    // RaftClient Thread
    new Thread(new Runnable() {
      @Override
      public void run() {
        LOGGER.info("RaftClientThread run() >>>");

        int raftClientPort = 0;
        try {
          raftClientPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
        } catch (IOException e) {
          LOGGER.error(e.getMessage());
        }

        MemberId memberId = MemberId.from(zeplServerHost + ":" + raftClientPort);
        Address address = Address.from(zeplServerHost, raftClientPort);
        raftAddressMap.put(memberId, address);

        MessagingService messagingManager
            = NettyMessagingService.builder().withAddress(address).build().start().join();
        RaftClientProtocol protocol = new RaftClientMessagingProtocol(
            messagingManager, protocolSerializer, raftAddressMap::get);

        raftClient = RaftClient.builder()
            .withMemberId(memberId)
            .withPartitionId(PartitionId.from("partition", 1))
            .withProtocol(protocol)
            .build();

        raftClient.connect(clusterMemberIds).join();

        raftSessionClient = createProxy(raftClient);

        LOGGER.info("RaftClientThread run() <<<");
      }
    }).start();

    // Cluster Meta Consume Thread
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          while (getRunning().get()) {
            ClusterMetaEntity metaEntity = clusterMetaQueue.peek();
            if (null != metaEntity) {
              // Determine whether the client is connected
              int retry = 0;
              while (!raftInitialized()) {
                retry++;
                if (0 == retry % 30) {
                  LOGGER.warn("Raft incomplete initialization! retry[{}]", retry);
                }
                Thread.sleep(100);
              }
              boolean success = false;
              switch (metaEntity.getOperation()) {
                case DELETE_OPERATION:
                  success = deleteClusterMeta(metaEntity);
                  break;
                case PUT_OPERATION:
                  success = putClusterMeta(metaEntity);
                  break;
              }
              if (true == success) {
                // The operation was successfully deleted
                clusterMetaQueue.remove(metaEntity);
                LOGGER.info("Cluster Meta Consume success! {}", metaEntity);
              } else {
                LOGGER.error("Cluster Meta Consume faild!");
              }
            } else {
              Thread.sleep(100);
            }
          }
        } catch (InterruptedException e) {
          LOGGER.error(e.getMessage());
        }
      }
    }).start();
  }

  // cluster shutdown
  public void shutdown() {
    if (!zConf.isClusterMode()) {
      return;
    }

    running.set(false);

    try {
      if (null != raftSessionClient) {
        raftSessionClient.close().get(3, TimeUnit.SECONDS);
      }
      if (null != raftClient) {
        raftClient.close().get(3, TimeUnit.SECONDS);
      }
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage());
    } catch (ExecutionException e) {
      LOGGER.error(e.getMessage());
    } catch (TimeoutException e) {
      LOGGER.error(e.getMessage());
    }
  }

  public String getClusterNodeName() {
    if (isTest) {
      // Start three cluster servers in the test case at the same time,
      // need to avoid duplicate names
      return this.zeplServerHost + ":" + this.raftServerPort;
    }

    String hostName = "";
    try {
      InetAddress addr = InetAddress.getLocalHost();
      hostName = addr.getHostName().toString();
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }
    return hostName;
  }

  // put metadata into cluster metadata
  private boolean putClusterMeta(ClusterMetaEntity entity) {
    if (!raftInitialized()) {
      LOGGER.error("Raft incomplete initialization!");
      return false;
    }

    ClusterMetaType metaType = entity.getMetaType();
    String metaKey = entity.getKey();
    HashMap<String, Object> newMetaValue = entity.getValues();

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("putClusterMeta {} {}", metaType, metaKey);
    }

    // add cluster name
    newMetaValue.put(ClusterMeta.SERVER_HOST, zeplServerHost);
    newMetaValue.put(ClusterMeta.SERVER_PORT, raftServerPort);

    raftSessionClient.execute(operation(ClusterStateMachine.PUT,
        clientSerializer.encode(entity)))
        .<Long>thenApply(clientSerializer::decode);
    return true;
  }

  // put metadata into cluster metadata
  public void putClusterMeta(ClusterMetaType type, String key, HashMap<String, Object> values) {
    ClusterMetaEntity metaEntity = new ClusterMetaEntity(PUT_OPERATION, type, key, values);

    boolean result = putClusterMeta(metaEntity);
    if (false == result) {
      LOGGER.warn("putClusterMeta failure, Cache metadata to queue.");
      clusterMetaQueue.add(metaEntity);
    }
  }

  // delete metadata by cluster metadata
  private boolean deleteClusterMeta(ClusterMetaEntity entity) {
    ClusterMetaType metaType = entity.getMetaType();
    String metaKey = entity.getKey();

    // Need to pay attention to delete metadata operations
    LOGGER.info("deleteClusterMeta {} {}", metaType, metaKey);

    if (!raftInitialized()) {
      LOGGER.error("Raft incomplete initialization!");
      return false;
    }

    raftSessionClient.execute(operation(
        ClusterStateMachine.REMOVE,
        clientSerializer.encode(entity)))
        .<Long>thenApply(clientSerializer::decode)
        .thenAccept(result -> {
          LOGGER.info("deleteClusterMeta {}", result);
        });

    return true;
  }

  // delete metadata from cluster metadata
  public void deleteClusterMeta(ClusterMetaType type, String key) {
    ClusterMetaEntity metaEntity = new ClusterMetaEntity(DELETE_OPERATION, type, key, null);

    boolean result = deleteClusterMeta(metaEntity);
    if (false == result) {
      LOGGER.warn("deleteClusterMeta faild, Cache data to queue.");
      clusterMetaQueue.add(metaEntity);
    }
  }

  // get metadata by cluster metadata
  public HashMap<String, HashMap<String, Object>> getClusterMeta(
      ClusterMetaType metaType, String metaKey) {
    HashMap<String, HashMap<String, Object>> clusterMeta = new HashMap<>();
    if (!raftInitialized()) {
      LOGGER.error("Raft incomplete initialization!");
      return clusterMeta;
    }

    ClusterMetaEntity entity = new ClusterMetaEntity(GET_OPERATION, metaType, metaKey, null);

    byte[] mateData = null;
    try {
      mateData = raftSessionClient.execute(operation(ClusterStateMachine.GET,
          clientSerializer.encode(entity))).get(3, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage());
    } catch (ExecutionException e) {
      LOGGER.error(e.getMessage());
    } catch (TimeoutException e) {
      LOGGER.error(e.getMessage());
    }

    if (null != mateData) {
      clusterMeta = clientSerializer.decode(mateData);
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("getClusterMeta >>> {}", clusterMeta.toString());
    }

    return clusterMeta;
  }

  public InterpreterClient getIntpProcessStatus(String intpName,
                                                int timeout,
                                                ClusterCallback<HashMap<String, Object>> callback) {
    final int CHECK_META_INTERVAL = 1000;
    int MAX_RETRY_GET_META = timeout / CHECK_META_INTERVAL;
    int retryGetMeta = 0;
    while (retryGetMeta++ < MAX_RETRY_GET_META) {
      HashMap<String, Object> intpMeta = getClusterMeta(INTP_PROCESS_META, intpName).get(intpName);
      if (interpreterMetaOnline(intpMeta)) {
        // connect exist Interpreter Process
        String intpTSrvHost = (String) intpMeta.get(INTP_TSERVER_HOST);
        int intpTSrvPort = (int) intpMeta.get(INTP_TSERVER_PORT);
        LOGGER.info("interpreter thrift {}:{} service is online!", intpTSrvHost, intpTSrvPort);

        // Check if the interpreter thrift service is available
        boolean remoteIntpAccessible =
            RemoteInterpreterUtils.checkIfRemoteEndpointAccessible(intpTSrvHost, intpTSrvPort);
        if (remoteIntpAccessible) {
          LOGGER.info("interpreter thrift {}:{} accessible!", intpTSrvHost, intpTSrvPort);
          return callback.online(intpMeta);
        } else {
          LOGGER.error("interpreter thrift {}:{} service is not available!",
              intpTSrvHost, intpTSrvPort);
          try {
            Thread.sleep(CHECK_META_INTERVAL);
            LOGGER.warn("retry {} times to get {} meta!", retryGetMeta, intpName);
          } catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
          }
        }
      } else {
        try {
          Thread.sleep(CHECK_META_INTERVAL);
        } catch (InterruptedException e) {
          LOGGER.error(e.getMessage(), e);
        }
      }
    }

    LOGGER.error("retry {} times not get {} meta!", retryGetMeta, intpName);
    callback.offline();
    return null;
  }

  // Check if the interpreter is online
  private boolean interpreterMetaOnline(HashMap<String, Object> intpProcMeta) {
    if (null != intpProcMeta
        && intpProcMeta.containsKey(INTP_TSERVER_HOST)
        && intpProcMeta.containsKey(INTP_TSERVER_PORT)
        && intpProcMeta.containsKey(STATUS)
        && StringUtils.equals((String) intpProcMeta.get(STATUS), ONLINE_STATUS)) {
      return true;
    }

    return false;
  }

  protected static final Serializer protocolSerializer = Serializer.using(Namespace.builder()
      .register(OpenSessionRequest.class)
      .register(OpenSessionResponse.class)
      .register(CloseSessionRequest.class)
      .register(CloseSessionResponse.class)
      .register(KeepAliveRequest.class)
      .register(KeepAliveResponse.class)
      .register(QueryRequest.class)
      .register(QueryResponse.class)
      .register(CommandRequest.class)
      .register(CommandResponse.class)
      .register(MetadataRequest.class)
      .register(MetadataResponse.class)
      .register(JoinRequest.class)
      .register(JoinResponse.class)
      .register(LeaveRequest.class)
      .register(LeaveResponse.class)
      .register(ConfigureRequest.class)
      .register(ConfigureResponse.class)
      .register(ReconfigureRequest.class)
      .register(ReconfigureResponse.class)
      .register(InstallRequest.class)
      .register(InstallResponse.class)
      .register(PollRequest.class)
      .register(PollResponse.class)
      .register(VoteRequest.class)
      .register(VoteResponse.class)
      .register(AppendRequest.class)
      .register(AppendResponse.class)
      .register(PublishRequest.class)
      .register(ResetRequest.class)
      .register(RaftResponse.Status.class)
      .register(RaftError.class)
      .register(RaftError.Type.class)
      .register(PrimitiveOperation.class)
      .register(ReadConsistency.class)
      .register(byte[].class)
      .register(long[].class)
      .register(CloseSessionEntry.class)
      .register(CommandEntry.class)
      .register(ConfigurationEntry.class)
      .register(InitializeEntry.class)
      .register(KeepAliveEntry.class)
      .register(MetadataEntry.class)
      .register(OpenSessionEntry.class)
      .register(QueryEntry.class)
      .register(PrimitiveOperation.class)
      .register(DefaultOperationId.class)
      .register(OperationType.class)
      .register(ReadConsistency.class)
      .register(ArrayList.class)
      .register(HashMap.class)
      .register(ClusterMetaEntity.class)
      .register(LocalDateTime.class)
      .register(Collections.emptyList().getClass())
      .register(HashSet.class)
      .register(DefaultRaftMember.class)
      .register(MemberId.class)
      .register(SessionId.class)
      .register(RaftMember.Type.class)
      .register(Instant.class)
      .register(Configuration.class)
      .build());

  protected static final Serializer storageSerializer = Serializer.using(Namespace.builder()
      .register(CloseSessionEntry.class)
      .register(CommandEntry.class)
      .register(ConfigurationEntry.class)
      .register(InitializeEntry.class)
      .register(KeepAliveEntry.class)
      .register(MetadataEntry.class)
      .register(OpenSessionEntry.class)
      .register(QueryEntry.class)
      .register(PrimitiveOperation.class)
      .register(DefaultOperationId.class)
      .register(OperationType.class)
      .register(ReadConsistency.class)
      .register(ArrayList.class)
      .register(ClusterMetaEntity.class)
      .register(HashMap.class)
      .register(HashSet.class)
      .register(LocalDateTime.class)
      .register(DefaultRaftMember.class)
      .register(MemberId.class)
      .register(RaftMember.Type.class)
      .register(Instant.class)
      .register(Configuration.class)
      .register(byte[].class)
      .register(long[].class)
      .build());

  protected static final Serializer clientSerializer = Serializer.using(Namespace.builder()
      .register(ReadConsistency.class)
      .register(ClusterMetaEntity.class)
      .register(ClusterMetaOperation.class)
      .register(ClusterMetaType.class)
      .register(HashMap.class)
      .register(LocalDateTime.class)
      .register(Maps.immutableEntry(new String(), new Object()).getClass())
      .build());
}
