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
package org.apache.zeppelin.service;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.zeppelin.user.AuthenticationInfo;

/** Immutable identity captured from the server-side authentication layer. */
public final class AuthenticatedIdentity {

  public static final String ANONYMOUS_PRINCIPAL = "anonymous";

  private static final AuthenticatedIdentity ANONYMOUS =
      new AuthenticatedIdentity(ANONYMOUS_PRINCIPAL, Collections.emptySet(), false, null);

  private final String principal;
  private final Set<String> roles;
  private final boolean authenticated;
  private final Serializable sessionId;

  public AuthenticatedIdentity(
      String principal, Set<String> roles, boolean authenticated, Serializable sessionId) {
    this.principal = Objects.requireNonNull(principal, "principal");
    this.roles = Collections.unmodifiableSet(new HashSet<>(Objects.requireNonNull(roles, "roles")));
    this.authenticated = authenticated;
    this.sessionId = sessionId;
  }

  public static AuthenticatedIdentity anonymous() {
    return ANONYMOUS;
  }

  public String getPrincipal() {
    return principal;
  }

  public Set<String> getRoles() {
    return roles;
  }

  public boolean isAuthenticated() {
    return authenticated;
  }

  /** Return the opaque server-side session handle, if authentication created one. */
  public Optional<Serializable> getSessionId() {
    return Optional.ofNullable(sessionId);
  }

  /** Build the authorization context consumed by REST and WebSocket services. */
  public ServiceContext toServiceContext() {
    Set<String> copiedRoles = new HashSet<>(roles);
    AuthenticationInfo authenticationInfo =
        new AuthenticationInfo(principal, copiedRoles, null);
    Set<String> userAndRoles = new HashSet<>(copiedRoles);
    userAndRoles.add(principal);
    return new ServiceContext(authenticationInfo, userAndRoles);
  }
}
