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
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;

class SecurityRestApiTest extends AbstractTestRestApi {
  Gson gson = new Gson();

  @BeforeAll
  static void init() throws Exception {
    AbstractTestRestApi.startUpWithAuthenticationEnable(SecurityRestApiTest.class.getSimpleName());
  }

  @AfterAll
  static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Test
  void testTicket() throws IOException {
    CloseableHttpResponse get = httpGet("/security/ticket", "admin", "password1");
    Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
        new TypeToken<Map<String, Object>>(){}.getType());
    Map<String, String> body = (Map<String, String>) resp.get("body");
    assertThat("Paramater principal", body.get("principal"),
        CoreMatchers.equalTo("admin"));
    assertThat("Paramater ticket", body.get("ticket"),
        CoreMatchers.not("anonymous"));
    get.close();
  }

  @Test
  void testGetUserList() throws IOException {
    CloseableHttpResponse get = httpGet("/security/userlist/admi", "admin", "password1");
    Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
        new TypeToken<Map<String, Object>>(){}.getType());
    List<String> userList = (List) ((Map) resp.get("body")).get("users");
    assertThat("Search result size", userList.size(),
        CoreMatchers.equalTo(1));
    assertThat("Search result contains admin", userList.contains("admin"),
        CoreMatchers.equalTo(true));
    get.close();

    CloseableHttpResponse notUser = httpGet("/security/userlist/randomString", "admin", "password1");
    Map<String, Object> notUserResp = gson.fromJson(EntityUtils.toString(notUser.getEntity(), StandardCharsets.UTF_8),
        new TypeToken<Map<String, Object>>(){}.getType());
    List<String> emptyUserList = (List) ((Map) notUserResp.get("body")).get("users");
    assertThat("Search result size", emptyUserList.size(),
        CoreMatchers.equalTo(0));

    notUser.close();
  }

  @Test
  void testRolesEscaped() throws IOException {
    CloseableHttpResponse get = httpGet("/security/ticket", "admin", "password1");
    Map<String, Object> resp = gson.fromJson(EntityUtils.toString(get.getEntity(), StandardCharsets.UTF_8),
            new TypeToken<Map<String, Object>>(){}.getType());
    String roles = (String) ((Map) resp.get("body")).get("roles");
    assertThat("Paramater roles", roles,
            CoreMatchers.equalTo("[\"admin\"]"));
    get.close();
  }

}
