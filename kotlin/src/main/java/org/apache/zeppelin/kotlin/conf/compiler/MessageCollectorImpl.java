package org.apache.zeppelin.kotlin.conf.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;

public class MessageCollectorImpl implements MessageCollector {
  @Override
  public void clear() {}

  @Override
  public boolean hasErrors() {
    return false;
  }

  @Override
  public void report(@NotNull CompilerMessageSeverity severity,
                     @NotNull String message,
                     @Nullable CompilerMessageLocation location) {
    if (severity.isError()) {
      String prefix = "";
      if (location != null) {
        prefix = formatLocationPrefix(location);
        throw new AssertionError(prefix + message);
      }
    }
  }

  private static String formatLocationPrefix(CompilerMessageLocation location) {
    return "(" + location.getPath() + ":" + location.getLine() + ":" + location.getColumn() + ") ";
  }
}
