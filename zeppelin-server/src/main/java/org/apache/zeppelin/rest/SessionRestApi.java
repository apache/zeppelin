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
import org.apache.zeppelin.rest.exception.SessionNoteFoundException;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.service.SessionManagerService;
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
import java.util.List;

/**
 * Rest api endpoint for ZSession operations.
 */
@Path("/session")
@Produces("application/json")
@Singleton
public class SessionRestApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(SessionRestApi.class);

  private SessionManagerService sessionManagerService;

  @Inject
  public SessionRestApi(Notebook notebook, InterpreterSettingManager interpreterSettingManager) {
    this.sessionManagerService = new SessionManagerService(notebook, interpreterSettingManager);
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
  @Path("/")
  public Response listSessions(@QueryParam("interpreter") String interpreter) throws Exception {
    if (StringUtils.isBlank(interpreter)) {
      LOGGER.info("List all sessions of all interpreters");
    } else {
      LOGGER.info("List all sessions for interpreter: " + interpreter);
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
  @Path("/")
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
