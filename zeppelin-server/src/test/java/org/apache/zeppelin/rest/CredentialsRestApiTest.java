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
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class CredentialsRestApiTest extends AbstractTestRestApi {
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
  public void testInvalidRequest() throws IOException {
    String jsonInvalidRequestEntityNull = "{\"entity\" : null, \"username\" : \"test\", \"password\" : \"testpass\"}";
    String jsonInvalidRequestNameNull = "{\"entity\" : \"test\", \"username\" : null, \"password\" : \"testpass\"}";
    String jsonInvalidRequestPasswordNull = "{\"entity\" : \"test\", \"username\" : \"test\", \"password\" : null}";
    String jsonInvalidRequestAllNull = "{\"entity\" : null, \"username\" : null, \"password\" : null}";

    PutMethod entityNullPut = httpPut("/credential", jsonInvalidRequestEntityNull);
    entityNullPut.addRequestHeader("Origin", "http://localhost");
    assertThat(entityNullPut, isBadRequest());
    entityNullPut.releaseConnection();

    PutMethod nameNullPut = httpPut("/credential", jsonInvalidRequestNameNull);
    nameNullPut.addRequestHeader("Origin", "http://localhost");
    assertThat(nameNullPut, isBadRequest());
    nameNullPut.releaseConnection();

    PutMethod passwordNullPut = httpPut("/credential", jsonInvalidRequestPasswordNull);
    passwordNullPut.addRequestHeader("Origin", "http://localhost");
    assertThat(passwordNullPut, isBadRequest());
    passwordNullPut.releaseConnection();

    PutMethod allNullPut = httpPut("/credential", jsonInvalidRequestAllNull);
    allNullPut.addRequestHeader("Origin", "http://localhost");
    assertThat(allNullPut, isBadRequest());
    allNullPut.releaseConnection();
  }

}

