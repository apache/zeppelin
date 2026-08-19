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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class ZeppelinClientTest {

  @Test
  void clientsKeepRestAndWebSocketCookiesIsolatedAndScoped() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/login", ZeppelinClientTest::login);
    server.createContext("/api/version", ZeppelinClientTest::version);
    server.start();
    int port = server.getAddress().getPort();
    String baseUrl = "http://localhost:" + port;

    try (ZeppelinClient first = new ZeppelinClient(new ClientConfig(baseUrl));
         ZeppelinClient second = new ZeppelinClient(new ClientConfig(baseUrl))) {
      first.login("user1", "password");
      second.login("user2", "password");

      assertEquals("user1", first.getVersion());
      assertEquals("user2", second.getVersion());

      URI wsUri = new URI("ws://localhost:" + port + "/ws");
      String firstCookies = first.getWebSocketCookieHeader(wsUri);
      String secondCookies = second.getWebSocketCookieHeader(wsUri);
      assertTrue(firstCookies.contains("JSESSIONID=user1"));
      assertTrue(secondCookies.contains("JSESSIONID=user2"));
      assertTrue(firstCookies.contains("WS_ONLY=ws"));
      assertFalse(firstCookies.contains("API_ONLY"));
      assertFalse(firstCookies.contains("SECURE_ONLY"));

      String encodedPathCookies = first.getWebSocketCookieHeader(
          new URI("ws://localhost:" + port + "/gateway%2Fzeppelin/ws"));
      assertTrue(encodedPathCookies.contains("ENCODED_CONTEXT=encoded"));

      String secureCookies = first.getWebSocketCookieHeader(
          new URI("wss://localhost:" + port + "/ws"));
      assertTrue(secureCookies.contains("SECURE_ONLY=secure"));
      assertEquals("", first.getWebSocketCookieHeader(
          new URI("ws://127.0.0.1:" + port + "/ws")));
    } finally {
      server.stop(0);
    }
  }

  private static void login(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    String user = body.contains("userName=user2") ? "user2" : "user1";
    exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=" + user + "; Path=/");
    exchange.getResponseHeaders().add("Set-Cookie", "WS_ONLY=ws; Path=/ws");
    exchange.getResponseHeaders().add("Set-Cookie", "API_ONLY=api; Path=/api");
    exchange.getResponseHeaders().add("Set-Cookie", "SECURE_ONLY=secure; Path=/; Secure");
    exchange.getResponseHeaders().add(
        "Set-Cookie", "ENCODED_CONTEXT=encoded; Path=/gateway/zeppelin");
    respond(exchange, "{\"status\":\"OK\"}");
  }

  private static void version(HttpExchange exchange) throws IOException {
    String cookie = exchange.getRequestHeaders().getFirst("Cookie");
    String user = cookie != null && cookie.contains("JSESSIONID=user2") ? "user2" : "user1";
    respond(exchange,
        "{\"status\":\"OK\",\"message\":\"\",\"body\":{\"version\":\""
            + user + "\"}}");
  }

  private static void respond(HttpExchange exchange, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
