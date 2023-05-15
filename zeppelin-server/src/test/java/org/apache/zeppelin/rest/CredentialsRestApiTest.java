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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.Map;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.zeppelin.service.AuthenticationService;
import org.apache.zeppelin.service.NoAuthenticationService;
import org.apache.zeppelin.user.Credentials;
import org.apache.zeppelin.user.UserCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CredentialsRestApiTest {
  private final Gson gson = new Gson();

  private CredentialRestApi credentialRestApi;
  private Credentials credentials;
  private AuthenticationService authenticationService;

  @BeforeEach
  public void setUp() throws IOException {
    credentials = new Credentials();
    authenticationService = new NoAuthenticationService();
    credentialRestApi = new CredentialRestApi(credentials, authenticationService);
  }

  @Test
  void testInvalidRequest() throws IOException {
    String jsonInvalidRequestEntityNull =
        "{\"entity\" : null, \"username\" : \"test\", " + "\"password\" : \"testpass\"}";
    String jsonInvalidRequestNameNull =
        "{\"entity\" : \"test\", \"username\" : null, " + "\"password\" : \"testpass\"}";
    String jsonInvalidRequestPasswordNull =
        "{\"entity\" : \"test\", \"username\" : \"test\", " + "\"password\" : null}";
    String jsonInvalidRequestAllNull =
        "{\"entity\" : null, \"username\" : null, " + "\"password\" : null}";

    Response response = credentialRestApi.putCredentials(jsonInvalidRequestEntityNull);
    assertEquals(Status.BAD_REQUEST, response.getStatusInfo().toEnum());

    response = credentialRestApi.putCredentials(jsonInvalidRequestNameNull);
    assertEquals(Status.BAD_REQUEST, response.getStatusInfo().toEnum());

    response = credentialRestApi.putCredentials(jsonInvalidRequestPasswordNull);
    assertEquals(Status.BAD_REQUEST, response.getStatusInfo().toEnum());

    response = credentialRestApi.putCredentials(jsonInvalidRequestAllNull);
    assertEquals(Status.BAD_REQUEST, response.getStatusInfo().toEnum());
  }

  public Map<String, UserCredentials> testGetUserCredentials() throws IOException {
    Response response = credentialRestApi.getCredentials();
    Map<String, Object> resp =
        gson.fromJson(
            response.getEntity().toString(), new TypeToken<Map<String, Object>>() {}.getType());
    Map<String, Object> body = (Map<String, Object>) resp.get("body");
    Map<String, UserCredentials> credentialMap =
        (Map<String, UserCredentials>) body.get("userCredentials");
    return credentialMap;
  }

  @Test
  void testCredentialsAPIs() throws IOException {
    String requestData1 =
        "{\"entity\" : \"entityname\", \"username\" : \"myuser\", \"password\" " + ": \"mypass\"}";
    String entity = "entityname";

    credentialRestApi.putCredentials(requestData1);
    assertNotNull(testGetUserCredentials(), "CredentialMap should not be null");

    credentialRestApi.removeCredentialEntity(entity);
    assertNull(testGetUserCredentials().get("entity1"), "CredentialMap should be null");

    credentialRestApi.removeCredentials();
    assertEquals("{}", testGetUserCredentials().toString(), "Compare CredentialMap");
  }
}
