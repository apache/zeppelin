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

package org.apache.zeppelin.beam;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;

/**
 * 
 * StaticRepl for compling the java code in memory
 * 
 */
public class StaticRepl {
  static Logger logger = LoggerFactory.getLogger(StaticRepl.class);

  public static String execute(String generatedClassName, String code) throws Exception {

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();

    // Java parasing
    JavaProjectBuilder builder = new JavaProjectBuilder();
    JavaSource src = builder.addSource(new StringReader(code));

    // get all classes in code (paragraph)
    List<JavaClass> classes = src.getClasses();
    String mainClassName = null;

    // Searching for class containing Main method
    for (int i = 0; i < classes.size(); i++) {
      boolean hasMain = false;

      for (int j = 0; j < classes.get(i).getMethods().size(); j++) {
        if (classes.get(i).getMethods().get(j).getName().equals("main") && classes.get(i)
            .getMethods().get(j).isStatic()) {          
          mainClassName = classes.get(i).getName();
          hasMain = true;
          break;
        }
      }
      if (hasMain == true) {
        break;
      }

    }

    // if there isn't Main method, will retuen error
    if (mainClassName == null) {
      logger.error("Exception for Main method", "There isn't any class "
          + "containing static main method.");
      throw new Exception("There isn't any class containing static main method.");
    }

    // replace name of class containing Main method with generated name
    code = code.replace(mainClassName, generatedClassName);

    JavaFileObject file = new JavaSourceFromString(generatedClassName, code.toString());
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

    CompilationTask task = compiler.getTask(null, null, diagnostics, null, null, compilationUnits);

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
      try {

        // creating new class loader
        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] { new File("").toURI()
            .toURL() });
        // execute the Main method
        Class.forName(generatedClassName, true, classLoader)
            .getDeclaredMethod("main", new Class[] { String[].class })
            .invoke(null, new Object[] { null });

        System.out.flush();
        System.err.flush();

        // set the stream to old stream
        System.setOut(oldOut);
        System.setErr(oldErr);

        return baosOut.toString();

      } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
          | InvocationTargetException e) {
        logger.error("Exception in Interpreter while execution", e);
        System.err.println(e);
        e.printStackTrace(newErr);
        throw new Exception(baosErr.toString(), e);

      } finally {

        System.out.flush();
        System.err.flush();

        System.setOut(oldOut);
        System.setErr(oldErr);
      }
    }

  }

}

class JavaSourceFromString extends SimpleJavaFileObject {
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
