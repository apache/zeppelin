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
import io.atomix.utils.serializer.Serializer;

import java.util.Map;

/**
 * Base class for Raft protocol.
 */
public abstract class LocalRaftProtocol {
  private final Serializer serializer;
  private final Map<MemberId, LocalRaftServerProtocol> servers;
  private final Map<MemberId, LocalRaftClientProtocol> clients;

  public LocalRaftProtocol(Serializer serializer,
                           Map<MemberId, LocalRaftServerProtocol> servers,
                           Map<MemberId, LocalRaftClientProtocol> clients) {
    this.serializer = serializer;
    this.servers = servers;
    this.clients = clients;
  }

  <T> T copy(T value) {
    return serializer.decode(serializer.encode(value));
  }

  byte[] encode(Object value) {
    return serializer.encode(value);
  }

  <T> T decode(byte[] bytes) {
    return serializer.decode(bytes);
  }

  LocalRaftServerProtocol server(MemberId memberId) {
    return servers.get(memberId);
  }

  LocalRaftClientProtocol client(MemberId memberId) {
    return clients.get(memberId);
  }
}
