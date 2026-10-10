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

package org.apache.zeppelin.service.assistant;

import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.rest.exception.NoteNotFoundException;
import org.apache.zeppelin.service.NotebookService;
import org.apache.zeppelin.user.AuthenticationInfo;

public class AssistantImpl implements Assistant {

  private static final Logger LOGGER = LoggerFactory.getLogger(AssistantImpl.class);
  private static final Gson GSON = new Gson();
  private static final int HISTORY_MESSAGE_LIMIT = 10;

  private static final String INSTRUCTIONS =
      "You are an AI assistant helping users work with a notebook in Apache Zeppelin.\n"
          + "Respond in the user's language, and keep your answers concise and clear.\n"
          + "Format your responses using Markdown.";

  private final ExecutorService assistantExecutor;

  private final Set<String> busyConversations = ConcurrentHashMap.newKeySet();

  private final Notebook notebook;
  private final ChatModel modelClient;
  private final ConversationRepository conversationRepository;
  private final AuthorizationService authorizationService;
  private final ToolExecutor toolExecutor;

  public AssistantImpl(
      Notebook notebook,
      ChatModel modelClient,
      NotebookService notebookService,
      AuthorizationService authorizationService,
      ConversationRepository conversationRepository,
      ExecutorService assistantExecutor
  ) {
    this.assistantExecutor = assistantExecutor;
    this.authorizationService = authorizationService;
    this.notebook = notebook;
    this.modelClient = modelClient;
    this.conversationRepository = conversationRepository;
    this.toolExecutor = new ToolExecutor(
        List.of(
            new ListParagraphsTool(notebookService)
        )
    );
  }

  @Override
  public List<Conversation> listConversations(
      String noteId,
      Set<String> userAndRoles
  ) throws IOException {
    if (!authorizationService.isReader(noteId, userAndRoles)) {
      throw new ForbiddenException();
    }

    return notebook.processNote(noteId, note -> {
      if (note == null) throw new NoteNotFoundException(noteId);
      return conversationRepository.findAll(noteId);
    });
  }

  @Override
  public Conversation createConversation(
      String noteId,
      String title,
      AuthenticationInfo authInfo,
      Set<String> userAndRoles
  ) throws IOException {
    if (!authorizationService.isReader(noteId, userAndRoles)) throw new ForbiddenException();
    if (AuthenticationInfo.isAnonymous(authInfo)) throw new ForbiddenException();

    return notebook.processNote(noteId, note -> {
      if (note == null) throw new NoteNotFoundException(noteId);
      var conversation = Conversation.create(noteId, title, authInfo.getUser());
      conversationRepository.create(conversation);
      return conversation;
    });
  }

  @Override
  public Conversation getConversation(
      String noteId,
      String conversationId,
      Set<String> userAndRoles
  ) throws IOException {
    if (!authorizationService.isReader(noteId, userAndRoles)) throw new ForbiddenException();

    return notebook.processNote(noteId, note -> {
      if (note == null) throw new NoteNotFoundException(noteId);
      return conversationRepository.find(noteId, conversationId)
          .orElseThrow(NotFoundException::new);
    });
  }

  @Override
  public Conversation updateTitle(
      String noteId,
      String conversationId,
      String title,
      String userId,
      Set<String> userAndRoles
  ) throws IOException {
    if (!busyConversations.add(conversationId)) {
      throw new ClientErrorException("", Response.Status.CONFLICT);
    }
    try {
      var conversation = getConversation(noteId, conversationId, userAndRoles);
      if (!conversation.isOwner(userId)) throw new ForbiddenException();
      return notebook.processNote(noteId, note -> {
        if (note == null) throw new NoteNotFoundException(noteId);
        conversationRepository.update(conversation, c -> c.setTitle(title));
        return conversation;
      });
    } finally {
      busyConversations.remove(conversationId);
    }
  }

  @Override
  public void deleteConversation(
      String noteId,
      String conversationId,
      String userId,
      Set<String> userAndRoles
  ) throws IOException {
    if (!busyConversations.add(conversationId)) {
      throw new ClientErrorException("", Response.Status.CONFLICT);
    }
    try {
      Conversation conversation = getConversation(noteId, conversationId, userAndRoles);
      if (!conversation.isOwner(userId)) throw new ForbiddenException();
      notebook.processNote(noteId, note -> {
        if (note == null) throw new NoteNotFoundException(noteId);
        conversationRepository.delete(noteId, conversationId);
        return null;
      });
    } finally {
      busyConversations.remove(conversationId);
    }
  }

  @Override
  public MessagePage listMessages(
      String noteId,
      String conversationId,
      String cursor,
      int limit,
      Set<String> userAndRoles
  ) throws IOException {
    if (limit <= 0) throw new BadRequestException();
    List<Message> messages = new ArrayList<>(
        getConversation(noteId, conversationId, userAndRoles).getMessages()
    );
    Collections.reverse(messages);

    int startInclusive = cursor == null ? 0 : IntStream.range(0, messages.size())
        .filter(i -> messages.get(i).getId().equals(cursor))
        .findFirst()
        .orElseThrow(NotFoundException::new) + 1;
    int pageSize = Math.min(limit, messages.size() - startInclusive);
    int endExclusive = startInclusive + pageSize;

    List<Message> page = messages.subList(startInclusive, endExclusive);

    boolean hasMore = endExclusive < messages.size();
    String nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
    return new MessagePage(page, nextCursor);
  }

  @Override
  public Future<?> sendMessage(
      String noteId,
      String conversationId,
      String userContent,
      AuthenticationInfo authInfo,
      Set<String> userAndRoles,
      AssistantEventListener sink
  ) {
    return assistantExecutor.submit(() -> runMessage(
        noteId, conversationId, userContent, authInfo, userAndRoles, sink)
    );
  }

  private void runMessage(
      String noteId,
      String conversationId,
      String userContent,
      AuthenticationInfo authInfo,
      Set<String> userAndRoles,
      AssistantEventListener sink
  ) {
    var runId = "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    boolean acquired = false;
    try {
      if (StringUtils.isBlank(userContent)) throw new BadRequestException();
      if (!busyConversations.add(conversationId)) {
        throw new ClientErrorException("", Response.Status.CONFLICT);
      }
      acquired = true;

      var conversation = getConversation(noteId, conversationId, userAndRoles);
      if (!conversation.isOwner(authInfo.getUser())) throw new ForbiddenException();

      notebook.processNote(noteId, note -> {
        if (note == null) throw new NoteNotFoundException(noteId);
        conversationRepository.update(
            conversation, c -> c.addMessage(Message.user(Message.id(), userContent))
        );
        return null;
      });

      sink.onEvent(
          AssistantEventType.RUN_STARTED,
          new AssistantEventPayload.RunStarted(runId, Instant.now().toString())
      );

      Map<String, Integer> tokens = new HashMap<>();
      tokens.put("input", 0);
      tokens.put("output", 0);

      runLoop(noteId, conversation, authInfo, userAndRoles, sink, tokens);

      sink.onEvent(
          AssistantEventType.RUN_COMPLETED,
          new AssistantEventPayload.RunCompleted(
              runId,
              new AssistantEventPayload.Usage(tokens.get("input"), tokens.get("output"))
          )
      );
    } catch (Exception e) {
      LOGGER.error("Error during Assistant run", e);
      sink.onEvent(
          AssistantEventType.RUN_FAILED,
          new AssistantEventPayload.RunFailed(runId, AssistantEventPayload.Error.of(e))
      );
    } finally {
      if (acquired) busyConversations.remove(conversationId);
    }
  }

  private void runLoop(
      String noteId,
      Conversation conversation,
      AuthenticationInfo authInfo,
      Set<String> userAndRoles,
      AssistantEventListener sink,
      Map<String, Integer> tokens
  ) throws IOException {
    for (int iteration = 0; iteration < 10; iteration++) {
      String assistantId = Message.id();
      var textBuffer = new StringBuilder();
      List<ToolCall> toolCalls = new ArrayList<>();

      modelClient.stream(
          INSTRUCTIONS,
          conversation.getRecentMessages(HISTORY_MESSAGE_LIMIT),
          toolExecutor.specs(),
          event -> {
            if (event instanceof AssistantEvent.TextDelta) {
              String delta = ((AssistantEvent.TextDelta) event).delta;
              textBuffer.append(delta);
              sink.onEvent(
                  AssistantEventType.MESSAGE_DELTA,
                  new AssistantEventPayload.MessageDelta(assistantId, delta)
              );
            } else if (event instanceof AssistantEvent.ToolCall) {
              AssistantEvent.ToolCall tc = (AssistantEvent.ToolCall) event;
              toolCalls.add(new ToolCall(tc.id, tc.name, tc.arguments));
            } else if (event instanceof AssistantEvent.Usage) {
              AssistantEvent.Usage u = (AssistantEvent.Usage) event;
              tokens.put("input", tokens.get("input") + u.inputTokens);
              tokens.put("output", tokens.get("output") + u.outputTokens);
            }
          }
      );

      if (!toolCalls.isEmpty()) {
        Message.Assistant assistantMsg = Message.assistant(assistantId, textBuffer.toString());
        toolCalls.forEach(assistantMsg::addToolCall);

        List<Message> turn = new ArrayList<>();
        turn.add(assistantMsg);

        if (textBuffer.length() > 0) {
          sink.onEvent(
              AssistantEventType.MESSAGE_DONE,
              new AssistantEventPayload.MessageDone(assistantId, textBuffer.toString())
          );
        }

        for (ToolCall tc : assistantMsg.getToolCalls()) {
          sink.onEvent(
              AssistantEventType.TOOL_CALL_STARTED,
              new AssistantEventPayload.ToolCallStarted(tc.getId(), tc.getName(), tc.getArguments())
          );
          ToolResult result = toolExecutor.callTool(
              noteId, tc.getName(), tc.getArguments(), authInfo, userAndRoles
          );
          tc.setResult(result);
          turn.add(Message.tool(Message.id(), tc.getId(), GSON.toJson(result)));
          sink.onEvent(
              AssistantEventType.TOOL_CALL_DONE,
              new AssistantEventPayload.ToolCallDone(tc.getId(), result)
          );
        }

        notebook.processNote(noteId, note -> {
          if (note == null) throw new NoteNotFoundException(noteId);
          conversationRepository.update(conversation, c -> turn.forEach(c::addMessage));
          return null;
        });
      } else {
        // No tool calls; finish the run.
        String assistantText = textBuffer.toString();
        notebook.processNote(noteId, note -> {
          if (note == null) throw new NoteNotFoundException(noteId);
          conversationRepository.update(
              conversation,
              c -> c.addMessage(Message.assistant(assistantId, assistantText))
          );
          return null;
        });
        sink.onEvent(
            AssistantEventType.MESSAGE_DONE,
            new AssistantEventPayload.MessageDone(assistantId, assistantText)
        );
        return;
      }
    }
    throw new IllegalStateException("Tool iteration limit exceeded");
  }

}
