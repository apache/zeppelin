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
import java.io.IOException;
import java.util.Map;
import org.apache.commons.httpclient.methods.GetMethod;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KnoxRestApiTest extends AbstractTestRestApi {

  private String KNOX_COOKIE = "hadoop-jwt=eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlzcyI6IktOT1hTU08iLCJleHAiOjE1MTM3NDU1MDd9.E2cWQo2sq75h0G_9fc9nWkL0SFMI5x_-Z0Zzr0NzQ86X4jfxliWYjr0M17Bm9GfPHRRR66s7YuYXa6DLbB4fHE0cyOoQnkfJFpU_vr1xhy0_0URc5v-Gb829b9rxuQfjKe-37hqbUdkwww2q6QQETVMvzp0rQKprUClZujyDvh0;";

  @Rule
  public ErrorCollector collector = new ErrorCollector();

  private static final Logger LOG = LoggerFactory.getLogger(KnoxRestApiTest.class);

  Gson gson = new Gson();

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUpWithKnoxEnable(KnoxRestApiTest.class.getSimpleName());
  }

  @AfterClass
  public static void destroy() throws Exception {
    AbstractTestRestApi.shutDown();
  }

  @Before
  public void setUp() {
  }


  @Test
  public void testThatOtherUserCanAccessNoteIfPermissionNotSet() throws IOException {
    GetMethod loginWithoutCookie = httpGet("/api/security/ticket");
    Map result = gson.fromJson(loginWithoutCookie.getResponseBodyAsString(), Map.class);
    collector.checkThat("Path is redirected to /login", loginWithoutCookie.getPath(),
        CoreMatchers.containsString("login"));

    collector.checkThat("Path is redirected to /login", loginWithoutCookie.getPath(),
        CoreMatchers.containsString("login"));

    collector.checkThat("response contains redirect URL",
        ((Map) result.get("body")).get("redirectURL").toString(), CoreMatchers.equalTo(
            "https://domain.example.com/gateway/knoxsso/knoxauth/login.html?originalUrl="));

    GetMethod loginWithCookie = httpGet("/api/security/ticket", "", "", KNOX_COOKIE);
    result = gson.fromJson(loginWithCookie.getResponseBodyAsString(), Map.class);

    collector.checkThat("User logged in as admin",
        ((Map) result.get("body")).get("principal").toString(), CoreMatchers.equalTo("admin"));

    System.out.println(result);
  }

}
