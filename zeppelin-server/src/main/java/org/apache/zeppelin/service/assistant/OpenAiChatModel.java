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
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseStreamEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/**
 * {@link ChatModel} backed by the official {@code com.openai:openai-java} SDK,
 */
public class OpenAiChatModel implements ChatModel, AutoCloseable {

  private static final Gson GSON = new Gson();

  private final String baseUrl;
  private final String apiKey;
  private final String model;
  private OpenAIClient cachedClient;
  private boolean closed;

  public OpenAiChatModel(String baseUrl, String apiKey, String model) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.model = model;
  }

  private synchronized OpenAIClient client() {
    if (closed) throw new CancellationException();
    if (cachedClient == null) {
      cachedClient = OpenAIOkHttpClient.builder()
          .baseUrl(baseUrl)
          .apiKey(apiKey)
          .timeout(Duration.ofMinutes(1))
          .build();
    }
    return cachedClient;
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    if (cachedClient != null) {
      cachedClient.close();
    }
  }

  @Override
  public void stream(
      String instruction,
      List<Message> history,
      List<ToolSpec> tools,
      Consumer<AssistantEvent> consumer
  ) {
    List<ResponseInputItem> input = new ArrayList<>();
    for (Message m : history) {
      addHistoryItems(input, m);
    }

    ResponseCreateParams.Builder params = ResponseCreateParams.builder()
        .model(model)
        .instructions(instruction)
        .store(false)
        .inputOfResponse(input);

    for (ToolSpec t : tools) {
      params.addTool(FunctionTool.builder()
          .name(t.name)
          .description(t.description)
          .parameters(toParameters(t.parameters))
          .strict(false)
          .build());
    }

    boolean[] completed = {false};
    try (
        StreamResponse<ResponseStreamEvent> stream = client()
            .responses()
            .createStreaming(params.build())
    ) {
      stream.stream().forEach(event -> {
        if (event.completed().isPresent()) completed[0] = true;
        handleEvent(event, consumer);
      });
    }
    // A stream that ends without a completed response
    if (!completed[0]) {
      throw new IllegalStateException();
    }
  }

  private static FunctionTool.Parameters toParameters(Map<String, Object> schema) {
    FunctionTool.Parameters.Builder builder = FunctionTool.Parameters.builder();
    for (Map.Entry<String, Object> e : schema.entrySet()) {
      builder.putAdditionalProperty(e.getKey(), JsonValue.from(e.getValue()));
    }
    return builder.build();
  }

  // TODO(seung-00) replace with pattern matching (Java 16+)
  private void addHistoryItems(List<ResponseInputItem> input, Message m) {
    if (m instanceof Message.User) {
      String content = ((Message.User) m).getContent();
      if (content != null) {
        input.add(easyMessage(EasyInputMessage.Role.USER, content));
      }
    } else if (m instanceof Message.Assistant) {
      Message.Assistant assistant = (Message.Assistant) m;
      if (assistant.getContent() != null && !assistant.getContent().isEmpty()) {
        input.add(easyMessage(EasyInputMessage.Role.ASSISTANT, assistant.getContent()));
      }
      for (ToolCall tc : assistant.getToolCalls()) {
        input.add(ResponseInputItem.ofFunctionCall(ResponseFunctionToolCall.builder()
            .callId(tc.getId())
            .name(tc.getName())
            .arguments(GSON.toJson(tc.getArguments()))
            .build()));
      }
    } else if (m instanceof Message.Tool) {
      Message.Tool tool = (Message.Tool) m;
      input.add(ResponseInputItem.ofFunctionCallOutput(
              ResponseInputItem.FunctionCallOutput.builder()
                  .callId(tool.getToolCallId())
                  .output(tool.getContent() != null ? tool.getContent() : "")
                  .build()
          )
      );
    }
  }

  private static ResponseInputItem easyMessage(EasyInputMessage.Role role, String content) {
    return ResponseInputItem.ofEasyInputMessage(
        EasyInputMessage.builder().role(role).content(content).build());
  }

  private void handleEvent(
      ResponseStreamEvent event,
      Consumer<AssistantEvent> consumer
  ) {
    // A failure event ends the stream without a completion; surface it instead of silently
    // saving an empty/partial reply and reporting success.
    event.error().ifPresent(e -> {
      throw new IllegalStateException("OpenAI stream error: " + e.message());
    });
    event.failed().ifPresent(f -> {
      throw new IllegalStateException("OpenAI response failed");
    });
    event.incomplete().ifPresent(i -> {
      throw new IllegalStateException("OpenAI response incomplete");
    });

    event.outputTextDelta().ifPresent(
        d -> consumer.accept(new AssistantEvent.TextDelta(d.delta())));

    // A refusal is a real reply the user must see; stream it as text so it is delivered and saved.
    event.refusalDelta().ifPresent(
        r -> consumer.accept(new AssistantEvent.TextDelta(r.delta())));

    event.outputItemDone().flatMap(done -> done.item().functionCall())
        .ifPresent(call ->
            consumer.accept(
                new AssistantEvent.ToolCall(call.callId(), call.name(), call.arguments())
            ));

    event.completed().flatMap(done -> done.response().usage())
        .ifPresent(u -> consumer.accept(
            new AssistantEvent.Usage((int) u.inputTokens(), (int) u.outputTokens())
        ));
  }
}
