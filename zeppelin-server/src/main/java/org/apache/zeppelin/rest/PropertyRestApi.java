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

import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.user.properties.UserProperties;
import org.apache.zeppelin.utils.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Properties Rest API
 *
 */
@Path("/property")
@Produces("application/json")
public class PropertyRestApi {
  Logger logger = LoggerFactory.getLogger(PropertyRestApi.class);
  private UserProperties userProperties;
  private Gson gson = new Gson();
  File propertiesFile;

  @Context
  private HttpServletRequest servReq;

  public PropertyRestApi() {
  }

  public PropertyRestApi(UserProperties userProperties) {
    this.userProperties = userProperties;
  }

  /**
   * Put User Properties REST API
   * @param message - JSON with name, value.
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @PUT
  public Response putProperty(String message) throws IOException, IllegalArgumentException {
    Map<String, String> messageMap = gson.fromJson(message,
        new TypeToken<Map<String, String>>(){}.getType());
    String name = messageMap.get("name");
    String value = messageMap.get("value");

    if (Strings.isNullOrEmpty(name) || Strings.isNullOrEmpty(value) ) {
      return new JsonResponse(Status.BAD_REQUEST).build();
    }
    logger.info("Update properties for name {} value {}", name, value);
    String userName = SecurityUtils.getPrincipal();
    userProperties.put(userName, name, value);
    return new JsonResponse(Status.OK).build();
  }

  /**
   * Get User Properties list REST API
   * @param
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @GET
  public Response getProperties(String message) throws
  IOException, IllegalArgumentException {
    String userName = SecurityUtils.getPrincipal();
    Map<String, String> properties = userProperties.get(userName);
    return new JsonResponse(Status.OK, properties).build();
  }

  /**
   * Remove User Properties REST API
   * @param
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @DELETE
  public Response removeProperties(String message) throws
  IOException, IllegalArgumentException {
    String userName = SecurityUtils.getPrincipal();
    logger.info("removeProperties for user {} ", userName);
    if (userProperties.remove(userName) != null) {
      return new JsonResponse(Status.NOT_FOUND).build();
    }
    return new JsonResponse(Status.OK).build();
  }
  
  
  /**
   * Remove Entity of User Properties entity REST API
   * @param
   * @return JSON with status.OK
   * @throws IOException, IllegalArgumentException
   */
  @DELETE
  @Path("{name}")
  public Response removeProperty(@PathParam("name") String name) throws
          IOException, IllegalArgumentException {
    String userName = SecurityUtils.getPrincipal();
    logger.info("removeProperty for user {} name {}", userName, name);
    if (userProperties.remove(userName, name) == false) {
      return new JsonResponse(Status.NOT_FOUND).build();
    }
    return new JsonResponse(Status.OK).build();
  }
}
