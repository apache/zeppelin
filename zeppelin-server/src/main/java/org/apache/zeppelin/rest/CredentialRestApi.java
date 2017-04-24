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

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.zeppelin.user.Credentials;
import org.apache.zeppelin.user.UserCredentials;
import org.apache.zeppelin.user.UsernamePassword;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.utils.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.util.Map;

/**
 * Credential Rest API
 *
 */
@Path("/credential")
@Produces("application/json")
public class CredentialRestApi {
  Logger logger = LoggerFactory.getLogger(CredentialRestApi.class);
  private Credentials credentials;
  private Gson gson = new Gson();

  @Context
  private HttpServletRequest servReq;

  public CredentialRestApi() {
  }

  public CredentialRestApi(Credentials credentials) {
    this.credentials = credentials;
  }

  /**
   * Put User Credentials REST API
   * @param message - JSON with entity, username, password.
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @PUT
  public Response putCredentials(String message) throws IOException, IllegalArgumentException {
    Map<String, String> messageMap = gson.fromJson(message,
      new TypeToken<Map<String, String>>(){}.getType());
    String entity = messageMap.get("entity");
    String username = messageMap.get("username");
    String password = messageMap.get("password");

    if (Strings.isNullOrEmpty(entity)
            || Strings.isNullOrEmpty(username) || Strings.isNullOrEmpty(password) ) {
      return new JsonResponse(Status.BAD_REQUEST).build();
    }

    String user = SecurityUtils.getPrincipal();
    logger.info("Update credentials for user {} entity {}", user, entity);
    UserCredentials uc = credentials.getUserCredentials(user);
    uc.putUsernamePassword(entity, new UsernamePassword(username, password));
    credentials.putUserCredentials(user, uc);
    return new JsonResponse(Status.OK).build();
  }

  /**
   * Get User Credentials list REST API
   * @param
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @GET
  public Response getCredentials(String message) throws
      IOException, IllegalArgumentException {
    String user = SecurityUtils.getPrincipal();
    logger.info("getCredentials credentials for user {} ", user);
    UserCredentials uc = credentials.getUserCredentials(user);
    return new JsonResponse(Status.OK, uc).build();
  }

 /**
  * Remove User Credentials REST API
  * @param
  * @return JSON with status.OK
  * @throws IOException, IllegalArgumentException
  */
  @DELETE
  public Response removeCredentials(String message) throws
      IOException, IllegalArgumentException {
    String user = SecurityUtils.getPrincipal();
    logger.info("removeCredentials credentials for user {} ", user);
    UserCredentials uc = credentials.removeUserCredentials(user);
    if (uc == null) {
      return new JsonResponse(Status.NOT_FOUND).build();
    }
    return new JsonResponse(Status.OK).build();
  }

  /**
   * Remove Entity of User Credential entity REST API
   * @param
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @DELETE
  @Path("{entity}")
  public Response removeCredentialEntity(@PathParam("entity") String entity) throws
          IOException, IllegalArgumentException {
    String user = SecurityUtils.getPrincipal();
    logger.info("removeCredentialEntity for user {} entity {}", user, entity);
    if (credentials.removeCredentialEntity(user, entity) == false) {
      return new JsonResponse(Status.NOT_FOUND).build();
    }
    return new JsonResponse(Status.OK).build();
  }
}
