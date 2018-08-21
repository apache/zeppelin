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
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

import java.net.ConnectException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Messaging service based Raft protocol.
 */
public abstract class RaftMessagingProtocol {
  protected final MessagingService messagingService;
  protected final Serializer serializer;
  private final Function<MemberId, Address> addressProvider;

  public RaftMessagingProtocol(MessagingService messagingService,
                               Serializer serializer,
                               Function<MemberId, Address> addressProvider) {
    this.messagingService = messagingService;
    this.serializer = serializer;
    this.addressProvider = addressProvider;
  }

  protected Address address(MemberId memberId) {
    return addressProvider.apply(memberId);
  }

  protected <T, U> CompletableFuture<U> sendAndReceive(MemberId memberId,
                                                       String type, T request) {
    Address address = address(memberId);
    if (address == null) {
      return Futures.exceptionalFuture(new ConnectException());
    }
    return messagingService.sendAndReceive(address, type, serializer.encode(request))
        .thenApply(serializer::decode);
  }

  protected CompletableFuture<Void> sendAsync(MemberId memberId, String type, Object request) {
    Address address = address(memberId);
    if (address != null) {
      return messagingService.sendAsync(address(memberId), type, serializer.encode(request));
    }
    return CompletableFuture.completedFuture(null);
  }

  protected <T, U> void registerHandler(String type, Function<T, CompletableFuture<U>> handler) {
    messagingService.registerHandler(type, (e, p) -> {
      CompletableFuture<byte[]> future = new CompletableFuture<>();
      handler.apply(serializer.decode(p)).whenComplete((result, error) -> {
        if (error == null) {
          future.complete(serializer.encode(result));
        } else {
          future.completeExceptionally(error);
        }
      });
      return future;
    });
  }

  protected void unregisterHandler(String type) {
    messagingService.unregisterHandler(type);
  }
}
