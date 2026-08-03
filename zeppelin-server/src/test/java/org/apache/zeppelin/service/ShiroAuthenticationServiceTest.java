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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.security.Principal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.lang.util.LifecycleUtils;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.realm.jdbc.JdbcRealm;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.realm.ActiveDirectoryGroupRealm;
import org.apache.zeppelin.realm.jwt.KnoxJwtRealm;
import org.apache.zeppelin.service.shiro.AbstractShiroTest;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShiroAuthenticationServiceTest extends AbstractShiroTest {

  Subject subject;
  ShiroAuthenticationService shiroSecurityService;
  ZeppelinConfiguration zConf;

  @BeforeEach
  void setup() throws Exception {
    subject = mock(Subject.class);
    zConf = mock(ZeppelinConfiguration.class);
    when(zConf.getShiroPath()).thenReturn(StringUtils.EMPTY);
    setSubject(subject);
    shiroSecurityService = new ShiroAuthenticationService(zConf);
  }

  @Test
  void testGetMatchedUsersWithJdbcRealm() throws Exception {

    // given in-memory jdbcRealm with some users
    JdbcRealm realm = new JdbcRealm();
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    realm.setDataSource(dataSource);

    LifecycleUtils.init(realm);
    DefaultSecurityManager securityManager = new DefaultSecurityManager(realm);
    ThreadContext.bind(securityManager);

    try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE users (username VARCHAR PRIMARY KEY, password VARCHAR)");
      stmt.execute("INSERT INTO users VALUES ('admin', '')");
      stmt.execute("INSERT INTO users VALUES ('admin1', '')");
      stmt.execute("INSERT INTO users VALUES ('test', '')");
    }

    // when
    List<String> users = shiroSecurityService.getMatchedUsers("adm", 1);

    // then
    assertEquals(1, users.size());
    assertEquals("admin", users.get(0));
  }

  @Test
  void canGetPrincipalName() {
    String expectedName = "java.security.Principal.getName()";
    setupPrincipalName(expectedName);
    assertEquals(expectedName, shiroSecurityService.getPrincipal());
  }

  @Test
  void testUsernameForceLowerCase() throws IOException, InterruptedException {
    String expectedName = "java.security.Principal.getName()";
    when(zConf.isUsernameForceLowerCase()).thenReturn(true);
    setupPrincipalName(expectedName);
    assertEquals(expectedName.toLowerCase(), shiroSecurityService.getPrincipal());
  }

  @Test
  void capturesPrincipalRolesAndSessionFromOneSubject() {
    setupPrincipalName("TestUser");
    when(zConf.isUsernameForceLowerCase()).thenReturn(true);

    Session session = mock(Session.class);
    when(session.getId()).thenReturn("session-id");
    when(subject.getSession(false)).thenReturn(session);

    KnoxJwtRealm realm = spy(new KnoxJwtRealm());
    LifecycleUtils.init(realm);
    doReturn(Set.of("reader")).when(realm).mapGroupPrincipals("testuser");
    DefaultSecurityManager securityManager = new DefaultSecurityManager(realm);
    ThreadContext.bind(securityManager);

    AuthenticatedIdentity identity = shiroSecurityService.getAuthenticatedIdentity();

    assertEquals("testuser", identity.getPrincipal());
    assertEquals(Set.of("reader"), identity.getRoles());
    assertEquals("session-id", identity.getSessionId().orElseThrow());
    assertTrue(identity.isAuthenticated());
    verify(subject, times(1)).isAuthenticated();
    verify(subject, times(1)).getPrincipal();
    verify(subject, times(1)).getSession(false);
  }

  @Test
  void capturesAnonymousIdentityWithoutLookingUpRolesOrSession() {
    when(subject.isAuthenticated()).thenReturn(false);

    AuthenticatedIdentity identity = shiroSecurityService.getAuthenticatedIdentity();

    assertEquals(AuthenticatedIdentity.ANONYMOUS_PRINCIPAL, identity.getPrincipal());
    assertEquals(Collections.emptySet(), identity.getRoles());
    assertFalse(identity.isAuthenticated());
    assertTrue(identity.getSessionId().isEmpty());
    verify(subject, times(1)).isAuthenticated();
    verify(subject, times(0)).getPrincipal();
    verify(subject, times(0)).getSession(false);
  }

  @Test
  void testKnoxGetRoles() {
    setupPrincipalName("test");

    KnoxJwtRealm realm = spy(new KnoxJwtRealm());
    LifecycleUtils.init(realm);
    Set<String> testRoles = new HashSet<String>();
    testRoles.add("role1");
    testRoles.add("role2");

    when(realm.mapGroupPrincipals("test")).thenReturn(testRoles);

    DefaultSecurityManager securityManager = new DefaultSecurityManager(realm);
    ThreadContext.bind(securityManager);

    Set<String> roles = shiroSecurityService.getAssociatedRoles();
    assertEquals(testRoles, roles);
  }

  @Test
  void capturesActiveDirectoryRolesWithOneAuthorizationQuery() throws Exception {
    setupPrincipalName("test");

    ActiveDirectoryGroupRealm realm = spy(new ActiveDirectoryGroupRealm());
    LifecycleUtils.init(realm);
    doReturn(new SimpleAuthorizationInfo(Set.of("role1", "role2")))
        .when(realm).queryForAuthorizationInfo(any());
    DefaultSecurityManager securityManager = new DefaultSecurityManager(realm);
    ThreadContext.bind(securityManager);

    Set<String> roles = shiroSecurityService.getAssociatedRoles();

    assertEquals(Set.of("role1", "role2"), roles);
    verify(realm, times(1)).queryForAuthorizationInfo(any());
    verify(subject, never()).hasRole(any());
  }

  @AfterEach
  public void tearDownSubject() {
    clearSubject();
  }

  private void setupPrincipalName(String expectedName) {
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.getPrincipal()).thenReturn(new TestPrincipal(expectedName));
  }

  public class TestPrincipal implements Principal {

    private String username;

    public TestPrincipal(String username) {
      this.username = username;
    }

    public String getUsername() {
      return username;
    }

    @Override
    public String getName() {
      return String.valueOf(username);
    }
  }
}
