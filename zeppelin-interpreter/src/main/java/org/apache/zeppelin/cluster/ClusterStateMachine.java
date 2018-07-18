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

import io.atomix.copycat.server.Commit;
import io.atomix.copycat.server.Snapshottable;
import io.atomix.copycat.server.StateMachine;
import io.atomix.copycat.server.StateMachineExecutor;
import io.atomix.copycat.server.storage.snapshot.SnapshotReader;
import io.atomix.copycat.server.storage.snapshot.SnapshotWriter;
import org.apache.zeppelin.cluster.meta.ClusterMeta;

/**
 * Cluster State Machine
 */
public class ClusterStateMachine extends StateMachine implements Snapshottable {
  private ClusterMeta clusterMeta = new ClusterMeta();

  @Override
  protected void configure(StateMachineExecutor executor) {
    executor.register(PutCommand.class, this::put);
    executor.register(GetCommand.class, this::get);
    executor.register(DeleteCommand.class, this::delete);
  }

  public Object put(Commit<PutCommand> commit) {
    try {
      clusterMeta.put(
          commit.operation().type(),
          commit.operation().key(), commit.operation().value());
    } finally {
      commit.close();
    }
    return null;
  }

  public Object get(Commit<GetCommand> commit) {
    try {
      return clusterMeta.get(commit.operation().type(), commit.operation().key());
    } finally {
      commit.close();
    }
  }

  /**
   * Deletes the value.
   */
  private Object delete(Commit<DeleteCommand> commit) {
    try {
      if (clusterMeta.contains(commit.operation().type(), commit.operation().key())) {
        clusterMeta.remove(commit.operation().type(), commit.operation().key());
      }
      return null;
    } finally {
      commit.close();
    }
  }

  // The following two methods come from interfaces that implement Snapshottable,
  // This interface is implemented to enable the replication server to compress the
  // local status log.
  // And form a snapshot (snapshot), when copycat-server restarts,
  // you can restore the status from the snapshot,
  // If other servers are added, snapshots can be copied to other servers.
  @Override
  public void snapshot(SnapshotWriter writer) {
    writer.writeObject(clusterMeta);
  }

  @Override
  public void install(SnapshotReader reader) {
    clusterMeta = reader.readObject();
  }
}
