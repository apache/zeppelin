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

package org.apache.zeppelin.integration;


import org.apache.zeppelin.client.ClientConfig;
import org.apache.zeppelin.client.ZeppelinClient;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ZeppelinClientWithAuthIntegrationTest extends AbstractTestRestApi {
  private static Notebook notebook;

  private static ClientConfig clientConfig;
  private static ZeppelinClient zeppelinClient;

  @BeforeClass
  public static void setUp() throws Exception {
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HELIUM_REGISTRY.getVarName(),
            "helium");
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_ALLOWED_ORIGINS.getVarName(), "*");

    AbstractTestRestApi.startUpWithAuthenticationEnable(ZeppelinClientWithAuthIntegrationTest.class.getSimpleName());
    notebook = TestUtils.getInstance(Notebook.class);

    clientConfig = new ClientConfig("http://localhost:8080");
    zeppelinClient = new ZeppelinClient(clientConfig);
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Test
  public void testZeppelinVersion() throws Exception {
    String version = zeppelinClient.getVersion();
    LOG.info("Zeppelin version: " + version);
  }

  @Test
  public void testCreateNoteWithoutLogin() throws Exception {
    try {
      zeppelinClient.createNote("/note_1");
      fail("Should fail due to not login");
    } catch (Exception e) {
      assertTrue(e.getMessage(), e.getMessage().contains("login first"));
    }
  }

  @Test
  public void testCreateNoteAfterLogin() throws Exception {
    zeppelinClient.login("admin", "password1");
    zeppelinClient.createNote("/note_2");
  }

  @Test
  public void testLoginFailed() throws Exception {
    // wrong password
    try {
      zeppelinClient.login("admin", "invalid_password");
      fail("Should fail to login");
    } catch (Exception e) {
      assertTrue(e.getMessage(), e.getMessage().contains("Forbidden"));
    }

    // wrong username
    try {
      zeppelinClient.login("invalid_user", "password1");
      fail("Should fail to login");
    } catch (Exception e) {
      assertTrue(e.getMessage(), e.getMessage().contains("Forbidden"));
    }
  }
}

