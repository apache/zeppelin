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
import io.atomix.primitive.operation.OperationId;
import io.atomix.primitive.service.AbstractPrimitiveService;
import io.atomix.primitive.service.BackupOutput;
import io.atomix.primitive.service.BackupInput;
import io.atomix.primitive.service.Commit;
import io.atomix.primitive.service.ServiceExecutor;
import io.atomix.utils.serializer.Serializer;
import org.apache.zeppelin.cluster.meta.ClusterMeta;
import org.apache.zeppelin.cluster.meta.ClusterMetaEntity;
import org.apache.zeppelin.cluster.meta.ClusterMetaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Cluster State Machine for Zeppelin
 * The cluster state is implemented as a snapshot state machine.
 * The state machine stores the service and process metadata information of the cluster.
 * Metadata information can be manipulated by put, get, remove, index, and snapshot.
 */
public class ClusterStateMachine extends AbstractPrimitiveService {
  private static Logger logger = LoggerFactory.getLogger(ClusterStateMachine.class);
  private ClusterMeta clusterMeta = new ClusterMeta();

  // Command to operation a variable in cluster state machine
  public static final OperationId PUT = OperationId.command("put");
  public static final OperationId GET = OperationId.query("get");
  public static final OperationId REMOVE = OperationId.command("remove");
  public static final OperationId INDEX = OperationId.command("index");

  public ClusterStateMachine() {
    super(ClusterPrimitiveType.INSTANCE);
  }

  @Override
  public Serializer serializer() {
    return ClusterManager.clientSerializer;
  }

  @Override
  protected void configure(ServiceExecutor executor) {
    executor.register(PUT, this::put);
    executor.register(GET, this::get);
    executor.register(REMOVE, this::remove);
    executor.register(INDEX, this::index);
  }

  protected long put(Commit<ClusterMetaEntity> commit) {
    clusterMeta.put(commit.value().getMetaType(),
        commit.value().getKey(), commit.value().getValues());
    return commit.index();
  }

  protected Map<String, Map<String, Object>> get(Commit<ClusterMetaEntity> commit) {
    return clusterMeta.get(commit.value().getMetaType(), commit.value().getKey());
  }

  protected long remove(Commit<ClusterMetaEntity> commit) {
    clusterMeta.remove(commit.value().getMetaType(), commit.value().getKey());
    return commit.index();
  }

  protected long index(Commit<Void> commit) {
    return commit.index();
  }

  @Override
  public void backup(BackupOutput writer) {
    if (logger.isDebugEnabled()) {
      logger.debug("ClusterStateMachine.backup()");
    }

    // backup SERVER_META
    // cluster meta map struct
    // cluster_name -> {server_tserver_host,server_tserver_port,cpu_capacity,...}
    Map<String, Map<String, Object>> mapServerMeta
        = clusterMeta.get(ClusterMetaType.SERVER_META, "");
    // write all SERVER_META size
    writer.writeInt(mapServerMeta.size());
    for (Map.Entry<String, Map<String, Object>> entry : mapServerMeta.entrySet()) {
      // write cluster_name
      writer.writeString(entry.getKey());

      Map<String, Object> kvPairs = entry.getValue();
      // write cluster mate kv pairs size
      writer.writeInt(kvPairs.size());
      for (Map.Entry<String, Object> entryValue : kvPairs.entrySet()) {
        // write cluster mate kv pairs
        writer.writeString(entryValue.getKey());
        writer.writeObject(entryValue.getValue());
      }
    }

    // backup INTP_PROCESS_META
    // Interpreter meta map struct
    // IntpGroupId -> {server_tserver_host,server_tserver_port,...}
    Map<String, Map<String, Object>> mapIntpProcMeta
        = clusterMeta.get(ClusterMetaType.INTP_PROCESS_META, "");
    // write interpreter size
    writer.writeInt(mapIntpProcMeta.size());
    for (Map.Entry<String, Map<String, Object>> entry : mapIntpProcMeta.entrySet()) {
      // write IntpGroupId
      writer.writeString(entry.getKey());

      Map<String, Object> kvPairs = entry.getValue();
      // write interpreter mate kv pairs size
      writer.writeInt(kvPairs.size());
      for (Map.Entry<String, Object> entryValue : kvPairs.entrySet()) {
        // write interpreter mate kv pairs
        writer.writeString(entryValue.getKey());
        writer.writeObject(entryValue.getValue());
      }
    }
  }

  @Override
  public void restore(BackupInput reader) {
    if (logger.isDebugEnabled()) {
      logger.debug("ClusterStateMachine.restore()");
    }

    clusterMeta = new ClusterMeta();
    // read all SERVER_META size
    int nServerMeta = reader.readInt();
    for (int i = 0; i < nServerMeta; i++) {
      // read cluster_name
      String clusterName = reader.readString();

      // read cluster mate kv pairs size
      int nKVpairs = reader.readInt();
      for (int j = 0; j < nKVpairs; i++) {
        // read cluster mate kv pairs
        String key = reader.readString();
        Object value = reader.readObject();

        clusterMeta.put(ClusterMetaType.SERVER_META,
            clusterName, Maps.immutableEntry(key, value));
      }
    }

    // read all INTP_PROCESS_META size
    int nIntpMeta = reader.readInt();
    for (int i = 0; i < nIntpMeta; i++) {
      // read interpreter name
      String intpName = reader.readString();

      // read interpreter mate kv pairs size
      int nKVpairs = reader.readInt();
      for (int j = 0; j < nKVpairs; i++) {
        // read interpreter mate kv pairs
        String key = reader.readString();
        Object value = reader.readObject();

        clusterMeta.put(ClusterMetaType.INTP_PROCESS_META,
            intpName, Maps.immutableEntry(key, value));
      }
    }
  }
}
