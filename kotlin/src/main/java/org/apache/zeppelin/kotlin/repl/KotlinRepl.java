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

package org.apache.zeppelin.kotlin.repl;

import static org.apache.zeppelin.kotlin.reflect.KotlinReflectUtil.shorten;
import org.jetbrains.kotlin.cli.common.repl.AggregatedReplStageState;
import org.jetbrains.kotlin.cli.common.repl.InvokeWrapper;
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine;
import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult;
import org.jetbrains.kotlin.cli.common.repl.ReplEvalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import kotlin.jvm.functions.Function0;
import kotlin.script.experimental.jvmhost.repl.JvmReplCompiler;
import kotlin.script.experimental.jvmhost.repl.JvmReplEvaluator;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.kotlin.reflect.ContextUpdater;
import org.apache.zeppelin.kotlin.reflect.KotlinFunctionInfo;
import org.apache.zeppelin.kotlin.reflect.KotlinVariableInfo;
import org.apache.zeppelin.kotlin.repl.building.KotlinReplProperties;
import org.apache.zeppelin.kotlin.repl.building.ReplBuilding;

/**
 * Read-evaluate-print loop for Kotlin code.
 * Each code snippet is compiled into Line_N class and evaluated.
 *
 * Outside variables and functions can be bound to REPL
 * by inheriting KotlinReceiver class and passing it to REPL properties on creation.
 * After that, all fields and methods of receiver are seen inside the snippet scope
 * as if the code was run in Kotlin's `with` block.
 *
 * By default, KotlinReceiver has KotlinContext bound by the name `kc`.
 * It can be used to show user-defined variables and functions
 * and setting invokeWrapper to add effects to snippet evaluation.
 */
public class KotlinRepl {
  private static Logger logger = LoggerFactory.getLogger(KotlinRepl.class);

  private JvmReplCompiler compiler;
  private JvmReplEvaluator evaluator;
  private AggregatedReplStageState<?, ?> state;
  private AtomicInteger counter;
  private ClassWriter writer;
  private KotlinContext ctx;
  private InvokeWrapper wrapper;
  private int maxResult;
  private ContextUpdater contextUpdater;
  boolean shortenTypes;

  private KotlinRepl() { }

  @SuppressWarnings("unchecked")
  public KotlinRepl(KotlinReplProperties properties) {
    compiler = ReplBuilding.buildCompiler(properties);
    evaluator = ReplBuilding.buildEvaluator(properties);
    ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();
    state = new AggregatedReplStageState(
        compiler.createState(stateLock),
        evaluator.createState(stateLock),
        stateLock);
    counter = new AtomicInteger(0);

    writer = new ClassWriter(properties.getOutputDir());
    maxResult = properties.getMaxResult();
    shortenTypes = properties.getShortenTypes();

    ctx = new KotlinContext();
    properties.getReceiver().kc = ctx;

    contextUpdater = new ContextUpdater(
        state, ctx.vars, ctx.functions);

    for (String line: properties.getCodeOnLoad()) {
      eval(line);
    }
  }

  public List<KotlinVariableInfo> getVariables() {
    return ctx.getVars();
  }

  public List<KotlinFunctionInfo> getFunctions() {
    return ctx.getFunctions();
  }

  public KotlinContext getKotlinContext() {
    return ctx;
  }

  /**
   * Evaluates code snippet and returns interpreter result.
   * REPL evaluation consists of:
   * - Compiling code in JvmReplCompiler
   * - Writing compiled classes to disk
   * - Evaluating compiled classes inside InvokeWrapper
   * - Updating list of user-defined functions and variables
   * - Formatting result
   * @param code Kotlin code to execute
   * @return result of interpretation
   */
  public InterpreterResult eval(String code) {
    ReplCompileResult compileResult = compiler.compile(state,
        new ReplCodeLine(counter.getAndIncrement(), 0, code));

    Optional<InterpreterResult> compileError = checkCompileError(compileResult);
    if (compileError.isPresent()) {
      return compileError.get();
    }

    ReplCompileResult.CompiledClasses classes =
        (ReplCompileResult.CompiledClasses) compileResult;
    writer.writeClasses(classes);

    ReplEvalResult evalResult  = evalInWrapper(classes);

    Optional<InterpreterResult> evalError = checkEvalError(evalResult);
    if (evalError.isPresent()) {
      return evalError.get();
    }

    contextUpdater.update();

    if (evalResult instanceof ReplEvalResult.UnitResult) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    }
    if (evalResult instanceof ReplEvalResult.ValueResult) {
      ReplEvalResult.ValueResult v = (ReplEvalResult.ValueResult) evalResult;
      String typeString = shortenTypes ? shorten(v.getType()) : v.getType();
      String valueString = prepareValueString(v.getValue());

      return new InterpreterResult(
          InterpreterResult.Code.SUCCESS,
          v.getName() + ": " + typeString + " = " + valueString);
    }
    return new InterpreterResult(InterpreterResult.Code.ERROR,
        "unknown evaluation result: " + evalResult.toString());
  }

  private ReplEvalResult evalInWrapper(ReplCompileResult.CompiledClasses classes) {
    ReplEvalResult evalResult;
    // For now, invokeWrapper parameter in evaluator.eval does not work, so wrapping happens here
    Function0<ReplEvalResult> runEvaluator = () -> evaluator.eval(state, classes, null, null);
    if (wrapper != null) {
      evalResult = wrapper.invoke(runEvaluator);
    } else {
      evalResult = runEvaluator.invoke();
    }
    return evalResult;
  }

  private Optional<InterpreterResult> checkCompileError(ReplCompileResult compileResult) {
    if (compileResult instanceof ReplCompileResult.Incomplete) {
      return Optional.of(new InterpreterResult(InterpreterResult.Code.INCOMPLETE));
    }

    if (compileResult instanceof ReplCompileResult.Error) {
      ReplCompileResult.Error e = (ReplCompileResult.Error) compileResult;
      return Optional.of(new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage()));
    }

    if (!(compileResult instanceof ReplCompileResult.CompiledClasses)) {
      return Optional.of(new InterpreterResult(InterpreterResult.Code.ERROR,
          "unknown compilation result:" + compileResult.toString()));
    }

    return Optional.empty();
  }

  private Optional<InterpreterResult> checkEvalError(ReplEvalResult evalResult) {
    if (evalResult instanceof ReplEvalResult.Error) {
      ReplEvalResult.Error e = (ReplEvalResult.Error) evalResult;
      return Optional.of(new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage()));
    }

    if (evalResult instanceof ReplEvalResult.Incomplete) {
      return Optional.of(new InterpreterResult(InterpreterResult.Code.INCOMPLETE));
    }

    if (evalResult instanceof ReplEvalResult.HistoryMismatch) {
      ReplEvalResult.HistoryMismatch e = (ReplEvalResult.HistoryMismatch) evalResult;
      return Optional.of(new InterpreterResult(
          InterpreterResult.Code.ERROR, "history mismatch at " + e.getLineNo()));
    }

    return Optional.empty();
  }

  private String prepareValueString(Object value) {
    if (value == null) {
      return "null";
    }
    if (!(value instanceof Collection<?>)) {
      return value.toString();
    }

    Collection<?> collection = (Collection<?>) value;

    if (collection.size() <= maxResult) {
      return value.toString();
    }

    return "[" + collection.stream()
        .limit(maxResult)
        .map(Object::toString)
        .collect(Collectors.joining(","))
        + " ... " + (collection.size() - maxResult) + " more]";
  }

  /**
   * Kotlin REPL has built-in context for getting user-declared functions and variables
   * and setting invokeWrapper for additional side effects in evaluation.
   * It can be accessed inside REPL by name `kc`, e.g. kc.showVars()
   */
  public class KotlinContext {
    private Map<String, KotlinVariableInfo> vars = new HashMap<>();
    private Set<KotlinFunctionInfo> functions = new TreeSet<>();

    public List<KotlinVariableInfo> getVars() {
      return new ArrayList<>(vars.values());
    }

    public void setWrapper(InvokeWrapper wrapper) {
      KotlinRepl.this.wrapper = wrapper;
    }

    public InvokeWrapper getWrapper() {
      return KotlinRepl.this.wrapper;
    }

    public List<KotlinFunctionInfo> getFunctions() {
      return new ArrayList<>(functions);
    }

    public void showVars() {
      for (KotlinVariableInfo var : vars.values()) {
        System.out.println(var.toString(shortenTypes));
      }
    }

    public void showFunctions() {
      for (KotlinFunctionInfo fun : functions) {
        System.out.println(fun.toString(shortenTypes));
      }
    }
  }
}
