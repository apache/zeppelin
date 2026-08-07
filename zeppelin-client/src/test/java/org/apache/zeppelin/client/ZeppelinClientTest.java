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

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

class ZeppelinClientTest {

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void restSessionCookiesAreIsolatedAndAvailableForWebSocketHandshake() throws Exception {
    AtomicInteger loginCount = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/login", exchange -> {
      try {
        exchange.getRequestBody().readAllBytes();
        String sessionId = "session-" + loginCount.incrementAndGet();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Set-Cookie",
                "JSESSIONID=" + sessionId + "; Path=/; HttpOnly");
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
      } finally {
        exchange.close();
      }
    });
    server.start();

    String serverUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    try (ZeppelinClient firstClient = new ZeppelinClient(new ClientConfig(serverUrl));
         ZeppelinClient secondClient = new ZeppelinClient(new ClientConfig(serverUrl))) {
      firstClient.login("first", "password");
      secondClient.login("second", "password");

      URI webSocketUri = URI.create(serverUrl.replace("http", "ws") + "/ws");
      assertEquals("JSESSIONID=session-1", firstClient.getSessionCookieHeader(webSocketUri));
      assertEquals("JSESSIONID=session-2", secondClient.getSessionCookieHeader(webSocketUri));
    }
  }

  @Test
  void cookieHeaderIsEmptyForAnotherHost() throws Exception {
    server = startServerWithSessionCookie("session-id");
    String serverUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    try (ZeppelinClient client = new ZeppelinClient(new ClientConfig(serverUrl))) {
      client.login("user", "password");

      assertEquals("", client.getSessionCookieHeader(URI.create("ws://localhost/ws")));
    }
  }

  @Test
  void cookieHeaderHonorsCookiePathAndSecureAttributes() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/login", exchange -> {
      try {
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Set-Cookie", "root-cookie=root; Path=/");
        exchange.getResponseHeaders().add("Set-Cookie", "ws-cookie=websocket; Path=/ws");
        exchange.getResponseHeaders().add("Set-Cookie", "api-cookie=rest; Path=/api");
        exchange.getResponseHeaders().add("Set-Cookie", "secure-cookie=secure; Path=/; Secure");
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
      } finally {
        exchange.close();
      }
    });
    server.start();

    String authority = "127.0.0.1:" + server.getAddress().getPort();
    try (ZeppelinClient client = new ZeppelinClient(
            new ClientConfig("http://" + authority))) {
      client.login("user", "password");

      assertEquals("ws-cookie=websocket; root-cookie=root",
              client.getSessionCookieHeader(URI.create("ws://" + authority + "/ws")));
      assertEquals("ws-cookie=websocket; root-cookie=root; secure-cookie=secure",
              client.getSessionCookieHeader(URI.create("wss://" + authority + "/ws")));
    }
  }

  @Test
  void cookieHeaderPreservesPercentEncodedGatewayPath() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/login", exchange -> {
      try {
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add(
                "Set-Cookie", "JSESSIONID=session-id; Path=/gateway/a%2Fb");
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
      } finally {
        exchange.close();
      }
    });
    server.start();

    String authority = "127.0.0.1:" + server.getAddress().getPort();
    try (ZeppelinClient client = new ZeppelinClient(
            new ClientConfig("http://" + authority))) {
      client.login("user", "password");

      assertEquals("JSESSIONID=session-id", client.getSessionCookieHeader(
              URI.create("ws://" + authority + "/gateway/a%2Fb/ws")));
    }
  }

  private HttpServer startServerWithSessionCookie(String sessionId) throws IOException {
    HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    httpServer.createContext("/api/login", exchange -> {
      try {
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Set-Cookie",
                "JSESSIONID=" + sessionId + "; Path=/; HttpOnly");
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
      } finally {
        exchange.close();
      }
    });
    httpServer.start();
    return httpServer;
  }
}
