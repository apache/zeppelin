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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.rest.message.CredentialRequest;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.service.AuthenticationService;
import org.apache.zeppelin.user.Credentials;
import org.apache.zeppelin.user.UserCredentials;
import org.apache.zeppelin.user.UsernamePassword;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Credential Rest API. */
@Path("/credential")
@Produces("application/json")
@Singleton
public class CredentialRestApi extends AbstractRestApi {
  private static final Logger LOGGER = LoggerFactory.getLogger(CredentialRestApi.class);
  private final Credentials credentials;

  @Inject
  public CredentialRestApi(Credentials credentials, AuthenticationService authenticationService) {
    super(authenticationService);
    this.credentials = credentials;
  }

  /**
   * Put User Credentials REST API.
   *
   * @param message - JSON with entity, username, password.
   * @return JSON with status.OK
   */
  @PUT
  public Response putCredentials(String message) {
    CredentialRequest request = GSON.fromJson(message, CredentialRequest.class);
    if (StringUtils.isAnyBlank(request.getEntity(), request.getUsername(), request.getPassword())) {
      return new JsonResponse<>(Status.BAD_REQUEST).build();
    }

    String user = authenticationService.getPrincipal();
    LOGGER.info("Update credentials for user {} entity {}", user, request.getEntity());
    UserCredentials uc;
    try {
      uc = credentials.getUserCredentials(user);
      uc.putUsernamePassword(request.getEntity(), new UsernamePassword(request.getUsername(), request.getPassword()));
      credentials.putUserCredentials(user, uc);
      return new JsonResponse<>(Status.OK).build();
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
      return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Get User Credentials list REST API.
   *
   * @return JSON with status.OK
   */
  @GET
  public Response getCredentials() {
    String user = authenticationService.getPrincipal();
    LOGGER.info("getCredentials for user {} ", user);
    UserCredentials uc;
    try {
      uc = credentials.getUserCredentials(user);
      return new JsonResponse<>(Status.OK, uc).build();
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
      return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Remove User Credentials REST API.
   *
   * @return JSON with status.OK
   */
  @DELETE
  public Response removeCredentials() {
    String user = authenticationService.getPrincipal();
    LOGGER.info("removeCredentials for user {} ", user);
    UserCredentials uc;
    try {
      uc = credentials.removeUserCredentials(user);
      if (uc == null) {
        return new JsonResponse<>(Status.NOT_FOUND).build();
      }
      return new JsonResponse<>(Status.OK).build();
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
      return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Remove Entity of User Credential entity REST API.
   *
   * @param
   * @return JSON with status.OK
   */
  @DELETE
  @Path("{entity}")
  public Response removeCredentialEntity(@PathParam("entity") String entity) {
    String user = authenticationService.getPrincipal();
    LOGGER.info("removeCredentialEntity for user {} entity {}", user, entity);
    try {
      if (!credentials.removeCredentialEntity(user, entity)) {
        return new JsonResponse<>(Status.NOT_FOUND).build();
      }
      return new JsonResponse<>(Status.OK).build();
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
      return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR).build();
    }
  }
}
