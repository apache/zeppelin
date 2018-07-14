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

import io.atomix.catalyst.transport.Address;
import io.atomix.catalyst.transport.netty.NettyTransport;
import io.atomix.copycat.server.CopycatServer;
import io.atomix.copycat.server.storage.Storage;
import io.atomix.copycat.server.storage.StorageLevel;
import org.apache.commons.collections.map.HashedMap;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.zeppelin.cluster.meta.ClusterMeta;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterNotFoundException;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.apache.zeppelin.interpreter.thrift.ClusterInterpreterParam;
import org.apache.zeppelin.interpreter.thrift.ClusterManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.apache.zeppelin.cluster.meta.ClusterMetaType.ServerMeta;

/**
 * Because ClusterManagerServer needs to get the annotation
 * interpreter through InterpreterFactory,
 * InterpreterFactory belongs to zeppelin-zengine module,
 * so you can only use ClusterManagerServer separately.
 * placed in the zeppelin-zengine module.
 *
 * Cluster management server class instantiated in zeppelin-server
 * 1. Create a raft server
 * 2. Remotely create interpreter's thrift service
 */
public class ClusterManagerServer extends ClusterManager {
  private static Logger logger = LoggerFactory.getLogger(ClusterManagerServer.class);

  private static ClusterManagerServer instance = null;

  // Raft server
  private CopycatServer copycatServer = null;

  // Find interpreter by note
  private InterpreterFactory interpreterFactory = null;

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

  public void start(InterpreterFactory interpreterFactory) {
    if (!isClusterMode()) {
      return;
    }
    this.interpreterFactory = interpreterFactory;

    initThread();

    // Instantiated cluster monitoring class
    String clusterName = getClusterName();
    clusterMonitor = new ClusterMonitor(this);
    clusterMonitor.start(ServerMeta, clusterName);

    super.start();
  }

  private void initThread() {
    // CopycatServer Thread
    new Thread(new Runnable() {
      @Override
      public void run() {
        // cluster ip and port
        Address localAddress = new Address(zServerHost, zServerPort);

        // Create copycat server by chain
        copycatServer = CopycatServer.builder(localAddress)
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
            logger.info("CopycatServer.State CANDIDATE!");
          } else if (state == CopycatServer.State.FOLLOWER) {
            logger.info("CopycatServer.State FOLLOWER!");
          } else if (state == CopycatServer.State.INACTIVE) {
            logger.info("CopycatServer.State INACTIVE!");
          } else if (state == CopycatServer.State.LEADER) {
            logger.info("CopycatServer.State LEADER!");
          } else if (state == CopycatServer.State.PASSIVE) {
            logger.info("CopycatServer.State PASSIVE!");
          } else {
            logger.error("unknown CopycatServer.State {}!", state);
          }
        });

        copycatServer.cluster().onJoin(member -> {
          logger.info(member.address() + " joined the cluster.");
        });

        copycatServer.cluster().onLeave(member -> {
          logger.warn(member.address() + " left the cluster.");
        });

        if (clusterMembers.size() > 1) {
          copycatServer.bootstrap(clusterMembers).join();
        } else {
          copycatServer.bootstrap().join();
        }
        logger.info("CopycatServerThread run() <<<");
      }
    }).start();
  }

  @Override
  public void shutdown() {
    if (!isClusterMode()) {
      return;
    }

    try {
      // delete local machine meta
      deleteClusterMeta(ServerMeta, getClusterName());
      clusterMonitor.shutdown();
      // wait copycat commit
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    if (copycatServer.isRunning()) {
      copycatServer.shutdown();
    }

    super.shutdown();
  }

  @Override
  public CopycatServer.State clusterStatus() {
    if (null != copycatServer) {
      return copycatServer.state();
    } else {
      return null;
    }
  }
}
