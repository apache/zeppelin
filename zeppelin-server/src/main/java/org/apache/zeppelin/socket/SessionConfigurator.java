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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.service.AuthenticatedIdentity;
import org.apache.zeppelin.service.AuthenticationService;
import org.apache.shiro.util.ThreadContext;
import org.apache.zeppelin.util.WatcherSecurityKey;
import org.apache.zeppelin.utils.CorsUtils;
import org.glassfish.hk2.api.ServiceLocator;

/**
 * This class set headers to websocket sessions and inject hk2 when initiating instances by ServerEndpoint annotation.
 */
public class SessionConfigurator extends Configurator {

  public static final String AUTHENTICATED_IDENTITY =
      SessionConfigurator.class.getName() + ".authenticatedIdentity";
  public static final String AUTHENTICATION_SECURITY_MANAGER =
      SessionConfigurator.class.getName() + ".authenticationSecurityManager";

  private final ServiceLocator serviceLocator;
  private final ZeppelinConfiguration zConf;
  private final AuthenticationService authenticationService;

  public SessionConfigurator(ServiceLocator serviceLocator) {
    this.serviceLocator = serviceLocator;
    this.zConf = serviceLocator.getService(ZeppelinConfiguration.class);
    this.authenticationService = serviceLocator.getService(AuthenticationService.class);
  }

  @Override
  public boolean checkOrigin(String originHeaderValue) {
    try {
      return CorsUtils.isValidOrigin(originHeaderValue, zConf);
    } catch (UnknownHostException | URISyntaxException e) {
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
    holder = request.getHeaders().get(CorsUtils.HEADER_ORIGIN);
    sec.getUserProperties().put(CorsUtils.HEADER_ORIGIN,
        null != holder && !holder.isEmpty() ? holder.get(0) : null);
    AuthenticatedIdentity identity = authenticationService.getAuthenticatedIdentity();
    sec.getUserProperties().put(AUTHENTICATED_IDENTITY, identity);
    sec.getUserProperties().put(
        AUTHENTICATION_SECURITY_MANAGER, ThreadContext.getSecurityManager());
  }

  @Override
  public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
    return serviceLocator.getService(endpointClass);
  }
}
