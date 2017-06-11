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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.util.Util;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 * Zeppelin root rest api endpoint.
 *
 * @since 0.3.4
 */
@Path("/")
public class ZeppelinRestApi {

  public ZeppelinRestApi() {
  }

  /**
   * Get the root endpoint Return always 200.
   *
   * @return 200 response
   */
  @GET
  public Response getRoot() {
    return Response.ok().build();
  }

  @GET
  @Path("version")
  @ZeppelinApi
  public Response getVersion() {
    Map<String, String> versionInfo = new HashMap<>();
    versionInfo.put("version", Util.getVersion());
    versionInfo.put("git-commit-id", Util.getGitCommitId());
    versionInfo.put("git-timestamp", Util.getGitTimestamp());

    return new JsonResponse<>(Response.Status.OK, "Zeppelin version", versionInfo).build();
  }

  /**
   * Set the log level for root logger
   * @param request
   * @param logLevel new log level for Rootlogger
   * @return
   */
  @PUT
  @Path("log/level/{logLevel}")
  public Response changeRootLogLevel(@Context HttpServletRequest request,
      @PathParam("logLevel") String logLevel) {
    Level level = Level.toLevel(logLevel);
    if (logLevel.toLowerCase().equalsIgnoreCase(level.toString().toLowerCase())) {
      Logger.getRootLogger().setLevel(level);
      return new JsonResponse<>(Response.Status.OK).build();
    } else {
      return new JsonResponse<>(Response.Status.NOT_ACCEPTABLE,
          "Please check LOG level specified. Valid values: DEBUG, ERROR, FATAL, "
              + "INFO, TRACE, WARN").build();
    }
  }
}
