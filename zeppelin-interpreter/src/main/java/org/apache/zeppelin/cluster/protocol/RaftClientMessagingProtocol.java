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

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.primitive.session.SessionId;
import io.atomix.protocols.raft.protocol.OpenSessionRequest;
import io.atomix.protocols.raft.protocol.OpenSessionResponse;
import io.atomix.protocols.raft.protocol.RaftClientProtocol;
import io.atomix.protocols.raft.protocol.HeartbeatRequest;
import io.atomix.protocols.raft.protocol.PublishRequest;
import io.atomix.protocols.raft.protocol.HeartbeatResponse;
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
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Raft client messaging service protocol.
 */
public class RaftClientMessagingProtocol extends RaftMessagingProtocol
    implements RaftClientProtocol {
  public RaftClientMessagingProtocol(MessagingService messagingService,
                                     Serializer serializer,
                                     Function<MemberId, Address> addressProvider) {
    super(messagingService, serializer, addressProvider);
  }

  @Override
  public CompletableFuture<OpenSessionResponse> openSession(MemberId memberId,
                                                            OpenSessionRequest request) {
    return sendAndReceive(memberId, "open-session", request);
  }

  @Override
  public CompletableFuture<CloseSessionResponse> closeSession(MemberId memberId,
                                                              CloseSessionRequest request) {
    return sendAndReceive(memberId, "close-session", request);
  }

  @Override
  public CompletableFuture<KeepAliveResponse> keepAlive(MemberId memberId,
                                                        KeepAliveRequest request) {
    return sendAndReceive(memberId, "keep-alive", request);
  }

  @Override
  public CompletableFuture<QueryResponse> query(MemberId memberId, QueryRequest request) {
    return sendAndReceive(memberId, "query", request);
  }

  @Override
  public CompletableFuture<CommandResponse> command(MemberId memberId,
                                                    CommandRequest request) {
    return sendAndReceive(memberId, "command", request);
  }

  @Override
  public CompletableFuture<MetadataResponse> metadata(MemberId memberId,
                                                      MetadataRequest request) {
    return sendAndReceive(memberId, "metadata", request);
  }

  @Override
  public void registerHeartbeatHandler(Function<HeartbeatRequest,
      CompletableFuture<HeartbeatResponse>> handler) {
    registerHandler("heartbeat", handler);
  }

  @Override
  public void unregisterHeartbeatHandler() {
    unregisterHandler("heartbeat");
  }

  @Override
  public void reset(Set<MemberId> members, ResetRequest request) {
    for (MemberId memberId : members) {
      sendAsync(memberId, String.format("reset-%d", request.session()), request);
    }
  }

  @Override
  public void registerPublishListener(SessionId sessionId, Consumer<PublishRequest> listener,
                                      Executor executor) {
    messagingService.registerHandler(String.format("publish-%d", sessionId.id()), (e, p) -> {
      listener.accept(serializer.decode(p));
    }, executor);
  }

  @Override
  public void unregisterPublishListener(SessionId sessionId) {
    messagingService.unregisterHandler(String.format("publish-%d", sessionId.id()));
  }
}
