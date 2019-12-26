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

package org.apache.zeppelin.kotlin.repl.building;

import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerImpl;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import kotlin.Unit;
import kotlin.script.experimental.api.KotlinType;
import kotlin.script.experimental.api.ScriptCompilationConfiguration;
import kotlin.script.experimental.api.ScriptCompilationKt;
import kotlin.script.experimental.api.ScriptEvaluationConfiguration;
import kotlin.script.experimental.api.ScriptEvaluationKt;
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator;
import kotlin.script.experimental.jvm.JvmScriptCompilationConfigurationBuilder;
import kotlin.script.experimental.jvm.JvmScriptCompilationKt;
import kotlin.script.experimental.jvmhost.impl.JvmHostUtilKt;
import kotlin.script.experimental.jvmhost.repl.JvmReplCompiler;
import kotlin.script.experimental.jvmhost.repl.JvmReplEvaluator;

/**
 * Util class for building REPL components.
 */
public class ReplBuilding {
  public static JvmReplCompiler buildCompiler(KotlinReplProperties properties) {
    String receiverClassPath = properties.getReceiver().getClass()
        .getProtectionDomain().getCodeSource().getLocation().getPath();
    properties.classPath(receiverClassPath);

    KJvmReplCompilerImpl compilerImpl =
        new KJvmReplCompilerImpl(JvmHostUtilKt.withDefaults(properties.getHostConf()));

    return new JvmReplCompiler(
        buildCompilationConfiguration(properties),
        properties.getHostConf(),
        compilerImpl);
  }

  public static JvmReplEvaluator buildEvaluator(KotlinReplProperties properties) {
    return new JvmReplEvaluator(
        buildEvaluationConfiguration(properties),
        new BasicJvmScriptEvaluator());
  }

  private static String buildClassPath(KotlinReplProperties p) {
    StringJoiner joiner = new StringJoiner(File.pathSeparator);
    for (String path : p.getClasspath()) {
      if (path != null && !path.equals("")) {
        joiner.add(path);
      }
    }
    return joiner.toString();
  }

  private static ScriptCompilationConfiguration buildCompilationConfiguration(
      KotlinReplProperties p) {
    return new ScriptCompilationConfiguration((b) -> {
      b.invoke(ScriptCompilationKt.getHostConfiguration(b), p.getHostConf());

      JvmScriptCompilationConfigurationBuilder jvmBuilder =
          JvmScriptCompilationKt.getJvm(b);
      JvmScriptCompilationKt.dependenciesFromCurrentContext(
          jvmBuilder, new String[0], true, false);

      List<String> compilerOptions = Arrays.asList("-classpath", buildClassPath(p));

      b.invoke(ScriptCompilationKt.getCompilerOptions(b), compilerOptions);

      KotlinType kt = new KotlinType(p.getReceiver().getClass().getCanonicalName());
      List<KotlinType> receivers =
          Collections.singletonList(kt);
      b.invoke(ScriptCompilationKt.getImplicitReceivers(b), receivers);

      return Unit.INSTANCE;
    });
  }

  private static ScriptEvaluationConfiguration buildEvaluationConfiguration(
      KotlinReplProperties p) {
    return new ScriptEvaluationConfiguration((b) -> {
      b.invoke(ScriptEvaluationKt.getHostConfiguration(b), p.getHostConf());

      List<Object> receivers =
          Collections.singletonList(p.getReceiver());
      b.invoke(ScriptEvaluationKt.getImplicitReceivers(b), receivers);

      return Unit.INSTANCE;
    });
  }
}
