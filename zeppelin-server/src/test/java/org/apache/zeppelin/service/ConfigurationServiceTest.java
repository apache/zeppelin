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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.socket.NotebookServer;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class ConfigurationServiceTest extends AbstractTestRestApi {

  private static ConfigurationService configurationService;

  private ServiceContext context =
      new ServiceContext(AuthenticationInfo.ANONYMOUS, new HashSet<>());

  private ServiceCallback callback = mock(ServiceCallback.class);

  @BeforeClass
  public static void setUp() throws Exception {
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_HELIUM_REGISTRY.getVarName(),
        "helium");
    AbstractTestRestApi.startUp(ConfigurationServiceTest.class.getSimpleName());
    configurationService = TestUtils.getInstance(ConfigurationService.class);
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Test
  public void testFetchConfiguration() throws IOException {
    Map<String, String> properties = configurationService.getAllProperties(context, callback);
    verify(callback).onSuccess(properties, context);
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      assertFalse(entry.getKey().contains("password"));
    }

    reset(callback);
    properties = configurationService.getPropertiesWithPrefix("zeppelin.server", context, callback);
    verify(callback).onSuccess(properties, context);
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      assertFalse(entry.getKey().contains("password"));
      assertTrue(entry.getKey().startsWith("zeppelin.server"));
    }
  }
}
