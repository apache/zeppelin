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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.Test;

class AuthenticatedSessionServiceTest {

  @Test
  void noAuthenticationAlwaysUsesAnonymousIdentity() {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedSessionService service =
        new AuthenticatedSessionService(authenticationService);

    AuthenticatedIdentity refreshed =
        service.refresh(AuthenticatedIdentity.anonymous(), null, true);

    assertSame(AuthenticatedIdentity.anonymous(), refreshed);
    verify(authenticationService, never()).getAuthenticatedIdentity();
  }

  @Test
  void explicitAnonymousShiroRuleRemainsAnonymous() {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedSessionService service =
        new AuthenticatedSessionService(authenticationService);

    AuthenticatedIdentity refreshed =
        service.refresh(AuthenticatedIdentity.anonymous(), null, true);

    assertSame(AuthenticatedIdentity.anonymous(), refreshed);
    verify(authenticationService, never()).getAuthenticatedIdentity();
  }

  @Test
  void refreshesIdentityFromTheCapturedSessionAndTouchesRealOperations() throws Exception {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedIdentity captured =
        new AuthenticatedIdentity("user1", Set.of("role1"), true, "session-id");
    AuthenticatedIdentity refreshed =
        new AuthenticatedIdentity("user1", Set.of("role2"), true, "session-id");
    when(authenticationService.getAuthenticatedIdentity()).thenReturn(refreshed);
    when(authenticationService.getPrincipal()).thenReturn("user1");

    Session session = mock(Session.class);
    when(session.getId()).thenReturn("session-id");
    Subject subject = mock(Subject.class);
    when(subject.getSession(false)).thenReturn(session);
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.execute(
        org.mockito.ArgumentMatchers.<Callable<AuthenticatedIdentity>>any()))
        .thenAnswer(
            invocation -> {
              Callable<?> callable = invocation.getArgument(0);
              return callable.call();
            });

    AuthenticatedSessionService service =
        spy(new AuthenticatedSessionService(authenticationService));
    SecurityManager securityManager = mock(SecurityManager.class);
    doReturn(subject).when(service).restoreSubject("session-id", securityManager);

    assertSame(refreshed, service.refresh(captured, securityManager, true));
    verify(session).touch();
  }

  @Test
  void pingValidationDoesNotExtendTheIdleTimeout() throws Exception {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user1", Set.of(), true, "session-id");
    when(authenticationService.getAuthenticatedIdentity()).thenReturn(identity);
    when(authenticationService.getPrincipal()).thenReturn("user1");

    Session session = mock(Session.class);
    when(session.getId()).thenReturn("session-id");
    Subject subject = mock(Subject.class);
    when(subject.getSession(false)).thenReturn(session);
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.execute(
        org.mockito.ArgumentMatchers.<Callable<AuthenticatedIdentity>>any()))
        .thenAnswer(
            invocation -> {
              Callable<?> callable = invocation.getArgument(0);
              return callable.call();
            });

    AuthenticatedSessionService service =
        spy(new AuthenticatedSessionService(authenticationService));
    SecurityManager securityManager = mock(SecurityManager.class);
    doReturn(subject).when(service).restoreSubject("session-id", securityManager);

    assertSame(identity, service.refresh(identity, securityManager, false));
    verify(session, never()).touch();
  }

  @Test
  void rejectsAChangedSessionIdentity() throws Exception {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedIdentity captured =
        new AuthenticatedIdentity("user1", Set.of(), true, "session-id");
    when(authenticationService.getAuthenticatedIdentity()).thenReturn(
        new AuthenticatedIdentity("user2", Set.of(), true, "session-id"));
    when(authenticationService.getPrincipal()).thenReturn("user1");

    Subject subject = mock(Subject.class);
    Session session = mock(Session.class);
    when(session.getId()).thenReturn("session-id");
    when(subject.getSession(false)).thenReturn(session);
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.execute(
        org.mockito.ArgumentMatchers.<Callable<AuthenticatedIdentity>>any()))
        .thenAnswer(
            invocation -> {
              Callable<?> callable = invocation.getArgument(0);
              return callable.call();
            });

    AuthenticatedSessionService service =
        spy(new AuthenticatedSessionService(authenticationService));
    SecurityManager securityManager = mock(SecurityManager.class);
    doReturn(subject).when(service).restoreSubject("session-id", securityManager);

    assertThrows(SessionAuthenticationException.class,
        () -> service.refresh(captured, securityManager, true));
  }

  @Test
  void rejectsAnExpiredOrMissingSession() {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedIdentity captured =
        new AuthenticatedIdentity("user1", Set.of(), true, "session-id");

    Subject subject = mock(Subject.class);
    when(subject.getSession(false)).thenReturn(null);
    AuthenticatedSessionService service =
        spy(new AuthenticatedSessionService(authenticationService));
    SecurityManager securityManager = mock(SecurityManager.class);
    doReturn(subject).when(service).restoreSubject("session-id", securityManager);

    assertThrows(SessionAuthenticationException.class,
        () -> service.refresh(captured, securityManager, false));
  }

  @Test
  void outboundRefreshReusesRolesOnlyWithinTheConfiguredAge() throws Exception {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedIdentity captured =
        new AuthenticatedIdentity("user1", Set.of("initial"), true, "session-id");
    AuthenticatedIdentity first =
        new AuthenticatedIdentity("user1", Set.of("role1"), true, "session-id");
    AuthenticatedIdentity second =
        new AuthenticatedIdentity("user1", Set.of("role2"), true, "session-id");
    when(authenticationService.getAuthenticatedIdentity()).thenReturn(first, second);
    when(authenticationService.getPrincipal()).thenReturn("user1");

    Session session = mock(Session.class);
    when(session.getId()).thenReturn("session-id");
    retainRoleSnapshot(session);
    Subject subject = authenticatedSubject(session);
    Clock clock = mock(Clock.class);
    when(clock.millis()).thenReturn(1_000L, 1_500L, 2_000L);
    AuthenticatedSessionService service =
        spy(new AuthenticatedSessionService(authenticationService, clock));
    SecurityManager securityManager = mock(SecurityManager.class);
    doReturn(subject).when(service).restoreSubject("session-id", securityManager);

    assertSame(first, service.refresh(captured, securityManager, false));
    assertEquals(
        Set.of("role1"),
        service.refresh(captured, securityManager, false, 1_000L).getRoles());
    assertSame(second, service.refresh(captured, securityManager, false, 1_000L));

    verify(authenticationService, times(2)).getAuthenticatedIdentity();
    verify(service, times(3)).restoreSubject("session-id", securityManager);
    verify(session, never()).touch();
  }

  @Test
  void strictRefreshAndZeroMaxAgeNeverReuseRoles() throws Exception {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedIdentity captured =
        new AuthenticatedIdentity("user1", Set.of(), true, "session-id");
    AuthenticatedIdentity first =
        new AuthenticatedIdentity("user1", Set.of("role1"), true, "session-id");
    AuthenticatedIdentity second =
        new AuthenticatedIdentity("user1", Set.of("role2"), true, "session-id");
    when(authenticationService.getAuthenticatedIdentity()).thenReturn(first, second);
    when(authenticationService.getPrincipal()).thenReturn("user1");

    Session session = mock(Session.class);
    when(session.getId()).thenReturn("session-id");
    retainRoleSnapshot(session);
    Subject subject = authenticatedSubject(session);
    Clock clock = mock(Clock.class);
    when(clock.millis()).thenReturn(1_000L, 1_001L);
    AuthenticatedSessionService service =
        spy(new AuthenticatedSessionService(authenticationService, clock));
    SecurityManager securityManager = mock(SecurityManager.class);
    doReturn(subject).when(service).restoreSubject("session-id", securityManager);

    assertSame(first, service.refresh(captured, securityManager, false));
    assertSame(second, service.refresh(captured, securityManager, false, 0));

    verify(authenticationService, times(2)).getAuthenticatedIdentity();
  }

  @Test
  void cachedRolesNeverBypassSessionExpiry() throws Exception {
    AuthenticationService authenticationService = mock(AuthenticationService.class);
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("user1", Set.of("role1"), true, "session-id");
    when(authenticationService.getAuthenticatedIdentity()).thenReturn(identity);
    when(authenticationService.getPrincipal()).thenReturn("user1");

    Session session = mock(Session.class);
    when(session.getId()).thenReturn("session-id");
    retainRoleSnapshot(session);
    Subject validSubject = authenticatedSubject(session);
    Subject expiredSubject = mock(Subject.class);
    when(expiredSubject.getSession(false)).thenReturn(null);
    Clock clock = mock(Clock.class);
    when(clock.millis()).thenReturn(1_000L, 1_001L);
    AuthenticatedSessionService service =
        spy(new AuthenticatedSessionService(authenticationService, clock));
    SecurityManager securityManager = mock(SecurityManager.class);
    doReturn(validSubject, expiredSubject)
        .when(service).restoreSubject("session-id", securityManager);

    assertSame(identity, service.refresh(identity, securityManager, false));
    assertThrows(
        SessionAuthenticationException.class,
        () -> service.refresh(identity, securityManager, false, 1_000L));

    verify(authenticationService).getAuthenticatedIdentity();
  }

  private static Subject authenticatedSubject(Session session) throws Exception {
    Subject subject = mock(Subject.class);
    when(subject.getSession(false)).thenReturn(session);
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.execute(
        org.mockito.ArgumentMatchers.<Callable<AuthenticatedIdentity>>any()))
        .thenAnswer(
            invocation -> {
              Callable<?> callable = invocation.getArgument(0);
              return callable.call();
            });
    return subject;
  }

  private static void retainRoleSnapshot(Session session) {
    AtomicReference<Object> roleSnapshot = new AtomicReference<>();
    when(session.getAttribute(anyString())).thenAnswer(unused -> roleSnapshot.get());
    doAnswer(invocation -> {
      roleSnapshot.set(invocation.getArgument(1));
      return null;
    }).when(session).setAttribute(anyString(), any());
  }
}
