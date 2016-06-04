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
   * Update credentials for current user
   */
  @PUT
  public Response putCredentials(String message) throws IOException {
    Map<String, String> messageMap = gson.fromJson(message,
            new TypeToken<Map<String, String>>(){}.getType());
    String entity = messageMap.get("entity");
    String username = messageMap.get("username");
    String password = messageMap.get("password");
    String user = SecurityUtils.getPrincipal();
    logger.info("Update credentials for user {} entity {}", user, entity);
    UserCredentials uc = credentials.getUserCredentials(user);
    uc.putUsernamePassword(entity, new UsernamePassword(username, password));
    credentials.putUserCredentials(user, uc);
    return new JsonResponse(Status.OK, "", "").build();
  }

}
