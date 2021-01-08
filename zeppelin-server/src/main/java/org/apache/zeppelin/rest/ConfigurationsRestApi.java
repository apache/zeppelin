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
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.service.ConfigurationService;
import org.apache.zeppelin.service.AuthenticationService;

/** Configurations Rest API Endpoint. */
@Path("/configurations")
@Produces("application/json")
@Singleton
public class ConfigurationsRestApi extends AbstractRestApi {

  private ConfigurationService configurationService;

  @Inject
  public ConfigurationsRestApi(
          AuthenticationService authenticationService, ConfigurationService configurationService) {
    super(authenticationService);
    this.configurationService = configurationService;
  }

  @GET
  @Path("all")
  @ZeppelinApi
  public Response getAll() {
    try {
      Map<String, String> properties =
          configurationService.getAllProperties(getServiceContext(), new RestServiceCallback<>());
      return new JsonResponse<>(Status.OK, "", properties).build();
    } catch (IOException e) {
      return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR, "Fail to get configuration", e).build();
    }
  }

  @GET
  @Path("prefix/{prefix}")
  @ZeppelinApi
  public Response getByPrefix(@PathParam("prefix") final String prefix) {
    try {
      Map<String, String> properties =
          configurationService.getPropertiesWithPrefix(
              prefix, getServiceContext(), new RestServiceCallback<>());
      return new JsonResponse<>(Status.OK, "", properties).build();
    } catch (IOException e) {
      return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR, "Fail to get configuration", e).build();
    }
  }
}
