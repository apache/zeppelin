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

package org.apache.zeppelin.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.Header;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.common.Message;
import org.apache.zeppelin.common.Message.OP;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.exceptions.UpgradeException;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebSocketAuthenticationTest extends AbstractTestRestApi {

  private static final Gson GSON = new Gson();
  private static MiniZeppelinServer zepServer;
  private static WebSocketClient webSocketClient;

  @BeforeAll
  static void startServer() throws Exception {
    zepServer = new MiniZeppelinServer(WebSocketAuthenticationTest.class.getSimpleName());
    zepServer.addConfigFile("shiro.ini", ZEPPELIN_SHIRO);
    zepServer.start();
    zepServer.getZeppelinConfiguration().setProperty(
        ConfVars.ZEPPELIN_ALLOWED_ORIGINS.getVarName(),
        "http://localhost:" + zepServer.getZeppelinConfiguration().getServerPort());
    webSocketClient = new WebSocketClient();
    webSocketClient.getHttpClient().setFollowRedirects(false);
    webSocketClient.start();
  }

  @AfterAll
  static void stopServer() throws Exception {
    if (webSocketClient != null) {
      webSocketClient.stop();
    }
    if (zepServer != null) {
      zepServer.destroy();
    }
  }

  @BeforeEach
  void setUpConfiguration() {
    zConf = zepServer.getZeppelinConfiguration();
  }

  @Test
  void handshakeWithoutAnAuthenticatedRestSessionIsRejected() throws Exception {
    HttpGet upgrade = new HttpGet(websocketUri().toString().replaceFirst("^ws", "http"));
    upgrade.setHeader("Connection", "Upgrade");
    upgrade.setHeader("Upgrade", "websocket");
    upgrade.setHeader("Sec-WebSocket-Version", "13");
    upgrade.setHeader("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==");
    upgrade.setHeader("Origin", validOrigin());

    try (CloseableHttpClient client = HttpClients.custom().disableRedirectHandling().build();
         CloseableHttpResponse response = client.execute(upgrade)) {
      assertEquals(302, response.getStatusLine().getStatusCode());
      Header location = response.getFirstHeader("Location");
      assertNotNull(location);
      assertEquals("/api/login", URI.create(location.getValue()).getPath());
    }
  }

  @Test
  void invalidOriginIsRejectedBeforeWebSocketOpen() throws Exception {
    String cookie = getCookie("user1", "password2");
    TestSocket socket = new TestSocket();

    ExecutionException failure = assertThrows(ExecutionException.class,
        () -> connect(socket, cookie, "https://evil.example").get(10, TimeUnit.SECONDS));

    UpgradeException upgradeException = findUpgradeException(failure);
    assertNotNull(upgradeException);
    assertEquals(403, upgradeException.getResponseStatusCode());
    assertFalse(socket.isConnected());
  }

  @Test
  void websocketUsesTheRestSessionAndIgnoresForgedMessageIdentity() throws Exception {
    String cookie = getCookie("user1", "password2");
    TestSocket socket = new TestSocket();
    Session session = connect(socket, cookie, validOrigin()).get(10, TimeUnit.SECONDS);
    String noteName = "/websocket-auth-" + System.nanoTime();
    Message createNote = new Message(OP.NEW_NOTE).put("name", noteName);
    createNote.principal = "user2";
    createNote.roles = "[\"admin\"]";
    createNote.ticket = "forged-ticket";

    session.getRemote().sendString(createNote.toJson());
    Message response = socket.awaitMessage(OP.NEW_NOTE);
    @SuppressWarnings("unchecked")
    String noteId = String.valueOf(((Map<String, Object>) response.get("note")).get("id"));

    AuthorizationService authorizationService = zepServer.getService(AuthorizationService.class);
    assertEquals(Set.of("user1"), authorizationService.getOwners(noteId));
    session.close();
  }

  @Test
  void classicWebAppUsesTheSecurityManagerThatAuthenticatedItsRestSession() throws Exception {
    String cookie = getCookieFromContext("/classic/api", "user1", "password2");
    TestSocket socket = new TestSocket();
    Session session = connect(
        socket,
        cookie,
        validOrigin(),
        URI.create("ws://localhost:" + zConf.getServerPort() + "/classic/ws"))
        .get(10, TimeUnit.SECONDS);

    session.getRemote().sendString(new Message(OP.LIST_NOTES).toJson());
    socket.awaitMessage(OP.NOTES_INFO);
    session.close();
  }

  @Test
  void restLogoutClosesTheWebSocketForThatSession() throws Exception {
    String cookie = getCookie("user1", "password2");
    TestSocket socket = new TestSocket();
    connect(socket, cookie, validOrigin()).get(10, TimeUnit.SECONDS);

    HttpPost logout = new HttpPost(getUrlToTest(zConf) + "/login/logout");
    logout.setHeader("Origin", getOriginToTest(zConf));
    logout.setHeader("Cookie", "JSESSIONID=" + cookie);
    try (CloseableHttpResponse ignored = getHttpClient().execute(logout)) {
      assertEquals(1008, socket.awaitCloseCode());
    }
  }

  @Test
  void reloginClosesTheWebSocketForThePreviousSession() throws Exception {
    String cookie = getCookie("user1", "password2");
    TestSocket socket = new TestSocket();
    connect(socket, cookie, validOrigin()).get(10, TimeUnit.SECONDS);

    HttpPost login = new HttpPost(getUrlToTest(zConf) + "/login");
    login.setHeader("Origin", getOriginToTest(zConf));
    login.setHeader("Cookie", "JSESSIONID=" + cookie);
    login.setEntity(new UrlEncodedFormEntity(List.of(
        new BasicNameValuePair("userName", "user2"),
        new BasicNameValuePair("password", "password3")), StandardCharsets.UTF_8));
    try (CloseableHttpResponse response = getHttpClient().execute(login)) {
      assertEquals(200, response.getStatusLine().getStatusCode());
      assertEquals(1008, socket.awaitCloseCode());
    }
  }

  private CompletableFuture<Session> connect(
      TestSocket socket, String sessionCookie, String origin) throws Exception {
    return connect(socket, sessionCookie, origin, websocketUri());
  }

  private CompletableFuture<Session> connect(
      TestSocket socket, String sessionCookie, String origin, URI uri) throws Exception {
    ClientUpgradeRequest request = new ClientUpgradeRequest();
    request.setHeader("Origin", origin);
    if (sessionCookie != null) {
      request.setHeader("Cookie", "JSESSIONID=" + sessionCookie);
    }
    return webSocketClient.connect(socket, uri, request);
  }

  private String getCookieFromContext(
      String apiPath, String userName, String password) throws Exception {
    HttpPost login = new HttpPost(
        "http://localhost:" + zConf.getServerPort() + apiPath + "/login");
    login.setHeader("Origin", validOrigin());
    login.setEntity(new UrlEncodedFormEntity(List.of(
        new BasicNameValuePair("userName", userName),
        new BasicNameValuePair("password", password)), StandardCharsets.UTF_8));
    try (CloseableHttpResponse response = getHttpClient().execute(login)) {
      assertEquals(200, response.getStatusLine().getStatusCode());
      Pattern sessionCookie = Pattern.compile("JSESSIONID=([a-zA-Z0-9-]+)");
      String finalSessionCookie = null;
      for (Header header : response.getHeaders("Set-Cookie")) {
        Matcher matcher = sessionCookie.matcher(header.getValue());
        if (matcher.find()) {
          finalSessionCookie = matcher.group(1);
        }
      }
      if (finalSessionCookie != null) {
        return finalSessionCookie;
      }
    }
    throw new AssertionError("Login did not issue a JSESSIONID cookie for " + apiPath);
  }

  private URI websocketUri() {
    return URI.create("ws://localhost:" + zConf.getServerPort() + "/ws");
  }

  private String validOrigin() {
    return "http://localhost:" + zConf.getServerPort();
  }

  private static UpgradeException findUpgradeException(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof UpgradeException) {
        return (UpgradeException) current;
      }
      current = current.getCause();
    }
    return null;
  }

  private static final class TestSocket extends WebSocketAdapter {
    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    private final CompletableFuture<Integer> closeCode = new CompletableFuture<>();

    @Override
    public void onWebSocketText(String message) {
      messages.add(message);
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
      closeCode.complete(statusCode);
      super.onWebSocketClose(statusCode, reason);
    }

    Message awaitMessage(OP expectedOperation) throws Exception {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (System.nanoTime() < deadline) {
        long remaining = deadline - System.nanoTime();
        String serialized = messages.poll(remaining, TimeUnit.NANOSECONDS);
        if (serialized == null) {
          break;
        }
        Message message = GSON.fromJson(serialized, Message.class);
        if (message.op == expectedOperation) {
          return message;
        }
      }
      throw new AssertionError("Did not receive WebSocket operation " + expectedOperation);
    }

    int awaitCloseCode() throws Exception {
      return closeCode.get(10, TimeUnit.SECONDS);
    }
  }
}
