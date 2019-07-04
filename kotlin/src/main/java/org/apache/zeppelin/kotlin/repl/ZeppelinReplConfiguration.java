package org.apache.zeppelin.kotlin.repl;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.diagnostics.Diagnostic;
import org.jetbrains.kotlin.scripting.repl.ReplExceptionReporter;
import org.jetbrains.kotlin.scripting.repl.ReplFromTerminal;
import org.jetbrains.kotlin.scripting.repl.configuration.ReplConfiguration;
import org.jetbrains.kotlin.scripting.repl.configuration.SnippetExecutionInterceptor;
import org.jetbrains.kotlin.scripting.repl.messages.DiagnosticMessageHolder;
import org.jetbrains.kotlin.scripting.repl.reader.ReplCommandReader;
import org.jetbrains.kotlin.scripting.repl.writer.ReplWriter;


// TODO(dk) implement everything
public class ZeppelinReplConfiguration implements ReplConfiguration {
  @Override
  public boolean getAllowIncompleteLines() {
    return false;
  }

  @NotNull
  @Override
  public ReplCommandReader getCommandReader() {
    return new ReplCommandReader() {
      @Nullable
      @Override
      public String readLine(@NotNull ReplFromTerminal.WhatNextAfterOneLine next) {
        return "";
      }

      @Override
      public void flushHistory() { }
    };
  }

  @NotNull
  @Override
  public ReplExceptionReporter getExceptionReporter() {
    return ReplExceptionReporter.DoNothing;
  }

  @NotNull
  @Override
  public SnippetExecutionInterceptor getExecutionInterceptor() {
    return SnippetExecutionInterceptor.Plain;
  }

  @NotNull
  @Override
  public ReplWriter getWriter() {
    return new ReplWriter() {
      @Override
      public void printlnWelcomeMessage(@NotNull String s) {

      }

      @Override
      public void printlnHelpMessage(@NotNull String s) {

      }

      @Override
      public void outputCommandResult(@NotNull String s) {

      }

      @Override
      public void notifyReadLineStart() {

      }

      @Override
      public void notifyReadLineEnd() {

      }

      @Override
      public void notifyIncomplete() {

      }

      @Override
      public void notifyCommandSuccess() {

      }

      @Override
      public void outputCompileError(@NotNull String s) {

      }

      @Override
      public void outputRuntimeError(@NotNull String s) {

      }

      @Override
      public void sendInternalErrorReport(@NotNull String s) {

      }
    };
  }

  @NotNull
  @Override
  public DiagnosticMessageHolder createDiagnosticHolder() {
    return new DiagnosticMessageHolder() {
      @NotNull
      @Override
      public String renderMessage() {
        return "";
      }

      @Override
      public void report(@NotNull Diagnostic diagnostic,
                         @NotNull PsiFile psiFile,
                         @NotNull String s) { }
    };
  }
}
