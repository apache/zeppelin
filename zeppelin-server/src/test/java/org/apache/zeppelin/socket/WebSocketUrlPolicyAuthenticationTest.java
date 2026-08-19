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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.zeppelin.MiniZeppelinServer;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.exceptions.UpgradeException;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.Test;

class WebSocketUrlPolicyAuthenticationTest {

  private static final String ANONYMOUS_WEBSOCKET_SHIRO =
      "[main]\n"
          + "sessionManager = org.apache.shiro.web.session.mgt.DefaultWebSessionManager\n"
          + "securityManager.sessionManager = $sessionManager\n"
          + "[urls]\n"
          + "/api/version = anon\n"
          + "/ws = anon\n"
          + "/** = authc";

  private static final String SHIRO_WITHOUT_CATCH_ALL =
      "[main]\n"
          + "sessionManager = org.apache.shiro.web.session.mgt.DefaultWebSessionManager\n"
          + "securityManager.sessionManager = $sessionManager\n"
          + "[urls]\n"
          + "/api/version = anon";

  @Test
  void defaultAnonymousPolicyAllowsClassicWebSocketFromLocalOrigin() throws Exception {
    withServer(null, (server, webSocketClient) -> {
      WebSocketAdapter socket = new WebSocketAdapter();
      ClientUpgradeRequest request = new ClientUpgradeRequest();
      request.setHeader("Origin", validOrigin(server));

      URI classicWebSocketUri = URI.create("ws://localhost:"
          + server.getZeppelinConfiguration().getServerPort() + "/classic/ws");
      Session session = webSocketClient.connect(socket, classicWebSocketUri, request)
          .get(10, TimeUnit.SECONDS);

      assertTrue(session.isOpen());
      assertTrue(socket.isConnected());
      session.close();
    });
  }

  @Test
  void explicitAnonymousRuleAllowsWebSocketWithoutRestSession() throws Exception {
    withServer(ANONYMOUS_WEBSOCKET_SHIRO, (server, webSocketClient) -> {
      WebSocketAdapter socket = new WebSocketAdapter();
      ClientUpgradeRequest request = new ClientUpgradeRequest();
      request.setHeader("Origin", validOrigin(server));

      Session session = webSocketClient.connect(socket, websocketUri(server), request)
          .get(10, TimeUnit.SECONDS);

      assertTrue(session.isOpen());
      assertTrue(socket.isConnected());
      session.close();
    });
  }

  @Test
  void unmatchedRestAndWebSocketRequestsFailClosed() throws Exception {
    withServer(SHIRO_WITHOUT_CATCH_ALL, (server, webSocketClient) -> {
      ClientUpgradeRequest request = new ClientUpgradeRequest();
      request.setHeader("Origin", validOrigin(server));

      ExecutionException failure = assertThrows(ExecutionException.class,
          () -> webSocketClient.connect(new WebSocketAdapter(), websocketUri(server), request)
              .get(10, TimeUnit.SECONDS));
      UpgradeException upgradeException = findUpgradeException(failure);
      assertNotNull(upgradeException);
      assertEquals(403, upgradeException.getResponseStatusCode());

      URL unmatchedRestUrl = URI.create(validOrigin(server) + "/api/unmatched").toURL();
      HttpURLConnection connection = (HttpURLConnection) unmatchedRestUrl.openConnection();
      connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("Origin", validOrigin(server));
      try {
        assertEquals(403, connection.getResponseCode());
      } finally {
        connection.disconnect();
      }
    });
  }

  private static void withServer(String shiroIni, ServerTest test) throws Exception {
    MiniZeppelinServer server =
        new MiniZeppelinServer(WebSocketUrlPolicyAuthenticationTest.class.getSimpleName());
    WebSocketClient webSocketClient = new WebSocketClient();
    try {
      if (shiroIni != null) {
        server.addConfigFile("shiro.ini", shiroIni);
      }
      server.start();
      webSocketClient.start();
      test.run(server, webSocketClient);
    } finally {
      try {
        webSocketClient.stop();
      } finally {
        server.destroy();
      }
    }
  }

  private static URI websocketUri(MiniZeppelinServer server) {
    return URI.create("ws://localhost:"
        + server.getZeppelinConfiguration().getServerPort() + "/ws");
  }

  private static String validOrigin(MiniZeppelinServer server) {
    return "http://localhost:" + server.getZeppelinConfiguration().getServerPort();
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

  @FunctionalInterface
  private interface ServerTest {
    void run(MiniZeppelinServer server, WebSocketClient client) throws Exception;
  }
}
