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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.gson.Gson;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.rest.message.RestartInterpreterRequest;
import org.apache.zeppelin.utils.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.repository.RemoteRepository;

import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.dep.Repository;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.rest.message.NewInterpreterSettingRequest;
import org.apache.zeppelin.rest.message.UpdateInterpreterSettingRequest;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.socket.NotebookServer;

/**
 * Interpreter Rest API
 */
@Path("/interpreter")
@Produces("application/json")
public class InterpreterRestApi {
  private static final Logger logger = LoggerFactory.getLogger(InterpreterRestApi.class);

  private InterpreterSettingManager interpreterSettingManager;
  private NotebookServer notebookServer;

  Gson gson = new Gson();

  public InterpreterRestApi() {
  }

  public InterpreterRestApi(InterpreterSettingManager interpreterSettingManager,
      NotebookServer notebookWsServer) {
    this.interpreterSettingManager = interpreterSettingManager;
    this.notebookServer = notebookWsServer;
  }

  /**
   * List all interpreter settings
   */
  @GET
  @Path("setting")
  @ZeppelinApi
  public Response listSettings() {
    return new JsonResponse<>(Status.OK, "", interpreterSettingManager.get()).build();
  }

  /**
   * Get a setting
   */
  @GET
  @Path("setting/{settingId}")
  @ZeppelinApi
  public Response getSetting(@PathParam("settingId") String settingId) {
    try {
      InterpreterSetting setting = interpreterSettingManager.get(settingId);
      if (setting == null) {
        return new JsonResponse<>(Status.NOT_FOUND).build();
      } else {
        return new JsonResponse<>(Status.OK, "", setting).build();
      }
    } catch (NullPointerException e) {
      logger.error("Exception in InterpreterRestApi while creating ", e);
      return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR, e.getMessage(),
          ExceptionUtils.getStackTrace(e)).build();
    }
  }

  /**
   * Add new interpreter setting
   *
   * @param message NewInterpreterSettingRequest
   */
  @POST
  @Path("setting")
  @ZeppelinApi
  public Response newSettings(String message) {
    try {
      NewInterpreterSettingRequest request =
          gson.fromJson(message, NewInterpreterSettingRequest.class);
      if (request == null) {
        return new JsonResponse<>(Status.BAD_REQUEST).build();
      }
      Properties p = new Properties();
      p.putAll(request.getProperties());
      InterpreterSetting interpreterSetting = interpreterSettingManager
          .createNewSetting(request.getName(), request.getGroup(), request.getDependencies(),
              request.getOption(), p);
      logger.info("new setting created with {}", interpreterSetting.getId());
      return new JsonResponse<>(Status.OK, "", interpreterSetting).build();
    } catch (InterpreterException | IOException e) {
      logger.error("Exception in InterpreterRestApi while creating ", e);
      return new JsonResponse<>(Status.NOT_FOUND, e.getMessage(), ExceptionUtils.getStackTrace(e))
          .build();
    }
  }

  @PUT
  @Path("setting/{settingId}")
  @ZeppelinApi
  public Response updateSetting(String message, @PathParam("settingId") String settingId) {
    logger.info("Update interpreterSetting {}", settingId);

    try {
      UpdateInterpreterSettingRequest request =
          gson.fromJson(message, UpdateInterpreterSettingRequest.class);
      interpreterSettingManager
          .setPropertyAndRestart(settingId, request.getOption(), request.getProperties(),
              request.getDependencies());
    } catch (InterpreterException e) {
      logger.error("Exception in InterpreterRestApi while updateSetting ", e);
      return new JsonResponse<>(Status.NOT_FOUND, e.getMessage(), ExceptionUtils.getStackTrace(e))
          .build();
    } catch (IOException e) {
      logger.error("Exception in InterpreterRestApi while updateSetting ", e);
      return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR, e.getMessage(),
          ExceptionUtils.getStackTrace(e)).build();
    }
    InterpreterSetting setting = interpreterSettingManager.get(settingId);
    if (setting == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "", settingId).build();
    }
    return new JsonResponse<>(Status.OK, "", setting).build();
  }

  /**
   * Remove interpreter setting
   */
  @DELETE
  @Path("setting/{settingId}")
  @ZeppelinApi
  public Response removeSetting(@PathParam("settingId") String settingId) throws IOException {
    logger.info("Remove interpreterSetting {}", settingId);
    interpreterSettingManager.remove(settingId);
    return new JsonResponse(Status.OK).build();
  }

  /**
   * Restart interpreter setting
   */
  @PUT
  @Path("setting/restart/{settingId}")
  @ZeppelinApi
  public Response restartSetting(String message, @PathParam("settingId") String settingId) {
    logger.info("Restart interpreterSetting {}, msg={}", settingId, message);

    InterpreterSetting setting = interpreterSettingManager.get(settingId);
    try {
      RestartInterpreterRequest request = gson.fromJson(message, RestartInterpreterRequest.class);

      String noteId = request == null ? null : request.getNoteId();
      if (null == noteId) {
        interpreterSettingManager.close(setting);
      } else {
        interpreterSettingManager.restart(settingId, noteId, SecurityUtils.getPrincipal());
      }
      notebookServer.clearParagraphRuntimeInfo(setting);

    } catch (InterpreterException e) {
      logger.error("Exception in InterpreterRestApi while restartSetting ", e);
      return new JsonResponse<>(Status.NOT_FOUND, e.getMessage(), ExceptionUtils.getStackTrace(e))
          .build();
    }
    if (setting == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "", settingId).build();
    }
    return new JsonResponse<>(Status.OK, "", setting).build();
  }

  /**
   * List all available interpreters by group
   */
  @GET
  @ZeppelinApi
  public Response listInterpreter(String message) {
    Map<String, InterpreterSetting> m = interpreterSettingManager.getAvailableInterpreterSettings();
    return new JsonResponse<>(Status.OK, "", m).build();
  }

  /**
   * List of dependency resolving repositories
   */
  @GET
  @Path("repository")
  @ZeppelinApi
  public Response listRepositories() {
    List<RemoteRepository> interpreterRepositories = interpreterSettingManager.getRepositories();
    return new JsonResponse<>(Status.OK, "", interpreterRepositories).build();
  }

  /**
   * Add new repository
   *
   * @param message Repository
   */
  @POST
  @Path("repository")
  @ZeppelinApi
  public Response addRepository(String message) {
    try {
      Repository request = gson.fromJson(message, Repository.class);
      interpreterSettingManager.addRepository(request.getId(), request.getUrl(),
          request.isSnapshot(), request.getAuthentication(), request.getProxy());
      logger.info("New repository {} added", request.getId());
    } catch (Exception e) {
      logger.error("Exception in InterpreterRestApi while adding repository ", e);
      return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR, e.getMessage(),
          ExceptionUtils.getStackTrace(e)).build();
    }
    return new JsonResponse(Status.OK).build();
  }

  /**
   * get the metainfo property value
   */
  @GET
  @Path("getmetainfos/{settingId}")
  public Response getMetaInfo(@Context HttpServletRequest req,
                              @PathParam("settingId") String settingId) {
    String propName = req.getParameter("propName");
    if (propName == null) {
      return new JsonResponse<>(Status.BAD_REQUEST).build();
    }
    String propValue = null;
    InterpreterSetting interpreterSetting = interpreterSettingManager.get(settingId);
    Map<String, String> infos = interpreterSetting.getInfos();
    if (infos != null) {
      propValue = infos.get(propName);
    }
    Map<String, String> respMap = new HashMap<>();
    respMap.put(propName, propValue);
    logger.debug("Get meta info");
    logger.debug("Interpretersetting Id: {}, property Name:{}, property value: {}", settingId,
        propName, propValue);
    return new JsonResponse<>(Status.OK, respMap).build();
  }

  /**
   * Delete repository
   *
   * @param repoId ID of repository
   */
  @DELETE
  @Path("repository/{repoId}")
  @ZeppelinApi
  public Response removeRepository(@PathParam("repoId") String repoId) {
    logger.info("Remove repository {}", repoId);
    try {
      interpreterSettingManager.removeRepository(repoId);
    } catch (Exception e) {
      logger.error("Exception in InterpreterRestApi while removing repository ", e);
      return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR, e.getMessage(),
          ExceptionUtils.getStackTrace(e)).build();
    }
    return new JsonResponse(Status.OK).build();
  }
}
