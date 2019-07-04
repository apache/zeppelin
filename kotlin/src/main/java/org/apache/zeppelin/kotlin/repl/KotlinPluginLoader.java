package org.apache.zeppelin.kotlin.repl;

import org.apache.zeppelin.dep.Booter;
import org.apache.zeppelin.dep.DependencyResolver;
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys;
import org.jetbrains.kotlin.cli.jvm.plugins.PluginCliParser;
import org.jetbrains.kotlin.config.CommonConfigurationKeys;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.sonatype.aether.RepositoryException;
import org.sonatype.aether.repository.RemoteRepository;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot;

import static org.jetbrains.kotlin.utils.PathUtil.KOTLIN_SCRIPTING_COMPILER_PLUGIN_JAR;
import static org.jetbrains.kotlin.utils.PathUtil.KOTLIN_SCRIPTING_COMPILER_IMPL_JAR;
import static org.jetbrains.kotlin.utils.PathUtil.KOTLIN_SCRIPTING_COMMON_JAR;
import static org.jetbrains.kotlin.utils.PathUtil.KOTLIN_SCRIPTING_JVM_JAR;
import static org.jetbrains.kotlin.utils.PathUtil.KOTLIN_JAVA_STDLIB_SRC_JAR;

public class KotlinPluginLoader {

  private DependencyResolver resolver;
  private static final List<String> pluginJars = Arrays.asList(
      KOTLIN_SCRIPTING_COMPILER_PLUGIN_JAR,
      KOTLIN_SCRIPTING_COMPILER_IMPL_JAR,
      KOTLIN_SCRIPTING_COMMON_JAR,
      KOTLIN_SCRIPTING_JVM_JAR,
      KOTLIN_JAVA_STDLIB_SRC_JAR
  );
  private static final String KOTLIN_STDLIB_JAR = "kotlin-stdlib.jar";

  public KotlinPluginLoader() {
    RemoteRepository mvnLocalRepo = Booter.newLocalRepository();
    String path = mvnLocalRepo.getUrl();
    resolver = new DependencyResolver(path);
  }

  public String getJarPathByName(String name) {
    try {
      return resolver.load(name).get(0).getAbsolutePath();
    } catch (RepositoryException | IOException e) {
      return null;
    }
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
    addClassPathRoot(configuration, getJarPathByName(KOTLIN_STDLIB_JAR));

    PluginCliParser.loadPluginsSafe(pluginJars, null, configuration);

    return configuration;
  }
}
