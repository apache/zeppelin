package org.apache.zeppelin.kotlin.conf.repl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.diagnostics.Diagnostic;
import org.jetbrains.kotlin.scripting.repl.ReplExceptionReporter;
import org.jetbrains.kotlin.scripting.repl.configuration.ReplConfiguration;
import org.jetbrains.kotlin.scripting.repl.configuration.SnippetExecutionInterceptor;
import org.jetbrains.kotlin.scripting.repl.messages.DiagnosticMessageHolder;
import org.jetbrains.kotlin.scripting.repl.reader.ReplCommandReader;
import org.jetbrains.kotlin.scripting.repl.writer.ReplWriter;

import java.io.OutputStream;


// TODO(dk) implement everything
public class ZeppelinReplConfiguration implements ReplConfiguration {

  private ReplCommandReader reader;
  private ReplWriter writer;

  public ZeppelinReplConfiguration() {
    reader = new ReaderStub();
    writer = new WriterStub();
  }

  public void setOutput(OutputStream out) {
    this.writer = new ZeppelinReplWriter(out);
  }

  @Override
  public boolean getAllowIncompleteLines() {
    return false;
  }

  @NotNull
  @Override
  public ReplCommandReader getCommandReader() {
    return reader;
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
    return writer;
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
                         @NotNull PsiFile psiFile, @NotNull String s) {

      }
    };
  }
}
