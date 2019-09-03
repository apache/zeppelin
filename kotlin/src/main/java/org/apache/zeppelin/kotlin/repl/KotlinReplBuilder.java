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

import static kotlin.script.experimental.jvm.JvmScriptingHostConfigurationKt.getDefaultJvmScriptingHostConfiguration;
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerImpl;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import kotlin.Unit;
import kotlin.script.experimental.api.KotlinType;
import kotlin.script.experimental.api.ScriptCompilationConfiguration;
import kotlin.script.experimental.api.ScriptCompilationKt;
import kotlin.script.experimental.api.ScriptEvaluationConfiguration;
import kotlin.script.experimental.api.ScriptEvaluationKt;
import kotlin.script.experimental.host.ScriptingHostConfiguration;
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator;
import kotlin.script.experimental.jvm.JvmScriptCompilationConfigurationBuilder;
import kotlin.script.experimental.jvm.JvmScriptCompilationKt;
import kotlin.script.experimental.jvmhost.impl.JvmHostUtilKt;
import kotlin.script.experimental.jvmhost.repl.JvmReplCompiler;
import kotlin.script.experimental.jvmhost.repl.JvmReplEvaluator;
import org.apache.zeppelin.kotlin.context.KotlinReceiver;

public class KotlinReplBuilder {

  private ScriptingHostConfiguration hostConf = getDefaultJvmScriptingHostConfiguration();

  private KotlinReceiver ctx;
  private Set<String> classpath;
  private List<String> codeOnLoad;
  private String outputDir;
  private int maxResult = 1000;
  private boolean shortenTypes = true;

  public KotlinReplBuilder() {
    this.ctx = new KotlinReceiver();

    this.classpath = new HashSet<>();
    String[] javaClasspath = System.getProperty("java.class.path").split(File.pathSeparator);
    Collections.addAll(classpath, javaClasspath);

    this.codeOnLoad = new ArrayList<>();
  }

  public KotlinRepl build() {
    String receiverClassPath = ctx.getClass()
        .getProtectionDomain().getCodeSource().getLocation().getPath();
    this.classPath(receiverClassPath);

    KJvmReplCompilerImpl compilerImpl =
        new KJvmReplCompilerImpl(JvmHostUtilKt.withDefaults(hostConf));

    JvmReplCompiler compiler = new JvmReplCompiler(
        buildCompilationConfiguration(),
        hostConf,
        compilerImpl);

    JvmReplEvaluator evaluator = new JvmReplEvaluator(
        buildEvaluationConfiguration(),
        new BasicJvmScriptEvaluator());

    KotlinRepl repl = new KotlinRepl(compiler, evaluator, ctx, outputDir, maxResult, shortenTypes);
    for (String line: codeOnLoad) {
      repl.eval(line);
    }
    return repl;
  }

  public KotlinReplBuilder maxResult(int maxResult) {
    this.maxResult = maxResult;
    return this;
  }

  public KotlinReplBuilder executionContext(KotlinReceiver ctx) {
    this.ctx = ctx;
    return this;
  }

  public KotlinReplBuilder classPath(String path) {
    this.classpath.add(path);
    return this;
  }

  public KotlinReplBuilder classPath(Collection<String> paths) {
    this.classpath.addAll(paths);
    return this;
  }

  public KotlinReplBuilder codeOnLoad(String code) {
    this.codeOnLoad.add(code);
    return this;
  }

  public KotlinReplBuilder codeOnLoad(Collection<String> code) {
    this.codeOnLoad.addAll(code);
    return this;
  }

  public KotlinReplBuilder outputDir(String outputDir) {
    this.outputDir = outputDir;
    return this;
  }

  public KotlinReplBuilder shortenTypes(boolean shortenTypes) {
    this.shortenTypes = shortenTypes;
    return this;
  }

  private String buildClassPath() {
    StringJoiner joiner = new StringJoiner(File.pathSeparator);
    for (String path : classpath) {
      if (path != null && !path.equals("")) {
        joiner.add(path);
      }
    }
    return joiner.toString();
  }

  private ScriptCompilationConfiguration buildCompilationConfiguration() {
    return new ScriptCompilationConfiguration((b) -> {
      b.invoke(ScriptCompilationKt.getHostConfiguration(b), hostConf);

      JvmScriptCompilationConfigurationBuilder jvmBuilder =
          JvmScriptCompilationKt.getJvm(b);
      JvmScriptCompilationKt.dependenciesFromCurrentContext(
          jvmBuilder, new String[0], true, false);

      List<String> compilerOptions = Arrays.asList("-classpath", buildClassPath());

      b.invoke(ScriptCompilationKt.getCompilerOptions(b), compilerOptions);

      KotlinType kt = new KotlinType(ctx.getClass().getCanonicalName());
      List<KotlinType> receivers =
          Collections.singletonList(kt);
      b.invoke(ScriptCompilationKt.getImplicitReceivers(b), receivers);

      return Unit.INSTANCE;
    });
  }

  private ScriptEvaluationConfiguration buildEvaluationConfiguration() {
    return new ScriptEvaluationConfiguration((b) -> {
      b.invoke(ScriptEvaluationKt.getHostConfiguration(b), hostConf);

      List<Object> receivers =
          Collections.singletonList(ctx);
      b.invoke(ScriptEvaluationKt.getImplicitReceivers(b), receivers);

      return Unit.INSTANCE;
    });
  }
}
