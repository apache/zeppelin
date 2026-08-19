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

import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import jakarta.websocket.server.ServerEndpointConfig.Configurator;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.util.ThreadContext;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.service.AuthenticatedIdentity;
import org.apache.zeppelin.service.AuthenticationService;
import org.apache.zeppelin.util.WatcherSecurityKey;
import org.apache.zeppelin.utils.CorsUtils;
import org.glassfish.hk2.api.ServiceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class set headers to websocket sessions and inject hk2 when initiating instances by ServerEndpoint annotation.
 */
public class SessionConfigurator extends Configurator {

  static final String AUTHENTICATED_IDENTITY = AuthenticatedIdentity.class.getName();
  static final String AUTHENTICATION_SECURITY_MANAGER = SecurityManager.class.getName();

  private static final Logger LOGGER = LoggerFactory.getLogger(SessionConfigurator.class);

  private final ServiceLocator serviceLocator;
  private final ZeppelinConfiguration zConf;

  public SessionConfigurator(
      ServiceLocator serviceLocator, ZeppelinConfiguration zConf) {
    this.serviceLocator = serviceLocator;
    this.zConf = zConf;
  }

  @Override
  public boolean checkOrigin(String originHeaderValue) {
    try {
      return CorsUtils.isValidOrigin(originHeaderValue, zConf);
    } catch (UnknownHostException | URISyntaxException e) {
      LOGGER.warn("Rejecting WebSocket handshake with invalid Origin: {}", originHeaderValue);
      return false;
    }
  }

  @Override
  public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request,
                              HandshakeResponse response) {
    List<String> holder;
    holder = request.getHeaders().get(WatcherSecurityKey.HTTP_HEADER);
    sec.getUserProperties().put(WatcherSecurityKey.HTTP_HEADER,
        null != holder && !holder.isEmpty() ? holder.get(0) : null);
    AuthenticationService authenticationService =
        serviceLocator.getService(AuthenticationService.class);
    sec.getUserProperties().put(
        AUTHENTICATED_IDENTITY, authenticationService.getAuthenticatedIdentity());
    SecurityManager securityManager = ThreadContext.getSecurityManager();
    if (securityManager != null) {
      sec.getUserProperties().put(AUTHENTICATION_SECURITY_MANAGER, securityManager);
    }
  }

  @Override
  public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
    return serviceLocator.getService(endpointClass);
  }
}
