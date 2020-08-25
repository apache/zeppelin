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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.rest.message.Session;
import org.apache.zeppelin.server.JsonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rest api endpoint for the ZSession.
 */
@Path("/session")
@Produces("application/json")
@Singleton
public class SessionRestApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(SessionRestApi.class);

  private Notebook notebook;
  private InterpreterSettingManager interpreterSettingManager;
  private SessionManager sessionManager;

  @Inject
  public SessionRestApi(Notebook notebook, InterpreterSettingManager interpreterSettingManager) {
    this.notebook = notebook;
    this.interpreterSettingManager = interpreterSettingManager;
    this.sessionManager = new SessionManager(interpreterSettingManager);
  }

  @GET
  @Path("/")
  public Response listSessions(@QueryParam("interpreter") String interpreter) {
    if (StringUtils.isBlank(interpreter)) {
      LOGGER.info("List all sessions");
    } else {
      LOGGER.info("List all sessions for interpreter: " + interpreter);
    }
    try {
      List<Session> sessionList = null;
      if (StringUtils.isBlank(interpreter)) {
        sessionList = sessionManager.getAllSessionStatus();
      } else {
        sessionList = sessionManager.getAllSessionStatus(interpreter);
      }
      return new JsonResponse<>(Response.Status.OK, sessionList).build();
    } catch (Exception e) {
      return new JsonResponse<>(Response.Status.INTERNAL_SERVER_ERROR,
              "Fail to list session", ExceptionUtils.getStackTrace(e)).build();
    }
  }

  @POST
  @Path("{interpreter}")
  public Response newSession(@PathParam("interpreter") String interpreter) {
    LOGGER.info("New session for interpreter: {}", interpreter);
    try {
      String sessionId = sessionManager.newSession(interpreter);
      LOGGER.info("Allocate new session id: " + sessionId);
      return new JsonResponse<>(Response.Status.OK, sessionId).build();
    } catch (Exception e) {
      return new JsonResponse<>(Response.Status.INTERNAL_SERVER_ERROR,
              "Fail to start session", ExceptionUtils.getStackTrace(e)).build();
    }
  }

  @DELETE
  @Path("{interpreter}/{sessionId}")
  public Response stopSession(@PathParam("interpreter") String interpreter,
                              @PathParam("sessionId") String sessionId) {
    LOGGER.info("Stop session: {} for interpreter: {}", sessionId, interpreter);
    try {
      sessionManager.removeSession(interpreter);
      notebook.getInterpreterSettingManager().get(interpreter).closeInterpreters(sessionId);
      return new JsonResponse<>(Response.Status.OK, sessionId).build();
    } catch (Exception e) {
      return new JsonResponse<>(Response.Status.INTERNAL_SERVER_ERROR,
              "Fail to stop session", ExceptionUtils.getStackTrace(e)).build();
    }
  }

  /**
   * Get a session info.
   */
  @GET
  @Path("{sessionId}")
  @ZeppelinApi
  public Response getSession(@PathParam("sessionId") String sessionId) {
    try {
      Session session = sessionManager.getSession(sessionId);
      if (session == null) {
        return new JsonResponse<>(Response.Status.NOT_FOUND).build();
      } else {
        return new JsonResponse<>(Response.Status.OK, "", session).build();
      }
    } catch (Throwable e) {
      LOGGER.error("Exception in SessionRestApi while getSession ", e);
      return new JsonResponse<>(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage(),
              ExceptionUtils.getStackTrace(e)).build();
    }
  }
}
