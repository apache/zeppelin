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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AnonymousWebSocketAuthenticationTest {

  private static final String ANONYMOUS_WEBSOCKET_SHIRO =
      "[main]\n"
          + "sessionManager = org.apache.shiro.web.session.mgt.DefaultWebSessionManager\n"
          + "securityManager.sessionManager = $sessionManager\n"
          + "securityManager.rememberMeManager = null\n"
          + "[urls]\n"
          + "/api/version = anon\n"
          + "/ws = anon\n"
          + "/** = authc";

  private static MiniZeppelinServer zepServer;
  private static WebSocketClient webSocketClient;

  @BeforeAll
  static void startServer() throws Exception {
    zepServer = new MiniZeppelinServer(AnonymousWebSocketAuthenticationTest.class.getSimpleName());
    zepServer.addConfigFile("shiro.ini", ANONYMOUS_WEBSOCKET_SHIRO);
    zepServer.start();
    zepServer.getZeppelinConfiguration().setProperty(
        ConfVars.ZEPPELIN_ALLOWED_ORIGINS.getVarName(), validOrigin());
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

  @Test
  void explicitAnonymousRuleAllowsWebSocketWithoutRestSession() throws Exception {
    WebSocketAdapter socket = new WebSocketAdapter();
    ClientUpgradeRequest request = new ClientUpgradeRequest();
    request.setHeader("Origin", validOrigin());

    Session session = webSocketClient.connect(socket, websocketUri(), request)
        .get(10, TimeUnit.SECONDS);

    assertTrue(session.isOpen());
    assertTrue(socket.isConnected());
    session.close();
  }

  private static URI websocketUri() {
    return URI.create("ws://localhost:" + zepServer.getZeppelinConfiguration().getServerPort()
        + "/ws");
  }

  private static String validOrigin() {
    return "http://localhost:" + zepServer.getZeppelinConfiguration().getServerPort();
  }
}
