/*
 * Copyright 2017-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.cluster.protocol;

import com.google.common.collect.Maps;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.session.SessionId;

import io.atomix.protocols.raft.protocol.HeartbeatRequest;
import io.atomix.protocols.raft.protocol.PublishRequest;
import io.atomix.protocols.raft.protocol.RaftClientProtocol;
import io.atomix.protocols.raft.protocol.HeartbeatResponse;
import io.atomix.protocols.raft.protocol.OpenSessionResponse;
import io.atomix.protocols.raft.protocol.OpenSessionRequest;
import io.atomix.protocols.raft.protocol.CloseSessionResponse;
import io.atomix.protocols.raft.protocol.CloseSessionRequest;
import io.atomix.protocols.raft.protocol.KeepAliveResponse;
import io.atomix.protocols.raft.protocol.KeepAliveRequest;
import io.atomix.protocols.raft.protocol.QueryResponse;
import io.atomix.protocols.raft.protocol.QueryRequest;
import io.atomix.protocols.raft.protocol.CommandResponse;
import io.atomix.protocols.raft.protocol.CommandRequest;
import io.atomix.protocols.raft.protocol.MetadataResponse;
import io.atomix.protocols.raft.protocol.MetadataRequest;
import io.atomix.protocols.raft.protocol.ResetRequest;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.serializer.Serializer;

import java.net.ConnectException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Protocol for intercommunication between Raft clients for each server in the cluster.
 * Communication protocol for handling sessions, queries, commands, and services within the cluster.
 */
public class LocalRaftClientProtocol extends LocalRaftProtocol implements RaftClientProtocol {
  private Function<HeartbeatRequest, CompletableFuture<HeartbeatResponse>> heartbeatHandler;
  private final Map<Long, Consumer<PublishRequest>> publishListeners = Maps.newConcurrentMap();

  public LocalRaftClientProtocol(MemberId memberId,
                                 Serializer serializer,
                                 Map<MemberId, LocalRaftServerProtocol> servers,
                                 Map<MemberId, LocalRaftClientProtocol> clients) {
    super(serializer, servers, clients);
    clients.put(memberId, this);
  }

  private CompletableFuture<LocalRaftServerProtocol> getServer(MemberId memberId) {
    LocalRaftServerProtocol server = server(memberId);
    if (server != null) {
      return Futures.completedFuture(server);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public CompletableFuture<OpenSessionResponse> openSession(MemberId memberId,
                                                            OpenSessionRequest request) {
    return getServer(memberId).thenCompose(protocol ->
        protocol.openSession(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<CloseSessionResponse> closeSession(MemberId memberId,
                                                              CloseSessionRequest request) {
    return getServer(memberId).thenCompose(protocol ->
        protocol.closeSession(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<KeepAliveResponse> keepAlive(MemberId memberId,
                                                        KeepAliveRequest request) {
    return getServer(memberId).thenCompose(protocol ->
        protocol.keepAlive(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<QueryResponse> query(MemberId memberId, QueryRequest request) {
    return getServer(memberId).thenCompose(protocol ->
        protocol.query(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<CommandResponse> command(MemberId memberId,
                                                    CommandRequest request) {
    return getServer(memberId).thenCompose(protocol ->
        protocol.command(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<MetadataResponse> metadata(MemberId memberId,
                                                      MetadataRequest request) {
    return getServer(memberId).thenCompose(protocol ->
        protocol.metadata(encode(request))).thenApply(this::decode);
  }

  CompletableFuture<byte[]> heartbeat(byte[] request) {
    if (heartbeatHandler != null) {
      return heartbeatHandler.apply(decode(request)).thenApply(this::encode);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public void registerHeartbeatHandler(Function<HeartbeatRequest,
      CompletableFuture<HeartbeatResponse>> handler) {
    this.heartbeatHandler = handler;
  }

  @Override
  public void unregisterHeartbeatHandler() {
    this.heartbeatHandler = null;
  }

  @Override
  public void reset(Set<MemberId> members, ResetRequest request) {
    members.forEach(nodeId -> {
      LocalRaftServerProtocol server = server(nodeId);
      if (server != null) {
        server.reset(request.session(), encode(request));
      }
    });
  }

  void publish(long sessionId, byte[] request) {
    Consumer<PublishRequest> listener = publishListeners.get(sessionId);
    if (listener != null) {
      listener.accept(decode(request));
    }
  }

  @Override
  public void registerPublishListener(SessionId sessionId,
                                      Consumer<PublishRequest> listener, Executor executor) {
    publishListeners.put(sessionId.id(), request ->
        executor.execute(() -> listener.accept(request)));
  }

  @Override
  public void unregisterPublishListener(SessionId sessionId) {
    publishListeners.remove(sessionId.id());
  }
}
