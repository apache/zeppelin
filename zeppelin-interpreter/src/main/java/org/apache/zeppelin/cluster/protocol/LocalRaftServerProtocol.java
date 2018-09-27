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
import io.atomix.protocols.raft.protocol.RaftServerProtocol;
import io.atomix.protocols.raft.protocol.OpenSessionRequest;
import io.atomix.protocols.raft.protocol.OpenSessionResponse;
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
import io.atomix.protocols.raft.protocol.TransferRequest;
import io.atomix.protocols.raft.protocol.TransferResponse;
import io.atomix.protocols.raft.protocol.AppendRequest;
import io.atomix.protocols.raft.protocol.AppendResponse;
import io.atomix.protocols.raft.protocol.ResetRequest;
import io.atomix.protocols.raft.protocol.PublishRequest;
import io.atomix.protocols.raft.protocol.HeartbeatResponse;
import io.atomix.protocols.raft.protocol.HeartbeatRequest;

import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.serializer.Serializer;

import java.net.ConnectException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Cluster server protocol.
 */
public class LocalRaftServerProtocol extends LocalRaftProtocol implements RaftServerProtocol {
  private Function<OpenSessionRequest, CompletableFuture<OpenSessionResponse>> openSessionHandler;
  private Function<CloseSessionRequest, CompletableFuture<CloseSessionResponse>>
      closeSessionHandler;
  private Function<KeepAliveRequest, CompletableFuture<KeepAliveResponse>> keepAliveHandler;
  private Function<QueryRequest, CompletableFuture<QueryResponse>> queryHandler;
  private Function<CommandRequest, CompletableFuture<CommandResponse>> commandHandler;
  private Function<MetadataRequest, CompletableFuture<MetadataResponse>> metadataHandler;
  private Function<JoinRequest, CompletableFuture<JoinResponse>> joinHandler;
  private Function<LeaveRequest, CompletableFuture<LeaveResponse>> leaveHandler;
  private Function<ConfigureRequest, CompletableFuture<ConfigureResponse>> configureHandler;
  private Function<ReconfigureRequest, CompletableFuture<ReconfigureResponse>> reconfigureHandler;
  private Function<InstallRequest, CompletableFuture<InstallResponse>> installHandler;
  private Function<PollRequest, CompletableFuture<PollResponse>> pollHandler;
  private Function<VoteRequest, CompletableFuture<VoteResponse>> voteHandler;
  private Function<TransferRequest, CompletableFuture<TransferResponse>> transferHandler;
  private Function<AppendRequest, CompletableFuture<AppendResponse>> appendHandler;
  private final Map<Long, Consumer<ResetRequest>> resetListeners = Maps.newConcurrentMap();

  public LocalRaftServerProtocol(MemberId memberId, Serializer serializer,
                                 Map<MemberId, LocalRaftServerProtocol> servers,
                                 Map<MemberId, LocalRaftClientProtocol> clients) {
    super(serializer, servers, clients);
    servers.put(memberId, this);
  }

  private CompletableFuture<LocalRaftServerProtocol> getServer(MemberId memberId) {
    LocalRaftServerProtocol server = server(memberId);
    if (server != null) {
      return Futures.completedFuture(server);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  private CompletableFuture<LocalRaftClientProtocol> getClient(MemberId memberId) {
    LocalRaftClientProtocol client = client(memberId);
    if (client != null) {
      return Futures.completedFuture(client);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public CompletableFuture<OpenSessionResponse> openSession(MemberId memberId,
                                                            OpenSessionRequest request) {
    return getServer(memberId).thenCompose(listener ->
        listener.openSession(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<CloseSessionResponse> closeSession(MemberId memberId,
                                                              CloseSessionRequest request) {
    return getServer(memberId).thenCompose(listener ->
        listener.closeSession(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<KeepAliveResponse> keepAlive(MemberId memberId,
                                                        KeepAliveRequest request) {
    return getServer(memberId).thenCompose(listener ->
        listener.keepAlive(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<QueryResponse> query(MemberId memberId, QueryRequest request) {
    return getServer(memberId).thenCompose(listener ->
        listener.query(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<CommandResponse> command(MemberId memberId,
                                                    CommandRequest request) {
    return getServer(memberId).thenCompose(listener ->
        listener.command(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<MetadataResponse> metadata(MemberId memberId,
                                                      MetadataRequest request) {
    return getServer(memberId).thenCompose(listener ->
        listener.metadata(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<JoinResponse> join(MemberId memberId, JoinRequest request) {
    return getServer(memberId).thenCompose(listener ->
        listener.join(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<LeaveResponse> leave(MemberId memberId, LeaveRequest request) {
    return getServer(memberId).thenCompose(listener ->
        listener.leave(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<ConfigureResponse> configure(MemberId memberId,
                                                        ConfigureRequest request) {
    return getServer(memberId).thenCompose(listener ->
        listener.configure(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<ReconfigureResponse> reconfigure(MemberId memberId,
                                                            ReconfigureRequest request) {
    return getServer(memberId).thenCompose(listener ->
        listener.reconfigure(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<InstallResponse> install(MemberId memberId, InstallRequest request) {
    return getServer(memberId).thenCompose(listener ->
        listener.install(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<TransferResponse> transfer(MemberId memberId, TransferRequest request) {
    return getServer(memberId).thenCompose(listener ->
        listener.install(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<PollResponse> poll(MemberId memberId, PollRequest request) {
    return getServer(memberId).thenCompose(listener ->
        listener.poll(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<VoteResponse> vote(MemberId memberId, VoteRequest request) {
    return getServer(memberId).thenCompose(listener ->
        listener.vote(encode(request))).thenApply(this::decode);
  }

  @Override
  public CompletableFuture<AppendResponse> append(MemberId memberId, AppendRequest request) {
    return getServer(memberId).thenCompose(listener ->
        listener.append(encode(request))).thenApply(this::decode);
  }

  @Override
  public void publish(MemberId memberId, PublishRequest request) {
    getClient(memberId).thenAccept(protocol ->
        protocol.publish(request.session(), encode(request)));
  }

  @Override
  public CompletableFuture<HeartbeatResponse> heartbeat(MemberId memberId,
                                                        HeartbeatRequest request) {
    return getClient(memberId).thenCompose(protocol ->
        protocol.heartbeat(encode(request))).thenApply(this::decode);
  }

  CompletableFuture<byte[]> openSession(byte[] request) {
    if (openSessionHandler != null) {
      return openSessionHandler.apply(decode(request)).thenApply(this::encode);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public void registerOpenSessionHandler(Function<OpenSessionRequest,
      CompletableFuture<OpenSessionResponse>> handler) {
    this.openSessionHandler = handler;
  }

  @Override
  public void unregisterOpenSessionHandler() {
    this.openSessionHandler = null;
  }

  CompletableFuture<byte[]> closeSession(byte[] request) {
    if (closeSessionHandler != null) {
      return closeSessionHandler.apply(decode(request)).thenApply(this::encode);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public void registerCloseSessionHandler(Function<CloseSessionRequest,
      CompletableFuture<CloseSessionResponse>> handler) {
    this.closeSessionHandler = handler;
  }

  @Override
  public void unregisterCloseSessionHandler() {
    this.closeSessionHandler = null;
  }

  CompletableFuture<byte[]> keepAlive(byte[] request) {
    if (keepAliveHandler != null) {
      return keepAliveHandler.apply(decode(request)).thenApply(this::encode);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public void registerKeepAliveHandler(Function<KeepAliveRequest,
      CompletableFuture<KeepAliveResponse>> handler) {
    this.keepAliveHandler = handler;
  }

  @Override
  public void unregisterKeepAliveHandler() {
    this.keepAliveHandler = null;
  }

  CompletableFuture<byte[]> query(byte[] request) {
    if (queryHandler != null) {
      return queryHandler.apply(decode(request)).thenApply(this::encode);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public void registerQueryHandler(Function<QueryRequest,
      CompletableFuture<QueryResponse>> handler) {
    this.queryHandler = handler;
  }

  @Override
  public void unregisterQueryHandler() {
    this.queryHandler = null;
  }

  CompletableFuture<byte[]> command(byte[] request) {
    if (commandHandler != null) {
      return commandHandler.apply(decode(request)).thenApply(this::encode);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public void registerCommandHandler(Function<CommandRequest,
      CompletableFuture<CommandResponse>> handler) {
    this.commandHandler = handler;
  }

  @Override
  public void unregisterCommandHandler() {
    this.commandHandler = null;
  }

  CompletableFuture<byte[]> metadata(byte[] request) {
    if (metadataHandler != null) {
      return metadataHandler.apply(decode(request)).thenApply(this::encode);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public void registerMetadataHandler(Function<MetadataRequest,
      CompletableFuture<MetadataResponse>> handler) {
    this.metadataHandler = handler;
  }

  @Override
  public void unregisterMetadataHandler() {
    this.metadataHandler = null;
  }

  CompletableFuture<byte[]> join(byte[] request) {
    if (joinHandler != null) {
      return joinHandler.apply(decode(request)).thenApply(this::encode);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public void registerJoinHandler(Function<JoinRequest,
      CompletableFuture<JoinResponse>> handler) {
    this.joinHandler = handler;
  }

  @Override
  public void unregisterJoinHandler() {
    this.joinHandler = null;
  }

  CompletableFuture<byte[]> leave(byte[] request) {
    if (leaveHandler != null) {
      return leaveHandler.apply(decode(request)).thenApply(this::encode);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public void registerLeaveHandler(Function<LeaveRequest,
      CompletableFuture<LeaveResponse>> handler) {
    this.leaveHandler = handler;
  }

  @Override
  public void unregisterLeaveHandler() {
    this.leaveHandler = null;
  }

  CompletableFuture<byte[]> configure(byte[] request) {
    if (configureHandler != null) {
      return configureHandler.apply(decode(request)).thenApply(this::encode);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public void registerConfigureHandler(Function<ConfigureRequest,
      CompletableFuture<ConfigureResponse>> handler) {
    this.configureHandler = handler;
  }

  @Override
  public void unregisterConfigureHandler() {
    this.configureHandler = null;
  }

  CompletableFuture<byte[]> reconfigure(byte[] request) {
    if (reconfigureHandler != null) {
      return reconfigureHandler.apply(decode(request)).thenApply(this::encode);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public void registerReconfigureHandler(Function<ReconfigureRequest,
      CompletableFuture<ReconfigureResponse>> handler) {
    this.reconfigureHandler = handler;
  }

  @Override
  public void unregisterReconfigureHandler() {
    this.reconfigureHandler = null;
  }

  CompletableFuture<byte[]> install(byte[] request) {
    if (installHandler != null) {
      return installHandler.apply(decode(request)).thenApply(this::encode);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public void registerInstallHandler(Function<InstallRequest,
      CompletableFuture<InstallResponse>> handler) {
    this.installHandler = handler;
  }

  @Override
  public void unregisterInstallHandler() {
    this.installHandler = null;
  }

  CompletableFuture<byte[]> poll(byte[] request) {
    if (pollHandler != null) {
      return pollHandler.apply(decode(request)).thenApply(this::encode);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public void registerPollHandler(Function<PollRequest,
      CompletableFuture<PollResponse>> handler) {
    this.pollHandler = handler;
  }

  @Override
  public void unregisterPollHandler() {
    this.pollHandler = null;
  }

  CompletableFuture<byte[]> vote(byte[] request) {
    if (voteHandler != null) {
      return voteHandler.apply(decode(request)).thenApply(this::encode);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public void registerVoteHandler(Function<VoteRequest,
      CompletableFuture<VoteResponse>> handler) {
    this.voteHandler = handler;
  }

  @Override
  public void unregisterVoteHandler() {
    this.voteHandler = null;
  }

  @Override
  public void registerTransferHandler(Function<TransferRequest,
      CompletableFuture<TransferResponse>> handler) {
    this.transferHandler = handler;
  }

  @Override
  public void unregisterTransferHandler() {
    this.transferHandler = null;
  }

  CompletableFuture<byte[]> transfer(byte[] request) {
    if (transferHandler != null) {
      return transferHandler.apply(decode(request)).thenApply(this::encode);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<byte[]> append(byte[] request) {
    if (appendHandler != null) {
      return appendHandler.apply(decode(request)).thenApply(this::encode);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public void registerAppendHandler(Function<AppendRequest,
      CompletableFuture<AppendResponse>> handler) {
    this.appendHandler = handler;
  }

  @Override
  public void unregisterAppendHandler() {
    this.appendHandler = null;
  }

  void reset(long sessionId, byte[] request) {
    Consumer<ResetRequest> listener = resetListeners.get(sessionId);
    if (listener != null) {
      listener.accept(decode(request));
    }
  }

  @Override
  public void registerResetListener(SessionId sessionId,
                                    Consumer<ResetRequest> listener, Executor executor) {
    resetListeners.put(sessionId.id(), request -> executor.execute(()
        -> listener.accept(request)));
  }

  @Override
  public void unregisterResetListener(SessionId sessionId) {
    resetListeners.remove(sessionId.id());
  }
}
