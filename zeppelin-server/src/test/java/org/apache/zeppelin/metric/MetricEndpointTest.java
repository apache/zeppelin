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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class MetricEndpointTest extends AbstractTestRestApi {

  @BeforeClass
  public static void setUp() throws Exception {
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_METRIC_ENABLE_PROMETHEUS.getVarName(),
      "true");
    AbstractTestRestApi.startUp(MetricEndpointTest.class.getSimpleName());
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  /**
   * Simple endpoint test
   * @throws IOException
   */
  @Test
  public void testPrometheusMetricJVM() throws IOException {
    CloseableHttpResponse get = getHttpClient().execute(new HttpGet(getUrlToTest() + "/metrics"));
    assertEquals(200, get.getStatusLine().getStatusCode());
    String response = EntityUtils.toString(get.getEntity());
    assertTrue("Contains JVM metric", response.contains("jvm_memory"));
    get.close();
  }

  protected static String getUrlToTest() {
    String url = "http://localhost:8080";
    if (System.getProperty("url") != null) {
      url = System.getProperty("url");
    }
    return url;
  }
}
