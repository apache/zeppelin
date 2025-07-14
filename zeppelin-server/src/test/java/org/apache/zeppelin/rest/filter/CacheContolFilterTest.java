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
package org.apache.zeppelin.rest.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.rest.AbstractTestRestApi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.HttpHeaders;

class CacheContolFilterTest extends AbstractTestRestApi {

  private static MiniZeppelinServer zepServer;

  @BeforeAll
  static void init() throws Exception {
    zepServer = new MiniZeppelinServer(CacheContolFilterTest.class.getSimpleName());
    zepServer.start();
  }

  @AfterAll
  static void destroy() throws Exception {
    zepServer.destroy();
  }

  @BeforeEach
  void setUp() {
    zConf = zepServer.getZeppelinConfiguration();
  }

  @Test
  void testCacheControl() throws IOException {
    try (CloseableHttpResponse get = httpGet("/version")) {
      Header[] headers = get.getHeaders(HttpHeaders.CACHE_CONTROL);
      assertEquals(1, headers.length);
      Header cacheHeader = headers[0];
      assertEquals(HttpHeaders.CACHE_CONTROL, cacheHeader.getName());
      assertTrue(cacheHeader.getValue().contains("no-cache"), () -> "Actual content:" + cacheHeader.getValue());
    }
  }
}
