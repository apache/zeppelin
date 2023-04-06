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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.LifecycleUtils;
import org.apache.shiro.util.ThreadContext;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.realm.jwt.KnoxJwtRealm;
import org.apache.zeppelin.service.shiro.AbstractShiroTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class ShiroAuthenticationServiceTest extends AbstractShiroTest {

  Subject subject;
  ShiroAuthenticationService shiroSecurityService;
  ZeppelinConfiguration zConf;

  @Before
  public void setup() throws Exception {
    subject = mock(Subject.class);
    zConf = mock(ZeppelinConfiguration.class);
    when(zConf.getShiroPath()).thenReturn(StringUtils.EMPTY);
    setSubject(subject);
    shiroSecurityService = new ShiroAuthenticationService(zConf);
  }

  @Test
  public void canGetPrincipalName() {
    String expectedName = "java.security.Principal.getName()";
    setupPrincipalName(expectedName);
    assertEquals(expectedName, shiroSecurityService.getPrincipal());
  }

  @Test
  public void testUsernameForceLowerCase() throws IOException, InterruptedException {
    String expectedName = "java.security.Principal.getName()";
    when(zConf.isUsernameForceLowerCase()).thenReturn(true);
    setupPrincipalName(expectedName);
    assertEquals(expectedName.toLowerCase(), shiroSecurityService.getPrincipal());
  }

  @Test
  public void testKnoxGetRoles() {
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

  @After
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
