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

import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.eclipse.lsp4j.jsonrpc.Launcher.createLauncher;

/**
 * Class that can connect to LSP Server and use it for autocomplete.
 */
public class LSPUtils {
  private static final Logger Logger = LoggerFactory.getLogger(LSPUtils.class);

  // Zeppelin don't support file versions, so we can pass any version
  private static final int ANY_VERSION = 42;
  // Every complete request are based on new connection, so we can pass any uri
  private static final String ANY_URI = "file_path";

  /**
   * Get completion list based on cursor position.
   *
   * @param buf statements
   * @param cursor cursor position in statements
   * @param host LSP Server's host
   * @param port LSP Server's port
   * @param langId language id
   *
   * @return list of possible completion.
   * Return empty list if there're nothing to return or exception occurred.
   */
  public static List<InterpreterCompletion> getLspServerCompletion(String buf, int cursor,
                                                                   String host, int port, String langId) {
    Logger.info("Trying to complete on {} with cursor {}", buf, cursor);

    List<InterpreterCompletion> list = Collections.emptyList();
    try (Socket socket = new Socket(host, port)) {
      LanguageServer server = connectToLanguageServer(socket);
      server.initialize(new InitializeParams());

      server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
          new TextDocumentItem(ANY_URI, langId, ANY_VERSION, buf)
      ));

      CompletableFuture future =
          server.getTextDocumentService().completion(getCompletionParams(buf, cursor));

      Either<List<CompletionItem>, CompletionList> either =
          (Either<List<CompletionItem>, CompletionList>) future.get(3, TimeUnit.SECONDS);
      list = eitherToCompletionList(either);
      Logger.info("Got completion list: {}", list);

      server.getTextDocumentService().didClose(
          new DidCloseTextDocumentParams(new TextDocumentIdentifier(ANY_URI)));
      server.exit();

    } catch (Exception e) {
      Logger.error(e.getMessage(), e);
    }

    return list;
  }

  private static int countLines(String str){
    return str.split("\r\n|\r|\n").length;
  }

  private static CompletionParams getCompletionParams(String buf, int cursor) {
    final int actualCursor = Math.min(cursor, buf.length());
    final String beforeCursor = buf.substring(0, actualCursor);
    final int line = countLines(beforeCursor) - 1;
    final int character = beforeCursor.length() - beforeCursor.lastIndexOf("\n") - 1;
    Logger.debug("Line: {}, character: {} from actual cursor: ", line, character, cursor);

    return new CompletionParams(
        new TextDocumentIdentifier(ANY_URI),
        new Position(line, character));
  }

  private static InterpreterCompletion castCompletionItemToInterpreterCompletion(
      CompletionItem item) {
    return new InterpreterCompletion(item.getLabel(), item.getLabel(), "");
  }

  private static List<InterpreterCompletion> castList(List<CompletionItem> items) {
    return items.stream()
        .map(LSPUtils::castCompletionItemToInterpreterCompletion)
        .collect(Collectors.toList());
  }

  private static List<InterpreterCompletion> eitherToCompletionList(
      Either<List<CompletionItem>, CompletionList> either) {
    if (either.isLeft()) {
      return castList(either.getLeft());
    } else {
      return castList(either.getRight().getItems());
    }
  }

  static LanguageServer connectToLanguageServer(Socket socket) throws IOException {
    Launcher<LanguageServer> launcher = createLauncher(
        new ZeppelinLanguageClient(),
        LanguageServer.class,
        socket.getInputStream(),
        socket.getOutputStream());
    launcher.startListening();
    return launcher.getRemoteProxy();
  }
}
