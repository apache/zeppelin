package org.apache.zeppelin.kotlin;

import static kotlin.script.experimental.jvm.JvmScriptingHostConfigurationKt.getDefaultJvmScriptingHostConfiguration;
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

  private static Logger logger = LoggerFactory.getLogger(KotlinReplBuilder.class);

  private ScriptingHostConfiguration hostConf = getDefaultJvmScriptingHostConfiguration();

  private KotlinReceiver ctx;
  private List<String> compilerOptions;
  private String outputDir;

  public KotlinReplBuilder() {
    this.ctx = new KotlinReceiver();
    this.compilerOptions = new ArrayList<>();
  }

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

    return new KotlinRepl(compiler, evaluator, outputDir);
  }

  public KotlinReplBuilder executionContext(KotlinReceiver ctx) {
    this.ctx = ctx;
    return this;
  }

  public KotlinReplBuilder compilerOptions(List<String> options) {
    options.forEach(logger::info);
    this.compilerOptions = options;
    return this;
  }

  private ScriptCompilationConfiguration buildCompilationConfiguration() {
    return new ScriptCompilationConfiguration((b) -> {
      b.invoke(ScriptCompilationKt.getHostConfiguration(b), hostConf);

      JvmScriptCompilationConfigurationBuilder jvmBuilder =
          JvmScriptCompilationKt.getJvm(b);
      JvmScriptCompilationKt.dependenciesFromCurrentContext(
          jvmBuilder, new String[0], true, false);

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

  public void outputDir(String outputDir) {
    this.outputDir = outputDir;
  }
}
