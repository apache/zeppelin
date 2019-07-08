package org.apache.zeppelin.kotlin.conf.repl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.scripting.repl.writer.ReplWriter;

public class WriterStub implements ReplWriter {
  @Override
  public void notifyCommandSuccess() {

  }

  @Override
  public void notifyIncomplete() {

  }

  @Override
  public void notifyReadLineEnd() {

  }

  @Override
  public void notifyReadLineStart() {

  }

  @Override
  public void outputCommandResult(@NotNull String s) {

  }

  @Override
  public void outputCompileError(@NotNull String s) {

  }

  @Override
  public void outputRuntimeError(@NotNull String s) {

  }

  @Override
  public void printlnHelpMessage(@NotNull String s) {

  }

  @Override
  public void printlnWelcomeMessage(@NotNull String s) {

  }

  @Override
  public void sendInternalErrorReport(@NotNull String s) {

  }
}
