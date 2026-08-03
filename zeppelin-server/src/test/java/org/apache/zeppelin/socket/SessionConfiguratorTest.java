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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.util.ThreadContext;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.service.AuthenticatedIdentity;
import org.apache.zeppelin.service.AuthenticationService;
import org.apache.zeppelin.util.WatcherSecurityKey;
import org.apache.zeppelin.utils.CorsUtils;
import org.glassfish.hk2.api.ServiceLocator;
import org.junit.jupiter.api.Test;

class SessionConfiguratorTest {

  @Test
  void rejectsInvalidOriginsBeforeTheEndpointIsOpened() {
    ZeppelinConfiguration zConf = mock(ZeppelinConfiguration.class);
    when(zConf.getAllowedOrigins()).thenReturn(List.of("https://trusted.example"));
    ServiceLocator serviceLocator = serviceLocator(zConf, mock(AuthenticationService.class));
    SessionConfigurator configurator = new SessionConfigurator(serviceLocator);

    assertTrue(configurator.checkOrigin("https://trusted.example"));
    assertFalse(configurator.checkOrigin("https://evil.example"));
    assertFalse(configurator.checkOrigin("not a uri"));
  }

  @Test
  void defaultLocalOriginMustMatchTheServerSchemeAndPort() {
    ZeppelinConfiguration zConf = mock(ZeppelinConfiguration.class);
    when(zConf.getAllowedOrigins()).thenReturn(List.of());
    when(zConf.getServerPort()).thenReturn(8080);
    ServiceLocator serviceLocator = serviceLocator(zConf, mock(AuthenticationService.class));
    SessionConfigurator configurator = new SessionConfigurator(serviceLocator);

    assertTrue(configurator.checkOrigin("http://localhost:8080"));
    assertFalse(configurator.checkOrigin("http://localhost:8081"));
    assertFalse(configurator.checkOrigin("https://localhost:8080"));
  }

  @Test
  void capturesTheServerAuthenticatedIdentityInPerHandshakeProperties() {
    ZeppelinConfiguration zConf = mock(ZeppelinConfiguration.class);
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user1", java.util.Set.of("role1"), true, "session-id");
    when(authenticationService.getAuthenticatedIdentity()).thenReturn(identity);
    SessionConfigurator configurator =
        new SessionConfigurator(serviceLocator(zConf, authenticationService));
    ServerEndpointConfig endpointConfig = ServerEndpointConfig.Builder
        .create(NotebookServer.class, "/ws")
        .build();
    HandshakeRequest request = mock(HandshakeRequest.class);
    when(request.getHeaders()).thenReturn(Map.of(
        WatcherSecurityKey.HTTP_HEADER, List.of("watcher-key"),
        CorsUtils.HEADER_ORIGIN, List.of("https://trusted.example")));
    SecurityManager securityManager = mock(SecurityManager.class);

    ThreadContext.bind(securityManager);
    try {
      configurator.modifyHandshake(endpointConfig, request, mock(HandshakeResponse.class));
    } finally {
      ThreadContext.unbindSecurityManager();
    }

    assertSame(identity,
        endpointConfig.getUserProperties().get(SessionConfigurator.AUTHENTICATED_IDENTITY));
    assertSame(securityManager,
        endpointConfig.getUserProperties().get(
            SessionConfigurator.AUTHENTICATION_SECURITY_MANAGER));
  }

  private static ServiceLocator serviceLocator(
      ZeppelinConfiguration zConf, AuthenticationService authenticationService) {
    ServiceLocator serviceLocator = mock(ServiceLocator.class);
    when(serviceLocator.getService(ZeppelinConfiguration.class)).thenReturn(zConf);
    when(serviceLocator.getService(AuthenticationService.class)).thenReturn(authenticationService);
    return serviceLocator;
  }
}
