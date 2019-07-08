package org.apache.zeppelin.kotlin.conf.repl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.scripting.repl.ReplFromTerminal;
import org.jetbrains.kotlin.scripting.repl.reader.ReplCommandReader;

public class ReaderStub implements ReplCommandReader {
  @Nullable
  @Override
  public String readLine(@NotNull ReplFromTerminal.WhatNextAfterOneLine next) {
    return "";
  }

  @Override
  public void flushHistory() { }
}
