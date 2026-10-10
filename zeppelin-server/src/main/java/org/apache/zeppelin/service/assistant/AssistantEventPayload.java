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

import java.util.Map;
import jakarta.ws.rs.WebApplicationException;

public interface AssistantEventPayload {

  final class RunStarted implements AssistantEventPayload {
    final String runId;
    final String createdAt;

    RunStarted(String runId, String createdAt) {
      this.runId = runId;
      this.createdAt = createdAt;
    }
  }

  final class RunCompleted implements AssistantEventPayload {
    final String runId;
    final Usage usage;

    RunCompleted(String runId, Usage usage) {
      this.runId = runId;
      this.usage = usage;
    }
  }

  final class RunFailed implements AssistantEventPayload {
    final String runId;
    final Error error;

    RunFailed(String runId, Error error) {
      this.runId = runId;
      this.error = error;
    }
  }

  final class MessageDelta implements AssistantEventPayload {
    final String messageId;
    final String delta;

    MessageDelta(String messageId, String delta) {
      this.messageId = messageId;
      this.delta = delta;
    }
  }

  final class MessageDone implements AssistantEventPayload {
    final String messageId;
    final String content;

    MessageDone(String messageId, String content) {
      this.messageId = messageId;
      this.content = content;
    }
  }

  final class ToolCallStarted implements AssistantEventPayload {
    final String toolCallId;
    final String name;
    final Map<String, Object> arguments;

    ToolCallStarted(String toolCallId, String name, Map<String, Object> arguments) {
      this.toolCallId = toolCallId;
      this.name = name;
      this.arguments = arguments;
    }
  }

  final class ToolCallDone implements AssistantEventPayload {
    final String toolCallId;
    final ToolResult result;

    ToolCallDone(String toolCallId, ToolResult result) {
      this.toolCallId = toolCallId;
      this.result = result;
    }
  }

  final class Usage {
    final int inputTokens;
    final int outputTokens;

    Usage(int inputTokens, int outputTokens) {
      this.inputTokens = inputTokens;
      this.outputTokens = outputTokens;
    }
  }

  final class Error {
    final int status;

    private Error(int status) {
      this.status = status;
    }

    public static Error of(Exception e) {
      int status = e instanceof WebApplicationException
          ? ((WebApplicationException) e).getResponse().getStatus()
          : 500;
      return new Error(status);
    }
  }
}
