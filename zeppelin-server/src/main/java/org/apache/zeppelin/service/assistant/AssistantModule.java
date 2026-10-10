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

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.service.NotebookService;

public class AssistantModule implements AutoCloseable {

  public final Assistant assistant;

  public final OpenAiChatModel model;

  public final ConversationRepository repository;

  private final ExecutorService assistantExecutor;

  public AssistantModule(
      boolean enabled,
      String endpoint,
      String apiKey,
      String modelName,
      String conversationDir,
      Notebook notebook,
      NotebookService notebookService,
      AuthorizationService authorizationService
  ) {
    if (!enabled || StringUtils.isBlank(apiKey)) {
      assistant = new DisabledAssistant();
      model = null;
      repository = null;
      assistantExecutor = null;
      return;
    }
    repository = new FileConversationRepository(new File(conversationDir));
    model = new OpenAiChatModel(endpoint, apiKey, modelName);
    assistantExecutor = new ThreadPoolExecutor(
        10, 10, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(10),
        new ThreadFactoryBuilder().setNameFormat("assistant-run-%d").setDaemon(true).build()
    );
    assistant = new AssistantImpl(
        notebook, model, notebookService, authorizationService, repository, assistantExecutor
    );
  }

  @Override
  public void close() {
    try {
      if (assistantExecutor != null) {
        assistantExecutor.shutdownNow();
      }
    } finally {
      if (model != null) {
        model.close();
      }
    }
  }
}
