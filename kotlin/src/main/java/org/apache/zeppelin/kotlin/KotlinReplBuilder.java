package org.apache.zeppelin.kotlin;

import kotlin.Unit;
import kotlin.script.experimental.api.ScriptCompilationConfiguration;
import kotlin.script.experimental.api.ScriptCompilationKt;
import kotlin.script.experimental.api.ScriptEvaluationConfiguration;
import kotlin.script.experimental.api.ScriptEvaluationKt;
import kotlin.script.experimental.host.ScriptingHostConfiguration;
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator;
import kotlin.script.experimental.jvmhost.impl.JvmHostUtilKt;
import kotlin.script.experimental.jvmhost.repl.JvmReplCompiler;
import kotlin.script.experimental.jvmhost.repl.JvmReplEvaluator;
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerImpl;

import static kotlin.script.experimental.jvm.JvmScriptingHostConfigurationKt.getDefaultJvmScriptingHostConfiguration;

public class KotlinReplBuilder {

  private ScriptingHostConfiguration hostConf = getDefaultJvmScriptingHostConfiguration();

  public KotlinRepl build() {
    KJvmReplCompilerImpl compilerImpl =
        new KJvmReplCompilerImpl(JvmHostUtilKt.withDefaults(hostConf));

    JvmReplCompiler compiler = new JvmReplCompiler(
        buildCompilationConfiguration(),
        hostConf,
        compilerImpl);

    JvmReplEvaluator evaluator = new JvmReplEvaluator(
        buildEvaluationConfiguration(),
        new BasicJvmScriptEvaluator());

    return new KotlinRepl(compiler, evaluator);
  }

  private ScriptCompilationConfiguration buildCompilationConfiguration() {
    return new ScriptCompilationConfiguration((b) -> {
      b.invoke(ScriptCompilationKt.getHostConfiguration(b), hostConf);

      return Unit.INSTANCE;
    });
  }

  private ScriptEvaluationConfiguration buildEvaluationConfiguration() {
    return new ScriptEvaluationConfiguration((b) -> {
      b.invoke(ScriptEvaluationKt.getHostConfiguration(b), hostConf);

      return Unit.INSTANCE;
    });
  }
}
