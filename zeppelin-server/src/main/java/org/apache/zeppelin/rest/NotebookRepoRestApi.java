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
import com.google.common.collect.Sets;
import com.google.gson.JsonSyntaxException;

import javax.inject.Inject;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.service.SecurityService;
import org.apache.zeppelin.service.ServiceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

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
public class NotebookRepoRestApi {
  private static final Logger LOG = LoggerFactory.getLogger(NotebookRepoRestApi.class);

  private NotebookRepoSync noteRepos;
  private NotebookServer notebookWsServer;
  private SecurityService securityService;

  @Inject
  public NotebookRepoRestApi(NotebookRepoSync noteRepos, NotebookServer notebookWsServer,
      SecurityService securityService) {
    this.noteRepos = noteRepos;
    this.notebookWsServer = notebookWsServer;
    this.securityService = securityService;
  }

  /**
   * List all notebook repository.
   */
  @GET
  @ZeppelinApi
  public Response listRepoSettings() {
    AuthenticationInfo subject = new AuthenticationInfo(securityService.getPrincipal());
    LOG.info("Getting list of NoteRepo with Settings for user {}", subject.getUser());
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
    AuthenticationInfo subject = new AuthenticationInfo(securityService.getPrincipal());
    LOG.info("Reloading notebook repository for user {}", subject.getUser());
    try {
      notebookWsServer.broadcastReloadedNoteList(null, getServiceContext());
    } catch (IOException e) {
      LOG.error("Fail to refresh repo", e);
    }
    return new JsonResponse<>(Status.OK, "", null).build();
  }

  private ServiceContext getServiceContext() {
    AuthenticationInfo authInfo = new AuthenticationInfo(securityService.getPrincipal());
    Set<String> userAndRoles = Sets.newHashSet();
    userAndRoles.add(securityService.getPrincipal());
    userAndRoles.addAll(securityService.getAssociatedRoles());
    return new ServiceContext(authInfo, userAndRoles);
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
    AuthenticationInfo subject = new AuthenticationInfo(securityService.getPrincipal());
    NotebookRepoSettingsRequest newSettings;
    try {
      newSettings = NotebookRepoSettingsRequest.fromJson(payload);
    } catch (JsonSyntaxException e) {
      LOG.error("Cannot update notebook repo settings", e);
      return new JsonResponse<>(Status.NOT_ACCEPTABLE, "",
          ImmutableMap.of("error", "Invalid payload structure")).build();
    }

    if (NotebookRepoSettingsRequest.isEmpty(newSettings)) {
      LOG.error("Invalid property");
      return new JsonResponse<>(Status.NOT_ACCEPTABLE, "",
          ImmutableMap.of("error", "Invalid payload")).build();
    }
    LOG.info("User {} is going to change repo setting", subject.getUser());
    NotebookRepoWithSettings updatedSettings =
        noteRepos.updateNotebookRepo(newSettings.name, newSettings.settings, subject);
    if (!updatedSettings.isEmpty()) {
      LOG.info("Broadcasting note list to user {}", subject.getUser());
      try {
        notebookWsServer.broadcastReloadedNoteList(null, getServiceContext());
      } catch (IOException e) {
        LOG.error("Fail to refresh repo.", e);
      }
    }
    return new JsonResponse<>(Status.OK, "", updatedSettings).build();
  }
}
