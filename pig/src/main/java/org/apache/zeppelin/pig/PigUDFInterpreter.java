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

package org.apache.zeppelin.pig;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaSource;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.pig.PigServer;
import org.apache.zeppelin.interpreter.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Interpreter for Pig UDF
 */
public class PigUDFInterpreter extends Interpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(PigUDFInterpreter.class);

  private PigServer pigServer;
  private String udfBuildClasspath;

  public PigUDFInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    pigServer = getPigInterpreter().getPigServer();
    // register dependency jars
    String localRepo = getProperty("zeppelin.interpreter.localRepo");
    if (localRepo != null && new File(localRepo).exists()) {
      File[] jars = new File(localRepo).listFiles(new FileFilter() {
        @Override
        public boolean accept(File pathname) {
          return pathname.isFile() && pathname.getName().endsWith(".jar");
        }
      });
      StringBuilder classPathBuilder = new StringBuilder(System.getProperty("java.class.path"));
      for (File jar : jars) {
        try {
          pigServer.registerJar(jar.getAbsolutePath());
          classPathBuilder.append(":" + jar.getAbsolutePath());
          LOGGER.debug("Register dependency jar:" + jar.getAbsolutePath());
        } catch (IOException e) {
          LOGGER.error("Fail to register dependency jar", e);
        }
      }
      this.udfBuildClasspath = classPathBuilder.toString();
    }
  }

  @Override
  public void close() {

  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    try {
      CompiledClass compiledClass = compile(st);
      File jarFile = buildJar(compiledClass);
      pigServer.registerJar(jarFile.getAbsolutePath());

      return new InterpreterResult(InterpreterResult.Code.SUCCESS, "Build successfully");
    } catch (Exception e) {
      LOGGER.error("Fail to compile/build udf", e);
      return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage());
    }
  }

  @Override
  public void cancel(InterpreterContext context) {

  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }


  private PigInterpreter getPigInterpreter() {
    LazyOpenInterpreter lazy = null;
    PigInterpreter pig = null;
    Interpreter p = getInterpreterInTheSameSessionByClassName(PigInterpreter.class.getName());

    while (p instanceof WrappedInterpreter) {
      if (p instanceof LazyOpenInterpreter) {
        lazy = (LazyOpenInterpreter) p;
      }
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }
    pig = (PigInterpreter) p;

    if (lazy != null) {
      lazy.open();
    }
    return pig;
  }

  private CompiledClass compile(String code) throws Exception {

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();

    // Java parsing
    JavaProjectBuilder builder = new JavaProjectBuilder();
    JavaSource src = builder.addSource(new StringReader(code));

    // get all classes in code (paragraph)
    List<JavaClass> classes = src.getClasses();
    if (classes.size() != 1) {
      throw new Exception("Either you doesn't define class or define multiple classes " +
          "in on paragraph.");
    }
    String className = classes.get(0).getName();
    String packageName = classes.get(0).getPackageName();
    JavaFileObject file = new JavaSourceFromString(className, code.toString());
    Iterable<? extends JavaFileObject> compilationUnits = Arrays.asList(file);

    ByteArrayOutputStream baosOut = new ByteArrayOutputStream();
    ByteArrayOutputStream baosErr = new ByteArrayOutputStream();

    // Creating new stream to get the output data
    PrintStream newOut = new PrintStream(baosOut);
    PrintStream newErr = new PrintStream(baosErr);
    // Save the old System.out!
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    // Tell Java to use your special stream
    System.setOut(newOut);
    System.setErr(newErr);

    List<String> options = new ArrayList<>();
    options.addAll(Arrays.asList("-classpath", udfBuildClasspath));
    JavaCompiler.CompilationTask task = compiler.getTask(null, null, diagnostics, options, null,
        compilationUnits);

    // executing the compilation process
    boolean success = task.call();

    // if success is false will get error
    if (!success) {
      for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
        if (diagnostic.getLineNumber() == -1) {
          continue;
        }
        System.err.println("line " + diagnostic.getLineNumber() + " : "
            + diagnostic.getMessage(null));
      }
      System.out.flush();
      System.err.flush();

      System.setOut(oldOut);
      System.setErr(oldErr);
      logger.error("Exception in Interpreter while compilation", baosErr.toString());
      throw new Exception(baosErr.toString());
    } else {
      System.out.flush();
      System.err.flush();

      // set the stream to old stream
      System.setOut(oldOut);
      System.setErr(oldErr);
      return new CompiledClass(packageName, new File(className + ".class"));
    }
  }

  private File buildJar(CompiledClass clazz) throws IOException {
    File tmpJarFile = File.createTempFile("zeppelin_pig", ".jar");
    FileOutputStream fOut = null;
    JarOutputStream jarOut = null;
    try {
      fOut = new FileOutputStream(tmpJarFile);
      jarOut = new JarOutputStream(fOut);
      String entryPath = null;
      if (clazz.packageName.isEmpty()) {
        entryPath = clazz.classFile.getName();
      } else {
        entryPath = clazz.packageName.replace(".", "/") + "/" + clazz.classFile.getName();
      }
      jarOut.putNextEntry(new JarEntry(entryPath));
      jarOut.write(FileUtils.readFileToByteArray(clazz.classFile));
      jarOut.closeEntry();
      LOGGER.debug("pig udf jar is created under " + tmpJarFile.getAbsolutePath());
      return tmpJarFile;
    } catch (IOException e) {
      throw e;
    } finally {
      if (jarOut != null) {
        jarOut.close();
      }
      if (fOut != null) {
        fOut.close();
      }
    }
  }

  /**
   *
   */
  public static class JavaSourceFromString extends SimpleJavaFileObject {
    final String code;

    JavaSourceFromString(String name, String code) {
      super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
      this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return code;
    }
  }

  /**
   *
   */
  public static class CompiledClass {
    public final String packageName;
    public final File classFile;

    public CompiledClass(String packageName, File classFile) {
      this.packageName = packageName;
      this.classFile = classFile;
    }
  }
}


