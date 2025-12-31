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

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonSyntaxException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.service.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.notebook.repo.NotebookRepoSync;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithSettings;
import org.apache.zeppelin.rest.message.NotebookRepoSettingsRequest;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.socket.NotebookServer;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 * NoteRepo rest API endpoint.
 *
 */
@Path("/notebook-repositories")
@Produces("application/json")
@Singleton
public class NotebookRepoRestApi extends AbstractRestApi {
  private static final Logger LOGGER = LoggerFactory.getLogger(NotebookRepoRestApi.class);

  private final NotebookRepoSync noteRepos;
  private final NotebookServer notebookWsServer;

  @Inject
  public NotebookRepoRestApi(NotebookRepoSync noteRepos, NotebookServer notebookWsServer,
      AuthenticationService authenticationService) {
    super(authenticationService);
    this.noteRepos = noteRepos;
    this.notebookWsServer = notebookWsServer;
    this.authenticationService = authenticationService;
  }

  /**
   * List all notebook repository.
   */
  @GET
  @ZeppelinApi
  public Response listRepoSettings() {
    AuthenticationInfo subject = new AuthenticationInfo(authenticationService.getPrincipal());
    LOGGER.info("Getting list of NoteRepo with Settings for user {}", subject.getUser());
    List<NotebookRepoWithSettings> settings = noteRepos.getNotebookRepos(subject);
    return new JsonResponse<>(Status.OK, "", settings).build();
  }

  /**
   * Reload notebook repository.
   */
  @GET
  @Path("reload")
  @ZeppelinApi
  public Response refreshRepo(){
    AuthenticationInfo subject = new AuthenticationInfo(authenticationService.getPrincipal());
    LOGGER.info("Reloading notebook repository for user {}", subject.getUser());
    try {
      notebookWsServer.broadcastReloadedNoteList(getServiceContext());
    } catch (IOException e) {
      LOGGER.error("Fail to refresh repo", e);
    }
    return new JsonResponse<>(Status.OK, "", null).build();
  }

  /**
   * Update a specific note repo.
   *
   * @param payload
   * @return
   */
  @PUT
  @ZeppelinApi
  public Response updateRepoSetting(String payload) {
    if (StringUtils.isBlank(payload)) {
      return new JsonResponse<>(Status.NOT_FOUND, "", Collections.emptyMap()).build();
    }
    AuthenticationInfo subject = new AuthenticationInfo(authenticationService.getPrincipal());
    NotebookRepoSettingsRequest newSettings;
    try {
      newSettings = GSON.fromJson(payload, NotebookRepoSettingsRequest.class);
    } catch (JsonSyntaxException e) {
      LOGGER.error("Cannot update notebook repo settings", e);
      return new JsonResponse<>(Status.NOT_ACCEPTABLE, "",
          ImmutableMap.of("error", "Invalid payload structure")).build();
    }

    if (NotebookRepoSettingsRequest.isEmpty(newSettings)) {
      LOGGER.error("Invalid property");
      return new JsonResponse<>(Status.NOT_ACCEPTABLE, "",
          ImmutableMap.of("error", "Invalid payload")).build();
    }
    LOGGER.info("User {} is going to change repo setting", subject.getUser());
    NotebookRepoWithSettings updatedSettings =
        noteRepos.updateNotebookRepo(newSettings.getName(), newSettings.getSettings(), subject);
    if (!updatedSettings.isEmpty()) {
      LOGGER.info("Broadcasting note list to user {}", subject.getUser());
      try {
        notebookWsServer.broadcastReloadedNoteList(getServiceContext());
      } catch (IOException e) {
        LOGGER.error("Fail to refresh repo.", e);
      }
    }
    return new JsonResponse<>(Status.OK, "", updatedSettings).build();
  }
}
