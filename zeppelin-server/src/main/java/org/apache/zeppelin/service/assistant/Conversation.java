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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Conversation {

  // yy-MM-dd HH:mm, e.g. "26-10-02 14:30"
  private static final DateTimeFormatter TITLE_TIME = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm");

  private String id;
  private String noteId;
  private String ownerId;
  private String title;
  private String createdAt;
  private String updatedAt;
  private List<Message> messages;

  Conversation() {
    this.messages = new ArrayList<>();
  }

  public static Conversation create(String noteId, String title, String ownerId) {
    Conversation c = new Conversation();
    c.id = "conv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    c.noteId = noteId;
    c.ownerId = ownerId;
    c.title = title != null ? title : LocalDateTime.now().format(TITLE_TIME);
    c.createdAt = Instant.now().toString();
    c.updatedAt = c.createdAt;
    return c;
  }

  public String getId() { return id; }
  public String getNoteId() { return noteId; }
  public String getOwnerId() { return ownerId; }
  public String getTitle() { return title; }
  public String getCreatedAt() { return createdAt; }
  public String getUpdatedAt() { return updatedAt; }
  public List<Message> getMessages() { return messages; }

  public List<Message> getRecentMessages(int limit) {
    if (limit <= 0) throw new IllegalArgumentException();
    int start = Math.max(0, messages.size() - limit);
    // Keep tool results together with the assistant message that requested them.
    while (start > 0 && messages.get(start) instanceof Message.Tool) start--;
    return messages.subList(start, messages.size());
  }

  public boolean isOwner(String user) {
    return ownerId.equals(user);
  }

  public void setTitle(String title) {
    this.title = title;
    touch();
  }

  public void addMessage(Message message) {
    messages.add(message);
    updatedAt = Instant.now().toString();
  }

  public void touch() {
    updatedAt = Instant.now().toString();
  }
}
