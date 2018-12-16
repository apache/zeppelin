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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.server.ZeppelinServer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import sun.security.acl.PrincipalImpl;

@RunWith(PowerMockRunner.class)
@PrepareForTest(org.apache.shiro.SecurityUtils.class)
public class ShiroSecurityServiceTest {
  @Mock
  org.apache.shiro.subject.Subject subject;

  ShiroSecurityService shiroSecurityService;
  ZeppelinConfiguration zeppelinConfiguration;

  @Before
  public void setup() throws Exception {
    zeppelinConfiguration = ZeppelinConfiguration.create();
    shiroSecurityService = new ShiroSecurityService(zeppelinConfiguration);
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
    when(subject.getPrincipal()).thenReturn(new PrincipalImpl(expectedName));

    Notebook notebook = Mockito.mock(Notebook.class);
    try {
      setFinalStatic(ZeppelinServer.class.getDeclaredField("notebook"), notebook);
      when(Notebook.getInstance().getConf())
          .thenReturn(new ZeppelinConfiguration(this.getClass().getResource("/zeppelin-site.xml")));
    } catch (NoSuchFieldException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (ConfigurationException e) {
      e.printStackTrace();
    }
  }

  private void setFinalStatic(Field field, Object newValue)
      throws NoSuchFieldException, IllegalAccessException {
    field.setAccessible(true);
    Field modifiersField = Field.class.getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
    field.set(null, newValue);
  }


}
