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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class ConfigurationsRestApiTest extends AbstractTestRestApi {
  Gson gson = new Gson();

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUp();
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Test
  public void testGetAll() throws IOException {
    GetMethod get = httpGet("/configurations/all");
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(),
        new TypeToken<Map<String, Object>>(){}.getType());
    Map<String, String> body = (Map<String, String>) resp.get("body");
    assertTrue(body.size() > 0);
    // it shouldn't have key/value pair which key contains "password"
    assertTrue(Iterators.all(body.keySet().iterator(), new Predicate<String>() {
        @Override
        public boolean apply(String key) {
          return !key.contains("password");
        }
      }
    ));
  }

  @Test
  public void testGetViaPrefix() throws IOException {
    final String prefix = "zeppelin.server";
    GetMethod get = httpGet("/configurations/prefix/" + prefix);
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(),
        new TypeToken<Map<String, Object>>(){}.getType());
    Map<String, String> body = (Map<String, String>) resp.get("body");
    assertTrue(body.size() > 0);
    assertTrue(Iterators.all(body.keySet().iterator(), new Predicate<String>() {
          @Override
          public boolean apply(String key) {
            return !key.contains("password") && key.startsWith(prefix);
          }
        }
    ));
  }
}
