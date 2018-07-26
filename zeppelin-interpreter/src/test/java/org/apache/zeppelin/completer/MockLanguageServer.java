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

package org.apache.zeppelin.completer;

import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MockLanguageServer implements LanguageServer {

  TextDocumentService textDocumentService = new TextDocumentService() {
    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion
        (CompletionParams position) {
      return CompletableFuture.supplyAsync(() -> Either.forLeft(
          Arrays.asList(new CompletionItem("print"), new CompletionItem
          ("pass"), new CompletionItem("private"))
      ));
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
      return null;
    }

    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
      return null;
    }

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
      return null;
    }

    @Override
    public CompletableFuture<List<? extends Location>> definition
        (TextDocumentPositionParams position) {
      return null;
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
      return null;
    }

    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight
        (TextDocumentPositionParams position) {
      return null;
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> documentSymbol
        (DocumentSymbolParams params) {
      return null;
    }

    @Override
    public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
      return null;
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
      return null;
    }

    @Override
    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
      return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
      return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting
        (DocumentRangeFormattingParams params) {
      return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting
        (DocumentOnTypeFormattingParams params) {
      return null;
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
      return null;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) { /* ignored */ }

    @Override
    public void didChange(DidChangeTextDocumentParams params) { /* ignored */ }

    @Override
    public void didClose(DidCloseTextDocumentParams params) { /* ignored */ }

    @Override
    public void didSave(DidSaveTextDocumentParams params) { /* ignored */ }
  };

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    return null;
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    return null;
  }

  @Override
  public void exit() {
    /* ignored */
  }

  @Override
  public TextDocumentService getTextDocumentService() {
    return textDocumentService;
  }

  @Override
  public WorkspaceService getWorkspaceService() {
    return null;
  }
}
