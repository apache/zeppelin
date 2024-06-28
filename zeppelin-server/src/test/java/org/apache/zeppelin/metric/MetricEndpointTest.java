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

package org.apache.zeppelin.metric;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.apache.zeppelin.rest.NotebookRepoRestApiTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MetricEndpointTest extends AbstractTestRestApi {

  private static MiniZeppelinServer zepServer;

  @BeforeAll
  static void setUp() throws Exception {
    zepServer = new MiniZeppelinServer(NotebookRepoRestApiTest.class.getSimpleName());
    zepServer.getZeppelinConfiguration().setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_METRIC_ENABLE_PROMETHEUS.getVarName(), "true");
    zepServer.start();
  }

  @AfterAll
  static void destroy() throws Exception {
    zepServer.destroy();
  }

  /**
   * Simple endpoint test
   * @throws IOException
   */
  @Test
  void testPrometheusMetricJVM() throws IOException {
    try (
        CloseableHttpResponse get = getHttpClient().execute(
            new HttpGet(getUrlToTest(zepServer.getZeppelinConfiguration()) + "/metrics"))) {
      assertEquals(200, get.getStatusLine().getStatusCode());
      String response = EntityUtils.toString(get.getEntity());
      assertTrue(response.contains("jvm_memory"), "Contains JVM metric");
    }
  }

  protected static String getUrlToTest(ZeppelinConfiguration zConf) {
    return "http://localhost:" + zConf.getServerPort();
  }
}
