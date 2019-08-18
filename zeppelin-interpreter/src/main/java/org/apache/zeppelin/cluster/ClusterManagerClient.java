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

import io.atomix.primitive.PrimitiveState;

import static org.apache.zeppelin.cluster.meta.ClusterMetaType.INTP_PROCESS_META;

/**
 * Cluster management client class instantiated in zeppelin-interperter
 */
public class ClusterManagerClient extends ClusterManager {
  private static ClusterManagerClient instance = null;

  public static ClusterManagerClient getInstance() {
    synchronized (ClusterManagerClient.class) {
      if (instance == null) {
        instance = new ClusterManagerClient();
      }
      return instance;
    }
  }

  public ClusterManagerClient() {
    super();
  }

  @Override
  public boolean raftInitialized() {
    if (null != raftClient && null != raftSessionClient
        && raftSessionClient.getState() == PrimitiveState.CONNECTED) {
      return true;
    }

    return false;
  }

  @Override
  public boolean isClusterLeader() {
    return false;
  }

  // In the ClusterManagerClient metaKey equal interperterGroupId
  public void start(String metaKey) {
    if (!zconf.isClusterMode()) {
      return;
    }
    super.start();

    // Instantiated cluster monitoring class
    clusterMonitor = new ClusterMonitor(this);
    clusterMonitor.start(INTP_PROCESS_META, metaKey);
  }

  public void shutdown() {
    if (!zconf.isClusterMode()) {
      return;
    }
    clusterMonitor.shutdown();

    super.shutdown();
  }
}
