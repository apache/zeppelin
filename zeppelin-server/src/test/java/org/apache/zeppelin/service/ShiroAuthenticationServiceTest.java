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

import java.io.IOException;
import java.security.Principal;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Notebook;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(org.apache.shiro.SecurityUtils.class)
public class ShiroAuthenticationServiceTest {
  @Mock
  org.apache.shiro.subject.Subject subject;

  ShiroAuthenticationService shiroSecurityService;
  ZeppelinConfiguration zeppelinConfiguration;

  @Before
  public void setup() throws Exception {
    zeppelinConfiguration = ZeppelinConfiguration.create();
    shiroSecurityService = new ShiroAuthenticationService(zeppelinConfiguration);
  }

  @Test
  public void canGetPrincipalName()  {
    String expectedName = "java.security.Principal.getName()";
    setupPrincipalName(expectedName);
    assertEquals(expectedName, shiroSecurityService.getPrincipal());
  }

  @Test
  public void testUsernameForceLowerCase() throws IOException, InterruptedException {
    String expectedName = "java.security.Principal.getName()";
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_USERNAME_FORCE_LOWERCASE
        .getVarName(), String.valueOf(true));
    setupPrincipalName(expectedName);
    assertEquals(expectedName.toLowerCase(), shiroSecurityService.getPrincipal());
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_USERNAME_FORCE_LOWERCASE
        .getVarName(), String.valueOf(false));
  }

  private void setupPrincipalName(String expectedName) {
    PowerMockito.mockStatic(org.apache.shiro.SecurityUtils.class);
    when(org.apache.shiro.SecurityUtils.getSubject()).thenReturn(subject);
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.getPrincipal()).thenReturn(new TestPrincipal(expectedName));

    Notebook notebook = Mockito.mock(Notebook.class);
    when(notebook.getConf())
        .thenReturn(ZeppelinConfiguration.create("zeppelin-site.xml"));

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
