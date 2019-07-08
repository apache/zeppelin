package org.apache.zeppelin.kotlin.conf.compiler;

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys;
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot;
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar;
import org.jetbrains.kotlin.config.CommonConfigurationKeys;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class KotlinPluginLoader {
  private static final Logger LOGGER = LoggerFactory.getLogger(KotlinPluginLoader.class);
  private static final String KOTLIN_STDLIB_JAR = "kotlin-stdlib.jar";
  private static final String LIB_PATH = "/Users/dmitry.kaznacheev/zeppelin/dist/kotlinc/lib/";

  public KotlinPluginLoader() {
  }

  String pathTo(String path) {
    return LIB_PATH + path;
  }

  private String findJavaRuntimeJarPath() {
    // TODO(dk) get actual runtime jar
    return "/Library/Java/JavaVirtualMachines/jdk1.8.0_212.jdk/Contents/Home/jre/lib/";
  }

  private void addClassPathRoot(CompilerConfiguration configuration, String path) {
    configuration.add(CLIConfigurationKeys.CONTENT_ROOTS,
            new JvmClasspathRoot(new File(path)));
  }

  // TODO(dk) split into two classes w/ plugin and compiler loader
  public CompilerConfiguration loadCompilerConfiguration() {
    CompilerConfiguration configuration = new CompilerConfiguration();

    configuration.put(CommonConfigurationKeys.MODULE_NAME, "zeppelin-kotlin");
    configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, new MessageCollectorImpl());

    addClassPathRoot(configuration, findJavaRuntimeJarPath());
    addClassPathRoot(configuration, pathTo(KOTLIN_STDLIB_JAR));

    configuration.add(ComponentRegistrar.Companion.getPLUGIN_COMPONENT_REGISTRARS(),
        new ScriptingCompilerConfigurationComponentRegistrar());

    return configuration;
  }
}
