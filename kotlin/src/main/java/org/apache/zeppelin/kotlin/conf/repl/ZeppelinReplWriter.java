package org.apache.zeppelin.kotlin.conf.repl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.scripting.repl.writer.ReplWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class ZeppelinReplWriter implements ReplWriter {

  private OutputStreamWriter out;

  public ZeppelinReplWriter(OutputStream out) {
    this.out = new OutputStreamWriter(out);
  }

  @Override
  public void notifyCommandSuccess() {
    try {
      out.write("SUCCESS");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void notifyIncomplete() {
    try {
      out.write("INCOMPLETE");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void notifyReadLineEnd() {
    try {
      out.write("READLINEEND");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void notifyReadLineStart() {
    try {
      out.write("READLINESTART");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void outputCommandResult(@NotNull String s) {
    try {
      out.write("RESULT " + s);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void outputCompileError(@NotNull String s) {
    try {
      out.write("COMPILE ERROR " + s);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void outputRuntimeError(@NotNull String s) {
    try {
      out.write("COMPILE ERROR " + s);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void printlnHelpMessage(@NotNull String s) {
    try {
      out.write("HELP " + s);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void printlnWelcomeMessage(@NotNull String s) {
    try {
      out.write("WELCOME " + s);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void sendInternalErrorReport(@NotNull String s) {
    try {
      out.write("INTERNAL ERROR " + s);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
