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
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.user.UserCredentials;
import org.apache.zeppelin.utils.SecurityUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.*;

public class CredentialsRestApiTest extends AbstractTestRestApi {
  protected static final Logger LOG = LoggerFactory.getLogger(CredentialsRestApiTest.class);
  Gson gson = new Gson();

  @BeforeClass
  public static void init() throws Exception {
    AbstractTestRestApi.startUp(CredentialsRestApiTest.class.getSimpleName());
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

  public Map<String, UserCredentials> testGetUserCredentials() throws IOException {
    GetMethod getMethod = httpGet("/credential");
    getMethod.addRequestHeader("Origin", "http://localhost");
    Map<String, Object> resp = gson.fromJson(getMethod.getResponseBodyAsString(),
            new TypeToken<Map<String, Object>>(){}.getType());
    Map<String, Object> body = (Map<String, Object>) resp.get("body");
    Map<String, UserCredentials> credentialMap = (Map<String, UserCredentials>)body.get("userCredentials");
    getMethod.releaseConnection();
    return credentialMap;
  }

  public void testPutUserCredentials(String requestData) throws IOException {
    PutMethod putMethod = httpPut("/credential", requestData);
    putMethod.addRequestHeader("Origin", "http://localhost");
    assertThat(putMethod, isAllowed());
    putMethod.releaseConnection();
  }

  public void testRemoveUserCredentials() throws IOException {
    DeleteMethod deleteMethod = httpDelete("/credential/");
    assertThat("Test delete method:", deleteMethod, isAllowed());
    deleteMethod.releaseConnection();
  }

  public void testRemoveCredentialEntity(String entity) throws IOException {
    DeleteMethod deleteMethod = httpDelete("/credential/" + entity);
    assertThat("Test delete method:", deleteMethod, isAllowed());
    deleteMethod.releaseConnection();
  }

  @Test
  public void testCredentialsAPIs() throws IOException {
    String requestData1 = "{\"entity\" : \"entityname\", \"username\" : \"myuser\", \"password\" : \"mypass\"}";
    String entity = "entityname";
    Map<String, UserCredentials> credentialMap;

    testPutUserCredentials(requestData1);
    credentialMap = testGetUserCredentials();
    assertNotNull("CredentialMap should be null", credentialMap);

    testRemoveCredentialEntity(entity);
    credentialMap = testGetUserCredentials();
    assertNull("CredentialMap should be null", credentialMap.get("entity1"));

    testRemoveUserCredentials();
    credentialMap = testGetUserCredentials();
    assertEquals("Compare CredentialMap", credentialMap.toString(), "{}");
  }
}
