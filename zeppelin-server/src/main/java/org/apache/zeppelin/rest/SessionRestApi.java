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
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.common.SessionInfo;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.rest.exception.SessionNoteFoundException;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.service.SessionManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Rest api endpoint for ZSession operations.
 */
@Path("/session")
@Produces("application/json")
@Singleton
public class SessionRestApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(SessionRestApi.class);

  private final SessionManagerService sessionManagerService;

  @Inject
  public SessionRestApi(Notebook notebook, InterpreterSettingManager interpreterSettingManager,
      ZeppelinConfiguration zConf) {
    this.sessionManagerService =
        new SessionManagerService(notebook, interpreterSettingManager, zConf);
  }

  /**
   * List all sessions when interpreter is not provided, otherwise only list all the sessions
   * of this provided interpreter.
   *
   * @param interpreter
   * @return
   * @throws Exception
   */
  @GET
  public Response listSessions(@QueryParam("interpreter") String interpreter) throws Exception {
    if (StringUtils.isBlank(interpreter)) {
      LOGGER.info("List all sessions of all interpreters");
    } else {
      LOGGER.info("List all sessions for interpreter: {}", interpreter);
    }
    List<SessionInfo> sessionList = null;
    if (StringUtils.isBlank(interpreter)) {
      sessionList = sessionManagerService.listSessions();
    } else {
      sessionList = sessionManagerService.listSessions(interpreter);
    }
    return new JsonResponse<>(Response.Status.OK, sessionList).build();
  }

  /**
   * Create a session for this provided interpreter.
   *
   * @param interpreter
   * @return
   * @throws Exception
   */
  @POST
  public Response createSession(@QueryParam("interpreter") String interpreter) throws Exception {
    LOGGER.info("Create new session for interpreter: {}", interpreter);
    SessionInfo sessionInfo = sessionManagerService.createSession(interpreter);
    return new JsonResponse<>(Response.Status.OK, sessionInfo).build();
  }

  /**
   * Stop the session of the provided sessionId.
   *
   * @param sessionId
   * @return
   */
  @DELETE
  @Path("{sessionId}")
  public Response stopSession(@PathParam("sessionId") String sessionId) {
    LOGGER.info("Stop session: {}", sessionId);
    try {
      sessionManagerService.stopSession(sessionId);
      return new JsonResponse<>(Response.Status.OK, sessionId).build();
    } catch (Exception e) {
      return new JsonResponse<>(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage()).build();
    }
  }

  /**
   * Get session info for provided sessionId.
   */
  @GET
  @Path("{sessionId}")
  @ZeppelinApi
  public Response getSession(@PathParam("sessionId") String sessionId) throws Exception {
    SessionInfo sessionInfo = sessionManagerService.getSessionInfo(sessionId);
    if (sessionInfo == null) {
      throw new SessionNoteFoundException(sessionId);
    } else {
      return new JsonResponse<>(Response.Status.OK, "", sessionInfo).build();
    }
  }
}
