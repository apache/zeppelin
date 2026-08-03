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
import java.time.Clock;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import jakarta.inject.Inject;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;

/**
 * Revalidates the Shiro session captured when a long-lived transport was established.
 *
 * <p>The transport retains only an opaque session handle, never a thread-bound {@link Subject}.
 * This lets REST and WebSocket requests use the same Shiro session lifecycle while ensuring that
 * logout and expiry are observed before a WebSocket operation is dispatched.
 */
public class AuthenticatedSessionService {

  private static final String ROLE_SNAPSHOT_SESSION_ATTRIBUTE =
      AuthenticatedSessionService.class.getName() + ".roleSnapshot";

  private final AuthenticationService authenticationService;
  private final Clock clock;

  @Inject
  public AuthenticatedSessionService(AuthenticationService authenticationService) {
    this(authenticationService, Clock.systemUTC());
  }

  AuthenticatedSessionService(AuthenticationService authenticationService, Clock clock) {
    this.authenticationService = authenticationService;
    this.clock = clock;
  }

  /** Validate that a transport's captured session is still authenticated without touching it. */
  public void validate(
      AuthenticatedIdentity connectionIdentity, SecurityManager securityManager) {
    try {
      restoreValidatedSubject(connectionIdentity, securityManager);
    } catch (SessionAuthenticationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new SessionAuthenticationException("Authenticated session is no longer valid", e);
    }
  }

  /**
   * Revalidate a transport identity and return a fresh principal/role snapshot.
   *
   * @param connectionIdentity identity captured at the transport handshake
   * @param securityManager exact Shiro security manager that authenticated the handshake
   * @param touchSession whether this operation should extend the Shiro session idle timeout
   * @return a fresh server-authenticated identity
   * @throws SessionAuthenticationException when the session is missing, expired or changed
   */
  public AuthenticatedIdentity refresh(
      AuthenticatedIdentity connectionIdentity,
      SecurityManager securityManager,
      boolean touchSession) {
    return refresh(connectionIdentity, securityManager, touchSession, 0);
  }

  /**
   * Revalidate a transport identity while allowing a recent role snapshot to be reused.
   *
   * <p>The Shiro session and principal are validated on every call. Only the role set may be
   * reused, which bounds role-revocation latency without delaying logout or session-expiry
   * detection. A non-positive {@code maxRoleAgeMillis} disables reuse and has the same strict
   * semantics as the three-argument overload.
   *
   * @param connectionIdentity identity captured at the transport handshake
   * @param securityManager exact Shiro security manager that authenticated the handshake
   * @param touchSession whether this operation should extend the Shiro session idle timeout
   * @param maxRoleAgeMillis maximum age of a reusable role snapshot, in milliseconds
   * @return a server-authenticated identity with current or recently refreshed roles
   * @throws SessionAuthenticationException when the session is missing, expired or changed
   */
  public AuthenticatedIdentity refresh(
      AuthenticatedIdentity connectionIdentity,
      SecurityManager securityManager,
      boolean touchSession,
      long maxRoleAgeMillis) {
    Objects.requireNonNull(connectionIdentity, "connectionIdentity");

    try {
      Subject subject = restoreValidatedSubject(connectionIdentity, securityManager);
      if (subject == null) {
        return AuthenticatedIdentity.anonymous();
      }
      Session session = subject.getSession(false);
      if (touchSession) {
        session.touch();
      }

      long roleLookupStartedAtMillis = clock.millis();
      if (maxRoleAgeMillis > 0) {
        Object cached = session.getAttribute(ROLE_SNAPSHOT_SESSION_ATTRIBUTE);
        if (cached instanceof RoleSnapshot) {
          RoleSnapshot roleSnapshot = (RoleSnapshot) cached;
          if (roleSnapshot.isReusableFor(
              connectionIdentity, roleLookupStartedAtMillis, maxRoleAgeMillis)) {
            return roleSnapshot.toIdentity();
          }
        }
      }

      AuthenticatedIdentity refreshed =
          subject.execute(authenticationService::getAuthenticatedIdentity);
      Serializable sessionId = connectionIdentity.getSessionId().orElseThrow(
          () -> new SessionAuthenticationException("Authenticated session is unavailable"));
      if (!refreshed.isAuthenticated()
          || !connectionIdentity.getPrincipal().equals(refreshed.getPrincipal())
          || !refreshed.getSessionId().filter(sessionId::equals).isPresent()) {
        throw new SessionAuthenticationException("Authenticated session identity changed");
      }
      session.setAttribute(
          ROLE_SNAPSHOT_SESSION_ATTRIBUTE,
          new RoleSnapshot(refreshed, roleLookupStartedAtMillis));
      return refreshed;
    } catch (SessionAuthenticationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new SessionAuthenticationException("Authenticated session is no longer valid", e);
    }
  }

  private Subject restoreValidatedSubject(
      AuthenticatedIdentity connectionIdentity, SecurityManager securityManager) {
    Objects.requireNonNull(connectionIdentity, "connectionIdentity");
    if (!connectionIdentity.isAuthenticated()
        && AuthenticatedIdentity.ANONYMOUS_PRINCIPAL.equals(
            connectionIdentity.getPrincipal())
        && connectionIdentity.getSessionId().isEmpty()) {
      // Reaching the endpoint means Shiro's configured `/ws` chain admitted this anonymous
      // handshake, or authentication is disabled entirely.
      return null;
    }

    Serializable sessionId = connectionIdentity.getSessionId().orElseThrow(
        () -> new SessionAuthenticationException("Authenticated session is unavailable"));
    if (!connectionIdentity.isAuthenticated()) {
      throw new SessionAuthenticationException("Authenticated session is unavailable");
    }
    if (securityManager == null) {
      throw new SessionAuthenticationException("Authentication manager is unavailable");
    }

    Subject subject = restoreSubject(sessionId, securityManager);
    Session session = subject.getSession(false);
    if (session == null || !subject.isAuthenticated() || !sessionId.equals(session.getId())) {
      throw new SessionAuthenticationException("Authenticated session is no longer valid");
    }
    String principal = subject.execute(authenticationService::getPrincipal);
    if (!connectionIdentity.getPrincipal().equals(principal)) {
      throw new SessionAuthenticationException("Authenticated session identity changed");
    }
    return subject;
  }

  Subject restoreSubject(Serializable sessionId, SecurityManager securityManager) {
    return new Subject.Builder(securityManager)
        .sessionId(sessionId)
        .sessionCreationEnabled(false)
        .buildSubject();
  }

  private static final class RoleSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String principal;
    private final Set<String> roles;
    private final Serializable sessionId;
    private final long capturedAtMillis;

    private RoleSnapshot(AuthenticatedIdentity identity, long capturedAtMillis) {
      this.principal = identity.getPrincipal();
      this.roles = Collections.unmodifiableSet(new HashSet<>(identity.getRoles()));
      this.sessionId = identity.getSessionId().orElse(null);
      this.capturedAtMillis = capturedAtMillis;
    }

    private boolean isReusableFor(
        AuthenticatedIdentity connectionIdentity, long nowMillis, long maxRoleAgeMillis) {
      long ageMillis = nowMillis - capturedAtMillis;
      return ageMillis >= 0
          && ageMillis < maxRoleAgeMillis
          && principal.equals(connectionIdentity.getPrincipal())
          && connectionIdentity.getSessionId().filter(sessionId::equals).isPresent();
    }

    private AuthenticatedIdentity toIdentity() {
      return new AuthenticatedIdentity(principal, roles, true, sessionId);
    }
  }
}
