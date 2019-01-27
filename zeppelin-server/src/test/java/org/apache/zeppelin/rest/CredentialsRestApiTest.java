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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.apache.zeppelin.service.NoSecurityService;
import org.apache.zeppelin.service.SecurityService;
import org.apache.zeppelin.user.Credentials;
import org.apache.zeppelin.user.UserCredentials;
import org.junit.Before;
import org.junit.Test;

public class CredentialsRestApiTest {
  private final Gson gson = new Gson();

  private CredentialRestApi credentialRestApi;
  private Credentials credentials;
  private SecurityService securityService;

  @Before
  public void setUp() throws IOException {
    credentials =
        new Credentials(false, Files.createTempFile("credentials", "test").toString(), null);
    securityService = new NoSecurityService();
    credentialRestApi = new CredentialRestApi(credentials, securityService);
  }

  @Test
  public void testInvalidRequest() throws IOException {
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
  public void testCredentialsAPIs() throws IOException {
    String requestData1 =
        "{\"entity\" : \"entityname\", \"username\" : \"myuser\", \"password\" " + ": \"mypass\"}";
    String entity = "entityname";

    credentialRestApi.putCredentials(requestData1);
    assertNotNull("CredentialMap should be null", testGetUserCredentials());

    credentialRestApi.removeCredentialEntity(entity);
    assertNull("CredentialMap should be null", testGetUserCredentials().get("entity1"));

    credentialRestApi.removeCredentials();
    assertEquals("Compare CredentialMap", testGetUserCredentials().toString(), "{}");
  }
}
