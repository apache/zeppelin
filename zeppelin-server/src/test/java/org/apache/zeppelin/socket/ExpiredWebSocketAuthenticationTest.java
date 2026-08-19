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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.common.Message;
import org.apache.zeppelin.common.Message.OP;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExpiredWebSocketAuthenticationTest extends AbstractTestRestApi {

  private static final long SESSION_TIMEOUT_MILLIS = 1000;

  private static MiniZeppelinServer zepServer;
  private static WebSocketClient webSocketClient;

  @BeforeAll
  static void startServer() throws Exception {
    zepServer = new MiniZeppelinServer(
        ExpiredWebSocketAuthenticationTest.class.getSimpleName());
    zepServer.addConfigFile(
        "shiro.ini", ZEPPELIN_SHIRO.replace("86400000", "1000"));
    zepServer.start();
    zepServer.getZeppelinConfiguration().setProperty(
        org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars.ZEPPELIN_ALLOWED_ORIGINS
            .getVarName(),
        "http://localhost:" + zepServer.getZeppelinConfiguration().getServerPort());
    webSocketClient = new WebSocketClient();
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
  void expiredSessionClosesBeforeInboundDispatch() throws Exception {
    String cookie = login();
    TestSocket socket = new TestSocket();
    Session session = connect(socket, cookie).get(10, TimeUnit.SECONDS);

    awaitSessionExpiration();
    session.getRemote().sendString(new Message(OP.LIST_NOTES).toJson());

    assertEquals(1008, socket.awaitCloseCode());
  }

  @Test
  void expiredSessionClosesBeforeOutboundDelivery() throws Exception {
    String cookie = login();
    TestSocket socket = new TestSocket();
    connect(socket, cookie).get(10, TimeUnit.SECONDS);

    awaitSessionExpiration();
    zepServer.getService(ConnectionManager.class).broadcast(new Message(OP.NOTES_INFO));

    assertEquals(1008, socket.awaitCloseCode());
  }

  private String login() throws Exception {
    HttpPost login = new HttpPost(
        "http://localhost:" + zConf.getServerPort() + "/api/login");
    login.setHeader("Origin", validOrigin());
    login.setEntity(new UrlEncodedFormEntity(List.of(
        new BasicNameValuePair("userName", "user1"),
        new BasicNameValuePair("password", "password2")), StandardCharsets.UTF_8));
    try (CloseableHttpClient client = HttpClients.custom()
             .disableCookieManagement()
             .disableRedirectHandling()
             .build();
         CloseableHttpResponse response = client.execute(login)) {
      assertEquals(200, response.getStatusLine().getStatusCode());
      return sessionCookie(response);
    }
  }

  private CompletableFuture<Session> connect(TestSocket socket, String sessionCookie)
      throws Exception {
    ClientUpgradeRequest request = new ClientUpgradeRequest();
    request.setHeader("Origin", validOrigin());
    request.setHeader("Cookie", "JSESSIONID=" + sessionCookie);
    return webSocketClient.connect(socket, websocketUri(), request);
  }

  private static String sessionCookie(CloseableHttpResponse response) {
    Pattern pattern = Pattern.compile("JSESSIONID=([a-zA-Z0-9-]+)");
    for (Header header : response.getHeaders("Set-Cookie")) {
      Matcher matcher = pattern.matcher(header.getValue());
      if (matcher.find() && !header.getValue().contains("Max-Age=0")) {
        return matcher.group(1);
      }
    }
    throw new AssertionError("Login did not issue a JSESSIONID cookie");
  }

  private static void awaitSessionExpiration() throws InterruptedException {
    Thread.sleep(SESSION_TIMEOUT_MILLIS + 300);
  }

  private URI websocketUri() {
    return URI.create("ws://localhost:" + zConf.getServerPort() + "/ws");
  }

  private String validOrigin() {
    return "http://localhost:" + zConf.getServerPort();
  }

  private static final class TestSocket extends WebSocketAdapter {
    private final CompletableFuture<Integer> closeCode = new CompletableFuture<>();

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
      closeCode.complete(statusCode);
      super.onWebSocketClose(statusCode, reason);
    }

    int awaitCloseCode() throws Exception {
      return closeCode.get(10, TimeUnit.SECONDS);
    }
  }
}
