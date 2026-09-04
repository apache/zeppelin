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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.lang.util.LifecycleUtils;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.apache.zeppelin.service.AuthenticatedSessionService.SessionAuthenticationException;
import org.junit.jupiter.api.Test;

class AuthenticatedSessionServiceTest {

  @Test
  void identityIsImmutableAndNoAuthenticationUsesTheSharedAnonymousIdentity() {
    Set<String> roles = new HashSet<>();
    roles.add("reader");
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user", roles, true, "session-id");

    roles.add("admin");

    assertEquals(Set.of("reader"), identity.getRoles());
    assertThrows(UnsupportedOperationException.class, () -> identity.getRoles().add("writer"));
    assertTrue(identity.isAuthenticated());
    assertEquals("session-id", identity.getSessionId().orElseThrow());

    AuthenticatedIdentity anonymous = new NoAuthenticationService().getAuthenticatedIdentity();
    assertSame(AuthenticatedIdentity.anonymous(), anonymous);
    assertFalse(anonymous.isAuthenticated());
    assertTrue(anonymous.getSessionId().isEmpty());
  }

  @Test
  void anonymousIdentityNeedsNoSessionOrAuthenticationManager() {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedSessionService service =
        new AuthenticatedSessionService(() -> authenticationService);

    assertSame(
        AuthenticatedIdentity.anonymous(),
        service.refresh(AuthenticatedIdentity.anonymous(), null, true));
    service.validate(AuthenticatedIdentity.anonymous(), null);

    verify(authenticationService, never()).getAuthenticatedIdentity();
    verify(authenticationService, never()).getPrincipal();
  }

  @Test
  void refreshesIdentityAndTouchesRealClientOperations() throws Exception {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedIdentity captured = identity("user1", "role1", "session-id");
    AuthenticatedIdentity refreshed = identity("user1", "role2", "session-id");
    when(authenticationService.getAuthenticatedIdentity()).thenReturn(refreshed);
    Session session = session("session-id");
    Subject subject = authenticatedSubject(session);
    AuthenticatedSessionService service =
        spy(new AuthenticatedSessionService(() -> authenticationService));
    SecurityManager securityManager = mock(SecurityManager.class);
    doReturn(subject).when(service).restoreSubject("session-id", securityManager);

    assertSame(refreshed, service.refresh(captured, securityManager, true));

    verify(session).touch();
  }

  @Test
  void pingRefreshDoesNotExtendIdleTimeout() throws Exception {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedIdentity identity = identity("user1", "role1", "session-id");
    when(authenticationService.getAuthenticatedIdentity()).thenReturn(identity);
    Session session = session("session-id");
    Subject subject = authenticatedSubject(session);
    AuthenticatedSessionService service =
        spy(new AuthenticatedSessionService(() -> authenticationService));
    SecurityManager securityManager = mock(SecurityManager.class);
    doReturn(subject).when(service).restoreSubject("session-id", securityManager);

    assertSame(identity, service.refresh(identity, securityManager, false));

    verify(session, never()).touch();
  }

  @Test
  void validateChecksPrincipalWithoutResolvingRolesOrTouchingSession() throws Exception {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedIdentity identity = identity("user1", "role1", "session-id");
    when(authenticationService.getPrincipal()).thenReturn("user1");
    Session session = session("session-id");
    Subject subject = authenticatedSubject(session);
    AuthenticatedSessionService service =
        spy(new AuthenticatedSessionService(() -> authenticationService));
    SecurityManager securityManager = mock(SecurityManager.class);
    doReturn(subject).when(service).restoreSubject("session-id", securityManager);

    service.validate(identity, securityManager);

    verify(authenticationService).getPrincipal();
    verify(authenticationService, never()).getAuthenticatedIdentity();
    verify(session, never()).touch();
  }

  @Test
  void resolvesAuthenticationServiceInCapturedContextAndRestoresCallerContext() {
    Map<Object, Object> previousResources = ThreadContext.getResources();
    ThreadContext.remove();

    SimpleAccountRealm realm = new SimpleAccountRealm();
    realm.addAccount("user1", "password");
    DefaultSecurityManager capturedManager = new DefaultSecurityManager(realm);
    DefaultSecurityManager callerManager = new DefaultSecurityManager();
    try {
      Subject loginSubject = new Subject.Builder(capturedManager).buildSubject();
      loginSubject.getSession();
      loginSubject.login(new UsernamePasswordToken("user1", "password"));
      Serializable sessionId = loginSubject.getSession(false).getId();

      AuthenticationService authenticationService = mock(AuthenticationService.class);
      when(authenticationService.getPrincipal()).thenReturn("user1");
      AtomicReference<Subject> restoredSubject = new AtomicReference<>();
      AtomicReference<Subject> providerSubject = new AtomicReference<>();
      AtomicReference<SecurityManager> providerManager = new AtomicReference<>();
      AuthenticatedSessionService service =
          new AuthenticatedSessionService(() -> {
            providerSubject.set(ThreadContext.getSubject());
            providerManager.set(ThreadContext.getSecurityManager());
            return authenticationService;
          }) {
            @Override
            Subject restoreSubject(
                Serializable requestedSessionId, SecurityManager securityManager) {
              Subject subject = super.restoreSubject(requestedSessionId, securityManager);
              restoredSubject.set(subject);
              return subject;
            }
          };

      Subject callerSubject = new Subject.Builder(callerManager).buildSubject();
      Object callerResource = new Object();
      ThreadContext.bind(callerManager);
      ThreadContext.bind(callerSubject);
      ThreadContext.put("caller-resource", callerResource);

      service.validate(
          new AuthenticatedIdentity("user1", Set.of("role1"), true, sessionId),
          capturedManager);

      assertSame(capturedManager, providerManager.get());
      assertSame(restoredSubject.get(), providerSubject.get());
      assertEquals(sessionId, providerSubject.get().getSession(false).getId());
      assertSame(callerManager, ThreadContext.getSecurityManager());
      assertSame(callerSubject, ThreadContext.getSubject());
      assertSame(callerResource, ThreadContext.get("caller-resource"));
    } finally {
      ThreadContext.remove();
      LifecycleUtils.destroy(callerManager);
      LifecycleUtils.destroy(capturedManager);
      if (!previousResources.isEmpty()) {
        ThreadContext.setResources(previousResources);
      }
    }
  }

  @Test
  void rejectsChangedPrincipal() throws Exception {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedIdentity captured = identity("user1", "role1", "session-id");
    when(authenticationService.getAuthenticatedIdentity())
        .thenReturn(identity("user2", "role1", "session-id"));
    Subject subject = authenticatedSubject(session("session-id"));
    AuthenticatedSessionService service =
        spy(new AuthenticatedSessionService(() -> authenticationService));
    SecurityManager securityManager = mock(SecurityManager.class);
    doReturn(subject).when(service).restoreSubject("session-id", securityManager);

    assertThrows(
        SessionAuthenticationException.class,
        () -> service.refresh(captured, securityManager, true));
  }

  @Test
  void rejectsMissingMismatchedOrUnauthenticatedSession() throws Exception {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedIdentity captured = identity("user1", "role1", "session-id");
    Subject subject = mock(Subject.class);
    when(subject.getSession(false)).thenReturn(null);
    AuthenticatedSessionService service =
        spy(new AuthenticatedSessionService(() -> authenticationService));
    SecurityManager securityManager = mock(SecurityManager.class);
    doReturn(subject).when(service).restoreSubject("session-id", securityManager);

    assertThrows(
        SessionAuthenticationException.class,
        () -> service.refresh(captured, securityManager, false));

    Subject mismatchedSubject = authenticatedSubject(session("another-session"));
    doReturn(mismatchedSubject).when(service).restoreSubject("session-id", securityManager);
    assertThrows(
        SessionAuthenticationException.class,
        () -> service.refresh(captured, securityManager, false));

    Subject unauthenticatedSubject = mock(Subject.class);
    Session exactSession = session("session-id");
    when(unauthenticatedSubject.getSession(false)).thenReturn(exactSession);
    doReturn(unauthenticatedSubject)
        .when(service).restoreSubject("session-id", securityManager);
    assertThrows(
        SessionAuthenticationException.class,
        () -> service.refresh(captured, securityManager, false));
  }

  @Test
  void authenticatedIdentityRequiresTheExactAuthenticationManager() {
    AuthenticatedSessionService service =
        new AuthenticatedSessionService(() -> mock(AuthenticationService.class));

    assertThrows(
        SessionAuthenticationException.class,
        () -> service.validate(identity("user1", "role1", "session-id"), null));
  }

  private static AuthenticatedIdentity identity(
      String principal, String role, String sessionId) {
    return new AuthenticatedIdentity(principal, Set.of(role), true, sessionId);
  }

  private static Session session(String id) {
    Session session = mock(Session.class);
    when(session.getId()).thenReturn(id);
    return session;
  }

  private static Subject authenticatedSubject(Session session) throws Exception {
    Subject subject = mock(Subject.class);
    when(subject.getSession(false)).thenReturn(session);
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.execute(org.mockito.ArgumentMatchers.<Callable<?>>any()))
        .thenAnswer(invocation -> ((Callable<?>) invocation.getArgument(0)).call());
    return subject;
  }
}
