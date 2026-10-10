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

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import org.apache.zeppelin.user.AuthenticationInfo;

public interface Assistant {

  List<Conversation> listConversations(
      String noteId,
      Set<String> userAndRoles
  ) throws IOException;

  Conversation createConversation(
      String noteId,
      String title,
      AuthenticationInfo authInfo,
      Set<String> userAndRoles
  ) throws IOException;

  Conversation getConversation(
      String noteId,
      String conversationId,
      Set<String> userAndRoles
  ) throws IOException;

  Conversation updateTitle(
      String noteId,
      String conversationId,
      String title,
      String userId,
      Set<String> userAndRoles
  ) throws IOException;

  void deleteConversation(
      String noteId,
      String conversationId,
      String userId,
      Set<String> userAndRoles
  ) throws IOException;

  MessagePage listMessages(
      String noteId,
      String conversationId,
      String cursor,
      int limit,
      Set<String> userAndRoles
  ) throws IOException;

  Future<?> sendMessage(
      String noteId,
      String conversationId,
      String userContent,
      AuthenticationInfo authInfo,
      Set<String> userAndRoles,
      AssistantEventListener sink
  );
}
