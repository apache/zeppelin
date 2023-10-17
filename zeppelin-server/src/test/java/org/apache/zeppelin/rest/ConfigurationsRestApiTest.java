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
package org.apache.zeppelin.rest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

class ConfigurationsRestApiTest extends AbstractTestRestApi {
  Gson gson = new Gson();

  @BeforeAll
  static void init() throws Exception {
    AbstractTestRestApi.startUp(ConfigurationsRestApi.class.getSimpleName());
  }

  @AfterAll
  static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Test
  void testGetAll() throws IOException {
    CloseableHttpResponse get = httpGet("/configurations/all");
    Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
        new TypeToken<Map<String, Object>>(){}.getType());
    Map<String, String> body = (Map<String, String>) resp.get("body");
    assertTrue(body.size() > 0);
    // it shouldn't have key/value pair which key contains "password"
    for (String key : body.keySet()) {
      assertTrue(!key.contains("password"));
    }
    get.close();
  }

  @Test
  void testGetViaPrefix() throws IOException {
    final String prefix = "zeppelin.server";
    CloseableHttpResponse get = httpGet("/configurations/prefix/" + prefix);
    Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
        new TypeToken<Map<String, Object>>(){}.getType());
    Map<String, String> body = (Map<String, String>) resp.get("body");
    assertTrue(body.size() > 0);
    for (String key : body.keySet()) {
      assertTrue(!key.contains("password") && key.startsWith(prefix));
    }
    get.close();
  }
}
