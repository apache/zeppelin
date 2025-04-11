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
import org.apache.zeppelin.MiniZeppelinServer;
import org.apache.zeppelin.common.SessionInfo;
import org.apache.zeppelin.server.JsonResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import jakarta.ws.rs.core.Response.Status;

class SessionRestApiTest extends AbstractTestRestApi {
  Gson gson = new Gson();

  private static MiniZeppelinServer zepServer;

  @BeforeAll
  public static void init() throws Exception {
    zepServer = new MiniZeppelinServer(SessionRestApiTest.class.getSimpleName());
    zepServer.addInterpreter("md");
    zepServer.start();
  }

  @AfterAll
  public static void destroy() throws Exception {
    zepServer.destroy();
  }

  @BeforeEach
  void setup() {
    zConf = zepServer.getZeppelinConfiguration();
  }

  @Test
  void testGetNotAvailableSession() throws IOException {
    try (CloseableHttpResponse get = httpGet("/session/testSession")) {
      assertEquals(Status.NOT_FOUND.getStatusCode(), get.getStatusLine().getStatusCode());
    }
  }

  @Test
  void testStartAndStopSession() throws IOException {
    String interpreter = "testInterpreter";
    try (CloseableHttpResponse post = httpPost("/session?interpreter=" + interpreter, "")) {
      assertEquals(Status.OK.getStatusCode(), post.getStatusLine().getStatusCode());
      Type collectionType = new TypeToken<JsonResponse<SessionInfo>>() {
      }.getType();
      JsonResponse<SessionInfo> resp = gson
        .fromJson(EntityUtils.toString(post.getEntity(), StandardCharsets.UTF_8), collectionType);
      SessionInfo info = resp.getBody();

      // Get by interpreter name
      try (CloseableHttpResponse get = httpGet("/session?interpreter" + interpreter)) {
        assertEquals(Status.OK.getStatusCode(), get.getStatusLine().getStatusCode());
      }

      // Get by sessionId
      try (CloseableHttpResponse get = httpGet("/session/" + info.getSessionId())) {
        assertEquals(Status.OK.getStatusCode(), get.getStatusLine().getStatusCode());
      }

      // Delete session
      try (CloseableHttpResponse delete = httpDelete("/session/" + info.getSessionId())) {
        assertEquals(Status.OK.getStatusCode(), delete.getStatusLine().getStatusCode());
      }
    }

  }
}
