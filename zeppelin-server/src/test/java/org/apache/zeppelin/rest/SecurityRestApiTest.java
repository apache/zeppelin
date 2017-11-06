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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.methods.GetMethod;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class SecurityRestApiTest extends AbstractTestRestApi {
  Gson gson = new Gson();

  @Rule
  public ErrorCollector collector = new ErrorCollector();

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUpWithAuthenticationEnable(SecurityRestApiTest.class.getSimpleName());
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Test
  public void testTicket() throws IOException {
    GetMethod get = httpGet("/security/ticket", "admin", "password1");
    get.addRequestHeader("Origin", "http://localhost");
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(),
        new TypeToken<Map<String, Object>>(){}.getType());
    Map<String, String> body = (Map<String, String>) resp.get("body");
    collector.checkThat("Paramater principal", body.get("principal"),
        CoreMatchers.equalTo("admin"));
    collector.checkThat("Paramater ticket", body.get("ticket"),
        CoreMatchers.not("anonymous"));
    get.releaseConnection();
  }

  @Test
  public void testGetUserList() throws IOException {
    GetMethod get = httpGet("/security/userlist/admi", "admin", "password1");
    get.addRequestHeader("Origin", "http://localhost");
    Map<String, Object> resp = gson.fromJson(get.getResponseBodyAsString(),
        new TypeToken<Map<String, Object>>(){}.getType());
    List<String> userList = (List) ((Map) resp.get("body")).get("users");
    collector.checkThat("Search result size", userList.size(),
        CoreMatchers.equalTo(1));
    collector.checkThat("Search result contains admin", userList.contains("admin"),
        CoreMatchers.equalTo(true));
    get.releaseConnection();

    GetMethod notUser = httpGet("/security/userlist/randomString", "admin", "password1");
    notUser.addRequestHeader("Origin", "http://localhost");
    Map<String, Object> notUserResp = gson.fromJson(notUser.getResponseBodyAsString(),
        new TypeToken<Map<String, Object>>(){}.getType());
    List<String> emptyUserList = (List) ((Map) notUserResp.get("body")).get("users");
    collector.checkThat("Search result size", emptyUserList.size(),
        CoreMatchers.equalTo(0));

    notUser.releaseConnection();
  }
}

