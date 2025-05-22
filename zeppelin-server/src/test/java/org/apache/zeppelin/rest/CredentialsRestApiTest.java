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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.service.AuthenticationService;
import org.apache.zeppelin.service.NoAuthenticationService;
import org.apache.zeppelin.storage.ConfigStorage;
import org.apache.zeppelin.user.Credentials;
import org.apache.zeppelin.user.CredentialsMgr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

class CredentialsRestApiTest {
  private final Gson gson = new Gson();

  private CredentialRestApi credentialRestApi;
  private CredentialsMgr credentials;
  private AuthenticationService authenticationService;

  @BeforeEach
  public void setUp() throws IOException {
    ZeppelinConfiguration zconf = mock(ZeppelinConfiguration.class);
    when(zconf.credentialsPersist()).thenReturn(false);
    ConfigStorage storage = mock(ConfigStorage.class);
    credentials = new CredentialsMgr(zconf, storage);
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

  public Credentials testGetUserCredentials() {
    Response response = credentialRestApi.getCredentials();
    System.out.println(response.getEntity().toString());
    Type collectionType = new TypeToken<JsonResponse<Credentials>>() {
    }.getType();
    JsonResponse<Credentials> resp = gson.fromJson(response.getEntity().toString(), collectionType);
    return resp.getBody();
  }

  @Test
  void testCredentialsAPIs() throws IOException {
    String requestData1 =
        "{\"entity\" : \"entityname\", \"username\" : \"myuser\", \"password\" " + ": \"mypass\"}";
    String entity = "entityname";

    Response response = credentialRestApi.putCredentials(requestData1);
    assertEquals(Status.OK, response.getStatusInfo().toEnum());
    assertNotNull(testGetUserCredentials(), "CredentialMap should not be null");

    response = credentialRestApi.removeCredentialEntity("not_exists");
    assertEquals(Status.NOT_FOUND, response.getStatusInfo().toEnum());

    response = credentialRestApi.removeCredentialEntity(entity);
    assertEquals(Status.OK, response.getStatusInfo().toEnum());

    assertNull(testGetUserCredentials().get("entity1"), "CredentialMap should be null");
  }

  @Test
  void testCredentialsAPIWithRoles() {
    String entity = "entityname";
    AuthenticationService mockAuthenticationService = mock(AuthenticationService.class);
    credentialRestApi = new CredentialRestApi(credentials, mockAuthenticationService);
    Set<String> roles = new HashSet<>();
    roles.add("group1");
    roles.add("group2");
    when(mockAuthenticationService.getPrincipal()).thenReturn("user");
    when(mockAuthenticationService.getAssociatedRoles()).thenReturn(roles);

    // Create credentials
    String requestData1 =
        "{\"entity\":\"entityname\",\"username\":\"myuser\",\"password\":\"mypass\"}";
    Response response = credentialRestApi.putCredentials(requestData1);
    assertEquals(Status.OK, response.getStatusInfo().toEnum());
    assertNotNull(testGetUserCredentials().get(entity), "CredentialMap should not be null");
    assertTrue(testGetUserCredentials().get(entity).getReaders().isEmpty(),
      "Reader of CredentialMap should be empty");

    // Switch to user2
    when(mockAuthenticationService.getPrincipal()).thenReturn("user2");
    assertFalse(testGetUserCredentials().isEmpty(), "CredentialMap should not be empty");
    // Try to delete credentials with user 2
    response = credentialRestApi.removeCredentialEntity(entity);
    assertEquals(Status.FORBIDDEN, response.getStatusInfo().toEnum());

    // Try to update credentials with user2
    String requestData2 =
      "{\"entity\":\"entityname\",\"username\":\"myuser\",\"password\":\"mypass\",\"readers\":[\"group1\"]}";
    response = credentialRestApi.putCredentials(requestData2);
    assertEquals(Status.FORBIDDEN, response.getStatusInfo().toEnum());

    // Switch to user
    when(mockAuthenticationService.getPrincipal()).thenReturn("user");

    // Update credentials
    response = credentialRestApi.putCredentials(requestData2);
    assertEquals(Status.OK, response.getStatusInfo().toEnum());
    assertFalse(testGetUserCredentials().get(entity).getReaders().isEmpty(),
      "CredentialMap should be updated");
    assertEquals("mypass", testGetUserCredentials().get(entity).getPassword(),
      "Password should be readable the owner");

    // Switch to user2
    when(mockAuthenticationService.getPrincipal()).thenReturn("user2");
    assertFalse(testGetUserCredentials().isEmpty(), "CredentialMap should now readable");
    assertFalse(testGetUserCredentials().get(entity).getReaders().isEmpty(),
      "CredentialMap should now readable");
    assertNull(testGetUserCredentials().get(entity).getPassword(),
      "Password should be not readable be a reader");
    response = credentialRestApi.removeCredentialEntity(entity);
    assertEquals(Status.FORBIDDEN, response.getStatusInfo().toEnum(),
      "Deletion should be forbidden, because user2 is only reader");

    // Switch to user
    when(mockAuthenticationService.getPrincipal()).thenReturn("user");
    response = credentialRestApi.removeCredentialEntity(entity);
    assertEquals(Status.OK, response.getStatusInfo().toEnum(),
      "Deletion should be allowed, because user is owner");
  }

  @Test
  void testCredentialsAPIWithRolesSpecialCasesOwner() {
    String entity = "entityname";
    AuthenticationService mockAuthenticationService = mock(AuthenticationService.class);
    credentialRestApi = new CredentialRestApi(credentials, mockAuthenticationService);
    Set<String> roles = new HashSet<>();
    roles.add("group1");
    roles.add("group2");
    when(mockAuthenticationService.getPrincipal()).thenReturn("user");
    when(mockAuthenticationService.getAssociatedRoles()).thenReturn(roles);

    // Give your own group owner rights
    String requestData1 =
        "{\"entity\":\"entityname\",\"username\":\"myuser\",\"password\":\"mypass\",\"readers\":[\"group1\"], \"owners\":[\"group1\"]}";
    Response response = credentialRestApi.putCredentials(requestData1);
    assertEquals(Status.OK, response.getStatusInfo().toEnum());
    assertEquals(1, testGetUserCredentials().get(entity).getOwners().size(), "CredentialMap should contain group1");
    assertTrue(testGetUserCredentials().get(entity).getOwners().contains("group1"), "CredentialMap should contain group1 as owner");

    // Try to lose access to the credentials
    String requestData2 =
        "{\"entity\":\"entityname\",\"username\":\"myuser\",\"password\":\"mypass\",\"readers\":[\"group1\"], \"owners\":[\"group5\"]}";
    response = credentialRestApi.putCredentials(requestData2);
    assertEquals(Status.OK, response.getStatusInfo().toEnum());
    assertEquals(2, testGetUserCredentials().get(entity).getOwners().size(), "CredentialMap should contain group5 and user as owner");
    assertTrue(testGetUserCredentials().get(entity).getOwners().contains("group5"), "CredentialMap should contain group5 as owner");
    assertTrue(testGetUserCredentials().get(entity).getOwners().contains("user"),
      "CredentialMap should contain group5 as owner");
    response = credentialRestApi.removeCredentialEntity(entity);
    assertEquals(Status.OK, response.getStatusInfo().toEnum(),
      "Deletion should be allowed, because user is owner");
  }
}
