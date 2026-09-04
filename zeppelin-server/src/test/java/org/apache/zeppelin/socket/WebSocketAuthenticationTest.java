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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

import com.google.gson.Gson;
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
    allowServerOrigin(zepServer);
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

    try (CloseableHttpClient client = isolatedHttpClient();
         CloseableHttpResponse response = client.execute(upgrade)) {
      assertEquals(302, response.getStatusLine().getStatusCode());
      Header location = response.getFirstHeader("Location");
      assertNotNull(location);
      assertEquals("/api/login", URI.create(location.getValue()).getPath());
    }
  }

  @Test
  void invalidOriginIsRejectedBeforeWebSocketOpen() throws Exception {
    String cookie = login("/api", "user1", "password2");
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
    String cookie = login("/api", "user1", "password2");
    TestSocket socket = new TestSocket();
    Session session = connect(socket, cookie, validOrigin()).get(10, TimeUnit.SECONDS);
    Message createNote = new Message(OP.NEW_NOTE)
        .put("name", "/websocket-auth-" + System.nanoTime());
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
  void concurrentHandshakesKeepTheirServerIdentitiesIsolated() throws Exception {
    String user1Cookie = login("/api", "user1", "password2");
    String user2Cookie = login("/api", "user2", "password3");
    TestSocket user1Socket = new TestSocket();
    TestSocket user2Socket = new TestSocket();

    CompletableFuture<Session> user1Future =
        connect(user1Socket, user1Cookie, validOrigin());
    CompletableFuture<Session> user2Future =
        connect(user2Socket, user2Cookie, validOrigin());
    Session user1Session = user1Future.get(10, TimeUnit.SECONDS);
    Session user2Session = user2Future.get(10, TimeUnit.SECONDS);

    user1Session.getRemote().sendString(
        new Message(OP.NEW_NOTE).put("name", "/user1-" + System.nanoTime()).toJson());
    user2Session.getRemote().sendString(
        new Message(OP.NEW_NOTE).put("name", "/user2-" + System.nanoTime()).toJson());

    String user1NoteId = noteId(user1Socket.awaitMessage(OP.NEW_NOTE));
    String user2NoteId = noteId(user2Socket.awaitMessage(OP.NEW_NOTE));
    AuthorizationService authorizationService = zepServer.getService(AuthorizationService.class);
    assertEquals(Set.of("user1"), authorizationService.getOwners(user1NoteId));
    assertEquals(Set.of("user2"), authorizationService.getOwners(user2NoteId));
    user1Session.close();
    user2Session.close();
  }

  @Test
  void classicContextUsesItsAuthenticatingSecurityManager() throws Exception {
    String cookie = login("/classic/api", "user1", "password2");
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
  void restLogoutInvalidatesTheWebSocketSession() throws Exception {
    String cookie = login("/api", "user1", "password2");
    TestSocket socket = new TestSocket();
    Session session = connect(socket, cookie, validOrigin()).get(10, TimeUnit.SECONDS);

    HttpPost logout = new HttpPost(getUrlToTest(zConf) + "/login/logout");
    logout.setHeader("Origin", validOrigin());
    logout.setHeader("Cookie", "JSESSIONID=" + cookie);
    try (CloseableHttpClient client = isolatedHttpClient();
         CloseableHttpResponse response = client.execute(logout)) {
      assertEquals(401, response.getStatusLine().getStatusCode());
    }
    session.getRemote().sendString(new Message(OP.PING).toJson());
    assertEquals(1008, socket.awaitCloseCode());
  }

  @Test
  void reloginInvalidatesTheOldWebSocketAndIssuesAUsableNewSession() throws Exception {
    String oldCookie = login("/api", "user1", "password2");
    TestSocket oldSocket = new TestSocket();
    Session oldSession = connect(oldSocket, oldCookie, validOrigin())
        .get(10, TimeUnit.SECONDS);

    HttpPost login = loginRequest("/api", "user2", "password3");
    login.setHeader("Cookie", "JSESSIONID=" + oldCookie);
    String newCookie;
    try (CloseableHttpClient client = isolatedHttpClient();
         CloseableHttpResponse response = client.execute(login)) {
      assertEquals(200, response.getStatusLine().getStatusCode());
      newCookie = sessionCookie(response);
    }

    assertNotEquals(oldCookie, newCookie);
    oldSession.getRemote().sendString(new Message(OP.PING).toJson());
    assertEquals(1008, oldSocket.awaitCloseCode());
    TestSocket newSocket = new TestSocket();
    Session newSession = connect(newSocket, newCookie, validOrigin())
        .get(10, TimeUnit.SECONDS);
    newSession.getRemote().sendString(new Message(OP.LIST_NOTES).toJson());
    newSocket.awaitMessage(OP.NOTES_INFO);
    newSession.close();
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

  private String login(String apiPath, String userName, String password) throws Exception {
    try (CloseableHttpClient client = isolatedHttpClient();
         CloseableHttpResponse response = client.execute(
             loginRequest(apiPath, userName, password))) {
      assertEquals(200, response.getStatusLine().getStatusCode());
      return sessionCookie(response);
    }
  }

  private HttpPost loginRequest(String apiPath, String userName, String password) {
    HttpPost login = new HttpPost(
        "http://localhost:" + zConf.getServerPort() + apiPath + "/login");
    login.setHeader("Origin", validOrigin());
    login.setEntity(new UrlEncodedFormEntity(List.of(
        new BasicNameValuePair("userName", userName),
        new BasicNameValuePair("password", password)), StandardCharsets.UTF_8));
    return login;
  }

  private static String sessionCookie(CloseableHttpResponse response) {
    Pattern pattern = Pattern.compile("JSESSIONID=([a-zA-Z0-9-]+)");
    String sessionCookie = null;
    for (Header header : response.getHeaders("Set-Cookie")) {
      Matcher matcher = pattern.matcher(header.getValue());
      if (matcher.find() && !header.getValue().contains("Max-Age=0")) {
        sessionCookie = matcher.group(1);
      }
    }
    if (sessionCookie == null) {
      throw new AssertionError("Login did not issue a JSESSIONID cookie");
    }
    return sessionCookie;
  }

  @SuppressWarnings("unchecked")
  private static String noteId(Message response) {
    return String.valueOf(((Map<String, Object>) response.get("note")).get("id"));
  }

  private static CloseableHttpClient isolatedHttpClient() {
    return HttpClients.custom()
        .disableCookieManagement()
        .disableRedirectHandling()
        .build();
  }

  private URI websocketUri() {
    return URI.create("ws://localhost:" + zConf.getServerPort() + "/ws");
  }

  private String validOrigin() {
    return "http://localhost:" + zConf.getServerPort();
  }

  private static void allowServerOrigin(MiniZeppelinServer server) {
    server.getZeppelinConfiguration().setProperty(
        org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars.ZEPPELIN_ALLOWED_ORIGINS
            .getVarName(),
        "http://localhost:" + server.getZeppelinConfiguration().getServerPort());
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
      while (true) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          break;
        }
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
