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

package org.apache.zeppelin.client.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.net.URI;

class ZeppelinWebSocketClientTest {

  @Test
  void upgradeRequestIncludesRestSessionCookies() throws Exception {
    ZeppelinWebSocketClient client = new ZeppelinWebSocketClient(msg -> { });

    ClientUpgradeRequest request = client.createUpgradeRequest(
            URI.create("wss://knox.example/gateway/default/zeppelin/ws"),
            "JSESSIONID=session-id; hadoop-jwt=knox-token");

    assertEquals("JSESSIONID=session-id; hadoop-jwt=knox-token",
            request.getHeader("Cookie"));
    assertEquals("https://knox.example", request.getHeader("Origin"));
  }

  @Test
  void upgradeRequestOmitsCookieHeaderForAnonymousSession() throws Exception {
    ZeppelinWebSocketClient client = new ZeppelinWebSocketClient(msg -> { });

    ClientUpgradeRequest request = client.createUpgradeRequest(
            URI.create("ws://localhost:8080/ws"), "  ");

    assertNull(request.getHeader("Cookie"));
    assertEquals("http://localhost:8080", request.getHeader("Origin"));
  }

  @Test
  void upgradeRequestCanonicalizesDefaultOriginPorts() throws Exception {
    ZeppelinWebSocketClient client = new ZeppelinWebSocketClient(msg -> { });

    ClientUpgradeRequest secure = client.createUpgradeRequest(
            URI.create("wss://zeppelin.example:443/ws"), null);
    ClientUpgradeRequest plain = client.createUpgradeRequest(
            URI.create("ws://zeppelin.example:80/ws"), null);

    assertEquals("https://zeppelin.example", secure.getHeader("Origin"));
    assertEquals("http://zeppelin.example", plain.getHeader("Origin"));
  }

  @Test
  void connectFailsFastWhenPortClosed() {
    ZeppelinWebSocketClient client = new ZeppelinWebSocketClient(msg -> { });

    assertTimeoutPreemptively(Duration.ofSeconds(20), () ->
        assertThrows(Exception.class, () -> client.connect("ws://127.0.0.1:1/ws")));
  }

}
