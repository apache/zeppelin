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
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.Interpreter.RegisteredInterpreter;
import org.apache.zeppelin.rest.message.NewInterpreterSettingRequest;
import org.apache.zeppelin.rest.message.UpdateInterpreterSettingRequest;
import org.apache.zeppelin.server.JsonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * Interpreter Rest API
 *
 */
@Path("/interpreter")
@Produces("application/json")
public class InterpreterRestApi {
  Logger logger = LoggerFactory.getLogger(InterpreterRestApi.class);

  private InterpreterFactory interpreterFactory;

  Gson gson = new Gson();

  public InterpreterRestApi() {

  }

  public InterpreterRestApi(InterpreterFactory interpreterFactory) {
    this.interpreterFactory = interpreterFactory;
  }

  /**
   * List all interpreter settings
   * @return
   */
  @GET
  @Path("setting")
  public Response listSettings() {
    List<InterpreterSetting> interpreterSettings = null;
    interpreterSettings = interpreterFactory.get();
    return new JsonResponse(Status.OK, "", interpreterSettings).build();
  }

  /**
   * Add new interpreter setting
   * @param message
   * @return
   * @throws IOException
   * @throws InterpreterException
   */
  @POST
  @Path("setting")
  public Response newSettings(String message) throws InterpreterException, IOException {
    NewInterpreterSettingRequest request = gson.fromJson(message,
        NewInterpreterSettingRequest.class);
    Properties p = new Properties();
    p.putAll(request.getProperties());
    // Option is deprecated from API, always use remote = true
    InterpreterGroup interpreterGroup = interpreterFactory.add(request.getName(),
        request.getGroup(), new InterpreterOption(true), p);
    InterpreterSetting setting = interpreterFactory.get(interpreterGroup.getId());
    logger.info("new setting created with " + setting.id());
    return new JsonResponse(Status.CREATED, "", setting ).build();
  }

  @PUT
  @Path("setting/{settingId}")
  public Response updateSetting(String message, @PathParam("settingId") String settingId) {
    logger.info("Update interpreterSetting {}", settingId);

    try {
      UpdateInterpreterSettingRequest p = gson.fromJson(message,
          UpdateInterpreterSettingRequest.class);
      // Option is deprecated from API, always use remote = true
      interpreterFactory.setPropertyAndRestart(settingId,
          new InterpreterOption(true), p.getProperties());
    } catch (InterpreterException e) {
      return new JsonResponse(
          Status.NOT_FOUND, e.getMessage(), ExceptionUtils.getStackTrace(e)).build();
    } catch (IOException e) {
      return new JsonResponse(
          Status.INTERNAL_SERVER_ERROR, e.getMessage(), ExceptionUtils.getStackTrace(e)).build();
    }
    InterpreterSetting setting = interpreterFactory.get(settingId);
    if (setting == null) {
      return new JsonResponse(Status.NOT_FOUND, "", settingId).build();
    }
    return new JsonResponse(Status.OK, "", setting).build();
  }

  /**
   * Remove interpreter setting
   */
  @DELETE
  @Path("setting/{settingId}")
  public Response removeSetting(@PathParam("settingId") String settingId) throws IOException {
    logger.info("Remove interpreterSetting {}", settingId);
    interpreterFactory.remove(settingId);
    return new JsonResponse(Status.OK).build();
  }

  /**
   * Restart interpreter setting
   */
  @PUT
  @Path("setting/restart/{settingId}")
  public Response restartSetting(@PathParam("settingId") String settingId) {
    logger.info("Restart interpreterSetting {}", settingId);
    try {
      interpreterFactory.restart(settingId);
    } catch (InterpreterException e) {
      return new JsonResponse(
          Status.NOT_FOUND, e.getMessage(), ExceptionUtils.getStackTrace(e)).build();
    }
    InterpreterSetting setting = interpreterFactory.get(settingId);
    if (setting == null) {
      return new JsonResponse(Status.NOT_FOUND, "", settingId).build();
    }
    return new JsonResponse(Status.OK, "", setting).build();
  }

  /**
   * List all available interpreters by group
   */
  @GET
  public Response listInterpreter(String message) {
    Map<String, RegisteredInterpreter> m = Interpreter.registeredInterpreters;
    return new JsonResponse(Status.OK, "", m).build();
  }
}
