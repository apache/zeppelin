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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConversationTest {

  @Test
  void validatesHistoryWindow() {
    var conversation = Conversation.create("noteId", "test", "holden");
    assertTrue(conversation.getRecentMessages(10).isEmpty());
    assertThrows(IllegalArgumentException.class, () -> conversation.getRecentMessages(0));
    assertThrows(IllegalArgumentException.class, () -> conversation.getRecentMessages(-1));
  }

  @Test
  void keepsToolCallsWithResults() {
    var conversation = Conversation.create("noteId", "test", "holden");
    conversation.addMessage(Message.user("old", "old"));
    var assistant = Message.assistant("assistant", "");
    assistant.addToolCall(new ToolCall("call_1", "list_paragraphs", Map.of()));
    conversation.addMessage(assistant);
    var result = Message.tool("tool", "call_1", "[]");
    conversation.addMessage(result);

    assertEquals(List.of(assistant, result), conversation.getRecentMessages(1));
    assertEquals(3, conversation.getMessages().size());
  }
}
