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
package org.apache.zeppelin.security;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

class DirAccessTest extends AbstractTestRestApi {
  private static final Logger LOGGER = LoggerFactory.getLogger(DirAccessTest.class);

  private MiniZeppelinServer zepServer;

  @Test
  void testDirAccessForbidden() throws Exception {
    try {
      zepServer = new MiniZeppelinServer(DirAccessTest.class.getSimpleName());
      zConf = zepServer.getZeppelinConfiguration();
      zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_DEFAULT_DIR_ALLOWED
          .getVarName(), "false");
      zepServer.start();
      CloseableHttpResponse getMethod =
          getHttpClient().execute(new HttpGet(getUrlToTest() + "/app"));
      LOGGER.info("Invoke getMethod - "
          + EntityUtils.toString(getMethod.getEntity(), StandardCharsets.UTF_8));
      LOGGER.info("server port {}", zConf.getServerPort());
      assertEquals(HttpStatus.SC_FORBIDDEN, getMethod.getStatusLine().getStatusCode());
    } finally {
      zepServer.destroy();
    }
  }

  @Test
  void testDirAccessOk() throws Exception {
    try {
      zepServer = new MiniZeppelinServer(DirAccessTest.class.getSimpleName());
      zConf = zepServer.getZeppelinConfiguration();
      zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_DEFAULT_DIR_ALLOWED
                .getVarName(), "true");
      zepServer.start();
      CloseableHttpResponse getMethod =
          getHttpClient().execute(new HttpGet(getUrlToTest() + "/app"));
      LOGGER.info("Invoke getMethod - "
          + EntityUtils.toString(getMethod.getEntity(), StandardCharsets.UTF_8));
      assertEquals(HttpStatus.SC_OK, getMethod.getStatusLine().getStatusCode());
    } finally {
      zepServer.destroy();
    }
  }

  protected String getUrlToTest() {
    String url = "http://localhost:" + zConf.getServerPort() + "/classic";
    if (System.getProperty("url") != null) {
      url = System.getProperty("url");
    }
    return url;
  }
}

