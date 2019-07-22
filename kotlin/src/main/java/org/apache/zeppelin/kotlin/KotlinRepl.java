package org.apache.zeppelin.kotlin;

import org.jetbrains.kotlin.cli.common.repl.AggregatedReplStageState;
import org.jetbrains.kotlin.cli.common.repl.CompiledClassData;
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine;
import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult;
import org.jetbrains.kotlin.cli.common.repl.ReplEvalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import kotlin.script.experimental.jvmhost.repl.JvmReplCompiler;
import kotlin.script.experimental.jvmhost.repl.JvmReplEvaluator;
import org.apache.zeppelin.interpreter.InterpreterResult;

public class KotlinRepl {
  private static Logger logger = LoggerFactory.getLogger(KotlinRepl.class);

  private JvmReplCompiler compiler;
  private JvmReplEvaluator evaluator;
  private AggregatedReplStageState<?, ?> state;
  private AtomicInteger counter;
  private String outputDir;

  public KotlinRepl(JvmReplCompiler compiler,
                    JvmReplEvaluator evaluator) {
    this(compiler, evaluator, null);
  }

  @SuppressWarnings("unchecked")
  public KotlinRepl(JvmReplCompiler compiler,
                    JvmReplEvaluator evaluator,
                    String outputDir) {
    this.compiler = compiler;
    this.evaluator = evaluator;
    ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();
    state = new AggregatedReplStageState(
        compiler.createState(stateLock),
        evaluator.createState(stateLock),
        stateLock);

    counter = new AtomicInteger(0);

    this.outputDir = outputDir;
  }

  public InterpreterResult eval(String code) {
    ReplCompileResult compileResult = compiler.compile(state,
        new ReplCodeLine(counter.getAndIncrement(), 0, code));

    if (compileResult instanceof ReplCompileResult.Incomplete) {
      return new InterpreterResult(InterpreterResult.Code.INCOMPLETE);
    }
    if (compileResult instanceof ReplCompileResult.Error) {
      ReplCompileResult.Error e = (ReplCompileResult.Error) compileResult;
      return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage());
    }
    if (!(compileResult instanceof ReplCompileResult.CompiledClasses)) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
          "unknown compilation result:" + compileResult.toString());
    }

    ReplCompileResult.CompiledClasses classes =
        (ReplCompileResult.CompiledClasses) compileResult;
    writeClasses(classes);

    ReplEvalResult evalResult = evaluator.eval(state, classes, null, null);

    if (evalResult instanceof ReplEvalResult.Error) {
      ReplEvalResult.Error e = (ReplEvalResult.Error) evalResult;
      return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage());
    }
    if (evalResult instanceof ReplEvalResult.Incomplete) {
      return new InterpreterResult(InterpreterResult.Code.INCOMPLETE);
    }
    if (evalResult instanceof ReplEvalResult.HistoryMismatch) {
      ReplEvalResult.HistoryMismatch e = (ReplEvalResult.HistoryMismatch) evalResult;
      return new InterpreterResult(
          InterpreterResult.Code.ERROR, "history mismatch at " + e.getLineNo());
    }
    if (evalResult instanceof ReplEvalResult.UnitResult) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    }
    if (evalResult instanceof ReplEvalResult.ValueResult) {
      ReplEvalResult.ValueResult e = (ReplEvalResult.ValueResult) evalResult;
      Object value = e.getValue();
      String valueString = (value != null) ? value.toString() : "null";

      return new InterpreterResult(
          InterpreterResult.Code.SUCCESS, valueString + ": " + e.getType());
    }
    return new InterpreterResult(InterpreterResult.Code.ERROR,
        "unknown evaluation result: " + evalResult.toString());
  }

  private void writeClasses(ReplCompileResult.CompiledClasses classes) {
    if (outputDir == null) {
      return;
    }

    for (CompiledClassData compiledClass: classes.getClasses()) {
      String filePath = compiledClass.getPath();
      if (filePath.contains("/")) {
        continue;
      }
      String classWritePath = outputDir + File.separator + filePath;

      try (FileOutputStream fos = new FileOutputStream(classWritePath);
           OutputStream out = new BufferedOutputStream(fos)) {
        out.write(compiledClass.getBytes());
        out.flush();
      } catch (IOException e) {
        logger.error(e.getMessage());
      }
    }
  }
}
