package org.apache.zeppelin.kotlin;

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
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerImpl;

import java.util.Collections;
import java.util.List;

import static kotlin.script.experimental.jvm.JvmScriptingHostConfigurationKt.getDefaultJvmScriptingHostConfiguration;

public class KotlinReplBuilder {

  private ScriptingHostConfiguration hostConf = getDefaultJvmScriptingHostConfiguration();

  public KotlinRepl build(ExecutionContext ctx) {
    KJvmReplCompilerImpl compilerImpl =
        new KJvmReplCompilerImpl(JvmHostUtilKt.withDefaults(hostConf));

    JvmReplCompiler compiler = new JvmReplCompiler(
        buildCompilationConfiguration(ctx),
        hostConf,
        compilerImpl);

    JvmReplEvaluator evaluator = new JvmReplEvaluator(
        buildEvaluationConfiguration(ctx),
        new BasicJvmScriptEvaluator());

    return new KotlinRepl(compiler, evaluator);
  }

  private ScriptCompilationConfiguration buildCompilationConfiguration(final ExecutionContext ctx) {
    return new ScriptCompilationConfiguration((b) -> {
      b.invoke(ScriptCompilationKt.getHostConfiguration(b), hostConf);

      JvmScriptCompilationConfigurationBuilder jvmBuilder =
          JvmScriptCompilationKt.getJvm(b);
      JvmScriptCompilationKt.dependenciesFromCurrentContext(
          jvmBuilder, new String[0], true, false);


      KotlinType kt = new KotlinType(ctx.getClass().getCanonicalName());
      List<KotlinType> receivers =
          Collections.singletonList(kt);
      b.invoke(ScriptCompilationKt.getImplicitReceivers(b), receivers);

      return Unit.INSTANCE;
    });
  }

  private ScriptEvaluationConfiguration buildEvaluationConfiguration(final ExecutionContext ctx) {
    return new ScriptEvaluationConfiguration((b) -> {
      b.invoke(ScriptEvaluationKt.getHostConfiguration(b), hostConf);

      List<Object> receivers =
          Collections.singletonList(ctx);
      b.invoke(ScriptEvaluationKt.getImplicitReceivers(b), receivers);

      return Unit.INSTANCE;
    });
  }
}
