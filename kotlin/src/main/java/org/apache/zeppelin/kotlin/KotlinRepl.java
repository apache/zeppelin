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

package org.apache.zeppelin.kotlin;

import static org.apache.zeppelin.kotlin.reflect.KotlinReflectUtil.functionSignature;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import kotlin.jvm.functions.Function0;
import kotlin.reflect.KFunction;
import kotlin.script.experimental.jvmhost.repl.JvmReplCompiler;
import kotlin.script.experimental.jvmhost.repl.JvmReplEvaluator;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.kotlin.context.KotlinReceiver;
import org.apache.zeppelin.kotlin.reflect.ContextUpdater;
import org.apache.zeppelin.kotlin.reflect.KotlinVariableInfo;

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

  @SuppressWarnings("unchecked")
  public KotlinRepl(JvmReplCompiler compiler,
                    JvmReplEvaluator evaluator,
                    KotlinReceiver receiver,
                    String outputDir,
                    int maxResult,
                    boolean shortenTypes) {
    this.compiler = compiler;
    this.evaluator = evaluator;
    ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();
    state = new AggregatedReplStageState(
        compiler.createState(stateLock),
        evaluator.createState(stateLock),
        stateLock);
    counter = new AtomicInteger(0);

    writer = new ClassWriter(outputDir);
    this.maxResult = maxResult;
    this.shortenTypes = shortenTypes;

    ctx = new KotlinContext();
    receiver.kc = ctx;

    contextUpdater = new ContextUpdater(state, ctx.vars, ctx.functions);
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
    writer.writeClasses(classes);

    ReplEvalResult evalResult;
    Function0<ReplEvalResult> runEvaluator = () -> evaluator.eval(state, classes, null, null);
    if (wrapper != null) {
      evalResult = wrapper.invoke(runEvaluator);
    } else {
      evalResult = runEvaluator.invoke();
    }

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
      contextUpdater.update();
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    }
    if (evalResult instanceof ReplEvalResult.ValueResult) {
      contextUpdater.update();

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

  public List<KotlinVariableInfo> getVariables() {
    return ctx.getVars();
  }

  public List<KFunction<?>> getFunctions() {
    return ctx.getFunctions();
  }

  public KotlinContext getKotlinContext() {
    return ctx;
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


  public class KotlinContext {
    private Map<String, KotlinVariableInfo> vars = new HashMap<>();
    private Set<KFunction<?>> functions = new HashSet<>();

    public List<KotlinVariableInfo> getVars() {
      return new ArrayList<>(vars.values());
    }

    public void setWrapper(InvokeWrapper wrapper) {
      KotlinRepl.this.wrapper = wrapper;
    }

    public InvokeWrapper getWrapper() {
      return KotlinRepl.this.wrapper;
    }

    public List<KFunction<?>> getFunctions() {
      return new ArrayList<>(functions);
    }

    public void showVars() {
      for (KotlinVariableInfo var : vars.values()) {
        System.out.println(var.toString(shortenTypes));
      }
    }

    public void showFunctions() {
      for (KFunction<?> fun : functions) {
        System.out.println(functionSignature(fun));
      }
    }
  }
}
