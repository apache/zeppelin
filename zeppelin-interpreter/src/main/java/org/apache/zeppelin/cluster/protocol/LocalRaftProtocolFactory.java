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
import io.atomix.protocols.raft.protocol.RaftClientProtocol;
import io.atomix.protocols.raft.protocol.RaftServerProtocol;
import io.atomix.utils.serializer.Serializer;

import java.util.Map;

/**
 * Cluster Raft protocol factory.
 */
public class LocalRaftProtocolFactory {
  private final Serializer serializer;
  private final Map<MemberId, LocalRaftServerProtocol> servers = Maps.newConcurrentMap();
  private final Map<MemberId, LocalRaftClientProtocol> clients = Maps.newConcurrentMap();

  public LocalRaftProtocolFactory(Serializer serializer) {
    this.serializer = serializer;
  }

  /**
   * Returns a new test client protocol.
   *
   * @param memberId the client member identifier
   * @return a new test client protocol
   */
  public RaftClientProtocol newClientProtocol(MemberId memberId) {
    return new LocalRaftClientProtocol(memberId, serializer, servers, clients);
  }

  /**
   * Returns a new test server protocol.
   *
   * @param memberId the server member identifier
   * @return a new test server protocol
   */
  public RaftServerProtocol newServerProtocol(MemberId memberId) {
    return new LocalRaftServerProtocol(memberId, serializer, servers, clients);
  }
}
