package org.apache.zeppelin.kotlin.conf.compiler;

import org.apache.zeppelin.interpreter.InterpreterException;
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys;
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer;
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector;
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot;
import org.jetbrains.kotlin.com.intellij.openapi.util.Pair;
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar;
import org.jetbrains.kotlin.config.CommonConfigurationKeys;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar;
import org.jetbrains.kotlin.utils.PathUtil;

import java.io.File;
import java.io.IOException;

public class KotlinCompilerLoader {

  private static void addClassPathRoot(CompilerConfiguration configuration, String path) {
    configuration.add(CLIConfigurationKeys.CONTENT_ROOTS,
            new JvmClasspathRoot(new File(path)));
  }

  private static String getKotlinStdlibPath() throws InterpreterException {
    try {
      return PathUtil.getResourcePathForClass(Pair.class).getCanonicalPath();
    } catch (IOException e) {
      throw new InterpreterException("Kotlin stdlib not found");
    }
  }

  public static CompilerConfiguration loadCompilerConfiguration() throws InterpreterException {

    CompilerConfiguration configuration = new CompilerConfiguration();

    configuration.put(CommonConfigurationKeys.MODULE_NAME, "zeppelin-kotlin");
    configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
        new PrintingMessageCollector(System.out, MessageRenderer.WITHOUT_PATHS, false));

    addClassPathRoot(configuration, getKotlinStdlibPath());

    // loading compiler scripting plugins
    configuration.add(ComponentRegistrar.Companion.getPLUGIN_COMPONENT_REGISTRARS(),
        new ScriptingCompilerConfigurationComponentRegistrar());

    return configuration;
  }
}
