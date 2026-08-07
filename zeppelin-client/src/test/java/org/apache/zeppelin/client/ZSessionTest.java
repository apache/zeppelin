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

package org.apache.zeppelin.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ZSessionTest {

  @Test
  void websocketUriPreservesGatewayPathAndSecureTransport() throws Exception {
    assertEquals("wss://knox.example/gateway/default/zeppelin/ws",
            ZSession.toWebSocketUri(
                    "https://knox.example/gateway/default/zeppelin").toString());
  }

  @Test
  void websocketUriNormalizesATrailingGatewaySlash() throws Exception {
    assertEquals("wss://knox.example/gateway/default/zeppelin/ws",
            ZSession.toWebSocketUri(
                    "https://knox.example/gateway/default/zeppelin/").toString());
  }

  @Test
  void websocketUriPreservesPercentEncodedGatewaySegments() throws Exception {
    assertEquals("wss://knox.example/gateway/a%2Fb/ws",
            ZSession.toWebSocketUri("https://knox.example/gateway/a%2Fb").toString());
  }

  @Test
  void websocketUriUsesPlainWebsocketForHttp() throws Exception {
    assertEquals("ws://localhost:8080/ws",
            ZSession.toWebSocketUri("http://localhost:8080").toString());
  }

  @Test
  void websocketUriRejectsUnsupportedSchemes() {
    assertThrows(IllegalArgumentException.class,
            () -> ZSession.toWebSocketUri("ftp://localhost:8080"));
  }

  @Test
  void existingSessionCanAuthenticateBeforeProtectedReconnect() throws Exception {
    AtomicBoolean authenticatedLookup = new AtomicBoolean();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/login", exchange -> {
      try {
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=session-cookie; Path=/");
        writeResponse(exchange, 200, "{}");
      } finally {
        exchange.close();
      }
    });
    server.createContext("/api/session/session-id", exchange -> {
      try {
        if ("GET".equals(exchange.getRequestMethod())) {
          authenticatedLookup.set(
              "JSESSIONID=session-cookie".equals(
                  exchange.getRequestHeaders().getFirst("Cookie")));
          writeResponse(exchange, 200,
              "{\"status\":\"OK\",\"body\":{\"sessionId\":\"session-id\","
                  + "\"state\":\"Running\"}}");
        } else {
          writeResponse(exchange, 200, "{\"status\":\"OK\",\"body\":{}}");
        }
      } finally {
        exchange.close();
      }
    });
    server.start();

    try {
      ClientConfig config = new ClientConfig(
          "http://127.0.0.1:" + server.getAddress().getPort());
      try (ZSession session = ZSession.createFromExistingSession(
          config,
          "spark",
          "session-id",
          client -> client.login("user", "password"),
          null)) {
        assertEquals("session-id", session.getSessionId());
        assertTrue(authenticatedLookup.get());
      }
    } finally {
      server.stop(0);
    }
  }

  private static void writeResponse(
      com.sun.net.httpserver.HttpExchange exchange, int status, String json) throws IOException {
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
  }
}
