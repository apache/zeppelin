package org.apache.zeppelin.kotlin;

import kotlin.script.experimental.jvmhost.repl.JvmReplCompiler;
import kotlin.script.experimental.jvmhost.repl.JvmReplEvaluator;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.jetbrains.kotlin.cli.common.repl.AggregatedReplStageState;
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine;
import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult;
import org.jetbrains.kotlin.cli.common.repl.ReplEvalResult;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class KotlinRepl {
  private JvmReplCompiler compiler;
  private JvmReplEvaluator evaluator;

  private AggregatedReplStageState<?, ?> state;

  private AtomicInteger counter;

  @SuppressWarnings("unchecked")
  public KotlinRepl(JvmReplCompiler compiler,
                    JvmReplEvaluator evaluator) {
    this.compiler = compiler;
    this.evaluator = evaluator;
    ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();
    state = new AggregatedReplStageState(
        compiler.createState(stateLock),
        evaluator.createState(stateLock),
        stateLock);

    counter = new AtomicInteger(0);
  }

  public InterpreterResult eval(String code) {
    ReplCompileResult compileResult = compiler.compile(state,
        new ReplCodeLine(counter.getAndIncrement(), 0, code));

    if (compileResult instanceof ReplCompileResult.Incomplete) {
      return new InterpreterResult(InterpreterResult.Code.INCOMPLETE);
    }
    if (compileResult instanceof ReplCompileResult.Error) {
      ReplCompileResult.Error e = (ReplCompileResult.Error) compileResult;
      return new InterpreterResult(InterpreterResult.Code.ERROR, e.toString());
    }
    if (!(compileResult instanceof ReplCompileResult.CompiledClasses)) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
          "unknown compilation result:" + compileResult.toString());
    }

    ReplCompileResult.CompiledClasses classes =
        (ReplCompileResult.CompiledClasses) compileResult;

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
}
