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
package org.apache.zeppelin.realm;

import java.util.Map;

import jakarta.ws.rs.core.Cookie;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;

/**
 * Contract used by the server login endpoint to interact with optional SSO realms without
 * depending on their implementation classes.
 */
public interface ExternalLoginRealm {

  AuthenticationToken getLoginAuthenticationToken(Map<String, Cookie> cookies)
      throws AuthenticationException;

  String getLoginPrincipal(AuthenticationToken token) throws AuthenticationException;

  boolean shouldRedirectOnMissingToken();

  int getLoginPriority();

  String getProviderUrl();

  String getRedirectParam();

  String getLogin();

  String getLogout();

  Boolean getLogoutAPI();
}
