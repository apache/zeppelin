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
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;

/** Revalidates the Shiro session captured when a long-lived transport was established. */
public class AuthenticatedSessionService {

  private final Provider<AuthenticationService> authenticationServiceProvider;

  @Inject
  public AuthenticatedSessionService(
      Provider<AuthenticationService> authenticationServiceProvider) {
    this.authenticationServiceProvider = authenticationServiceProvider;
  }

  /** Validate a captured session without extending its idle timeout or resolving roles. */
  public void validate(
      AuthenticatedIdentity connectionIdentity, SecurityManager securityManager) {
    try {
      Subject subject = restoreValidatedSubject(connectionIdentity, securityManager);
      if (subject == null) {
        return;
      }
      String principal = subject.execute(
          () -> authenticationServiceProvider.get().getPrincipal());
      if (!connectionIdentity.getPrincipal().equals(principal)) {
        throw new SessionAuthenticationException("Authenticated session identity changed");
      }
    } catch (SessionAuthenticationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new SessionAuthenticationException("Authenticated session is no longer valid", e);
    }
  }

  /**
   * Revalidate a captured session and return a fresh server-authenticated identity.
   *
   * @param touchSession whether this client operation should extend the session idle timeout
   */
  public AuthenticatedIdentity refresh(
      AuthenticatedIdentity connectionIdentity,
      SecurityManager securityManager,
      boolean touchSession) {
    try {
      Subject subject = restoreValidatedSubject(connectionIdentity, securityManager);
      if (subject == null) {
        return AuthenticatedIdentity.anonymous();
      }

      Session session = subject.getSession(false);
      if (touchSession) {
        session.touch();
      }
      AuthenticatedIdentity refreshed =
          subject.execute(
              () -> authenticationServiceProvider.get().getAuthenticatedIdentity());
      Serializable sessionId = connectionIdentity.getSessionId().orElseThrow(
          () -> new SessionAuthenticationException("Authenticated session is unavailable"));
      if (!refreshed.isAuthenticated()
          || !connectionIdentity.getPrincipal().equals(refreshed.getPrincipal())
          || refreshed.getSessionId().filter(sessionId::equals).isEmpty()) {
        throw new SessionAuthenticationException("Authenticated session identity changed");
      }
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
      return null;
    }

    Serializable sessionId = connectionIdentity.getSessionId().orElseThrow(
        () -> new SessionAuthenticationException("Authenticated session is unavailable"));
    if (!connectionIdentity.isAuthenticated() || securityManager == null) {
      throw new SessionAuthenticationException("Authenticated session is unavailable");
    }

    Subject subject = restoreSubject(sessionId, securityManager);
    Session session = subject.getSession(false);
    if (session == null || !subject.isAuthenticated() || !sessionId.equals(session.getId())) {
      throw new SessionAuthenticationException("Authenticated session is no longer valid");
    }
    return subject;
  }

  Subject restoreSubject(Serializable sessionId, SecurityManager securityManager) {
    return new Subject.Builder(securityManager)
        .sessionId(sessionId)
        .sessionCreationEnabled(false)
        .buildSubject();
  }

  /** Indicates that a transport's server-authenticated session is no longer valid. */
  public static final class SessionAuthenticationException extends RuntimeException {

    public SessionAuthenticationException(String message) {
      super(message);
    }

    public SessionAuthenticationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
