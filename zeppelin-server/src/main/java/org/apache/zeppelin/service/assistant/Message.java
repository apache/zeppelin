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

import com.google.gson.annotations.SerializedName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class Message {

  public enum Role {
    @SerializedName("system") SYSTEM,
    @SerializedName("user") USER,
    @SerializedName("assistant") ASSISTANT,
    @SerializedName("tool") TOOL;

    public static Role fromValue(String value) {
      for (Role r : values()) {
        if (r.name().equalsIgnoreCase(value)) {
          return r;
        }
      }
      throw new IllegalArgumentException();
    }
  }

  private String id;
  private String createdAt;

  private Message() {}

  Message(String id, String createdAt) {
    this.id = id;
    this.createdAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public String getCreatedAt() { return createdAt; }

  public abstract Role getRole();

  public static String id() {
    return "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }

  public static Message.User user(String id, String content) {
    return new Message.User(id, content, Instant.now().toString());
  }

  public static Message.Assistant assistant(String id, String content) {
    return new Message.Assistant(id, content, Instant.now().toString());
  }

  public static Message.Tool tool(String id, String toolCallId, String content) {
    return new Message.Tool(id, toolCallId, content, Instant.now().toString());
  }

  public static class User extends Message {
    private Role role = Role.USER;
    private String content;

    private User() {}

    User(String id, String content, String createdAt) {
      super(id, createdAt);
      this.content = content;
    }

    @Override
    public Role getRole() {
      return role;
    }

    public String getContent() {
      return content;
    }
  }

  public static class Assistant extends Message {
    private Role role = Role.ASSISTANT;
    private String content;
    private List<ToolCall> toolCalls = new ArrayList<>();

    private Assistant() {}

    Assistant(String id, String content, String createdAt) {
      super(id, createdAt);
      this.content = content;
    }

    @Override
    public Role getRole() {
      return role;
    }

    public String getContent() {
      return content;
    }

    public List<ToolCall> getToolCalls() {
      return toolCalls;
    }

    public void addToolCall(ToolCall toolCall) {
      toolCalls.add(toolCall);
    }
  }

  public static class Tool extends Message {
    private Role role = Role.TOOL;
    private String toolCallId;
    private String content;

    private Tool() {}

    Tool(String id, String toolCallId, String content, String createdAt) {
      super(id, createdAt);
      this.toolCallId = toolCallId;
      this.content = content;
    }

    @Override
    public Role getRole() {
      return role;
    }

    public String getToolCallId() {
      return toolCallId;
    }

    public String getContent() {
      return content;
    }
  }
}
