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

package org.apache.zeppelin.rest.message;

import java.util.List;
import org.apache.zeppelin.service.assistant.Conversation;
import org.apache.zeppelin.service.assistant.Message;

public final class ConversationResponse {
  private final String id;
  private final String noteId;
  private final String ownerId;
  private final String title;
  private final String createdAt;
  private final String updatedAt;
  private final boolean canSendMessage;
  private final List<Message> messages;

  private ConversationResponse(Conversation conversation, String userId) {
    this.id = conversation.getId();
    this.noteId = conversation.getNoteId();
    this.ownerId = conversation.getOwnerId();
    this.title = conversation.getTitle();
    this.createdAt = conversation.getCreatedAt();
    this.updatedAt = conversation.getUpdatedAt();
    this.canSendMessage = conversation.isOwner(userId);
    this.messages = List.copyOf(conversation.getMessages());
  }

  public static ConversationResponse of(Conversation conversation, String userId) {
    return new ConversationResponse(conversation, userId);
  }
}
