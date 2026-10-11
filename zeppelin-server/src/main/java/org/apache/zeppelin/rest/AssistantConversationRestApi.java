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

import com.google.gson.JsonSyntaxException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.rest.message.ConversationRequest;
import org.apache.zeppelin.rest.message.ConversationMetadata;
import org.apache.zeppelin.rest.message.ConversationResponse;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.service.AuthenticationService;
import org.apache.zeppelin.service.assistant.Assistant;

@Path("/notes/{noteId}/conversations")
@Produces("application/json")
@Singleton
public class AssistantConversationRestApi extends AbstractRestApi {

  private final Assistant assistant;

  @Inject
  public AssistantConversationRestApi(
      AuthenticationService authenticationService,
      Assistant assistant
  ) {
    super(authenticationService);
    this.assistant = assistant;
  }

  private static ConversationRequest parseBody(String body) {
    try {
      return GSON.fromJson(body, ConversationRequest.class);
    } catch (JsonSyntaxException e) {
      throw new BadRequestException();
    }
  }

  @GET
  @ZeppelinApi
  public Response list(@PathParam("noteId") String noteId) throws IOException {
    var context = getServiceContext();
    return new JsonResponse<>(
        Response.Status.OK,
        "",
        assistant.listConversations(noteId, context.getUserAndRoles()).stream()
            .map(c -> ConversationMetadata.of(c, context.getAutheInfo().getUser()))
            .collect(Collectors.toList())
    ).build();
  }

  @GET
  @Path("/{conversationId}")
  @ZeppelinApi
  public Response get(
      @PathParam("noteId") String noteId,
      @PathParam("conversationId") String conversationId
  ) throws IOException {
    var context = getServiceContext();
    var conversation = assistant.getConversation(
        noteId,
        conversationId,
        context.getUserAndRoles()
    );
    return new JsonResponse<>(
        Response.Status.OK,
        "",
        ConversationResponse.of(conversation, context.getAutheInfo().getUser())
    ).build();
  }

  @POST
  @ZeppelinApi
  public Response create(
      @PathParam("noteId") String noteId,
      String body
  ) throws IOException {
    String title = Optional.ofNullable(parseBody(body))
        .map(ConversationRequest::getTitle)
        .orElse(null);
    var context = getServiceContext();
    var conversation = assistant.createConversation(
        noteId, title, context.getAutheInfo(), context.getUserAndRoles()
    );
    return new JsonResponse<>(
        Response.Status.CREATED,
        "",
        ConversationMetadata.of(conversation, context.getAutheInfo().getUser())
    ).build();
  }

  @PATCH
  @Path("/{conversationId}")
  @ZeppelinApi
  public Response updateTitle(
      @PathParam("noteId") String noteId,
      @PathParam("conversationId") String conversationId,
      String body
  ) throws IOException {
    String title = Optional.ofNullable(parseBody(body))
        .map(ConversationRequest::getTitle)
        .filter(t -> !t.isBlank())
        .orElseThrow(() -> new BadRequestException("title is required"));
    var context = getServiceContext();
    var conversation = assistant.updateTitle(
        noteId, conversationId, title, context.getAutheInfo().getUser(), context.getUserAndRoles());
    return new JsonResponse<>(
        Response.Status.OK,
        "",
        ConversationMetadata.of(conversation, context.getAutheInfo().getUser())
    ).build();
  }

  @DELETE
  @Path("/{conversationId}")
  @ZeppelinApi
  public Response delete(
      @PathParam("noteId") String noteId,
      @PathParam("conversationId") String conversationId
  ) throws IOException {
    var context = getServiceContext();
    assistant.deleteConversation(
        noteId, conversationId, context.getAutheInfo().getUser(), context.getUserAndRoles());
    return Response.noContent().build();
  }
}
