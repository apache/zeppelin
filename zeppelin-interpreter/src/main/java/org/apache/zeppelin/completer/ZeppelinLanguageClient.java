/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.completer;

import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Very basic implementation of LanguageClient.
 */
public class ZeppelinLanguageClient implements LanguageClient {
  private static final Logger Logger = LoggerFactory.getLogger(LanguageClient.class);
  @Override
  public void telemetryEvent(Object object) {
    /* ignored */
  }

  @Override
  public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
    /* ignored */
  }

  @Override
  public void showMessage(MessageParams messageParams) {
    /* ignored */
  }

  @Override
  public CompletableFuture<MessageActionItem> showMessageRequest(
      ShowMessageRequestParams requestParams) {
    return null;
  }

  @Override
  public void logMessage(MessageParams message) {
    switch (message.getType()) {
      case Log:
        Logger.debug(message.getMessage());
        break;
      case Warning:
        Logger.warn(message.getMessage());
        break;
      case Error:
        Logger.error(message.getMessage());
        break;
      default:
        Logger.info(message.getMessage());
    }
  }
}
