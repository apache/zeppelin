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

package org.apache.zeppelin.spark;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.spark.repl.SparkILoop;
import org.apache.spark.repl.SparkIMain;
import org.apache.spark.repl.SparkJLineCompletion;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.WrappedInterpreter;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.spark.dep.SparkDependencyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.DependencyResolutionException;

import scala.Console;
import scala.None;
import scala.Some;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.Completion.Candidates;
import scala.tools.nsc.interpreter.Completion.ScalaCompleter;
import scala.tools.nsc.settings.MutableSettings.BooleanSetting;
import scala.tools.nsc.settings.MutableSettings.PathSetting;


/**
 * DepInterpreter downloads dependencies and pass them when SparkInterpreter initialized.
 * It extends SparkInterpreter but does not create sparkcontext
 *
 */
public class DepInterpreter extends Interpreter {

  static {
    Interpreter.register(
        "dep",
        "spark",
        DepInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add("zeppelin.dep.localrepo",
                getSystemDefault("ZEPPELIN_DEP_LOCALREPO", null, "local-repo"),
                "local repository for dependency loader")
            .add("zeppelin.dep.additionalRemoteRepository",
                "spark-packages,http://dl.bintray.com/spark-packages/maven,false;",
                "A list of 'id,remote-repository-URL,is-snapshot;' for each remote repository.")
            .build());

  }

  private SparkIMain intp;
  private ByteArrayOutputStream out;
  private SparkDependencyContext depc;
  private SparkJLineCompletion completor;
  private SparkILoop interpreter;
  static final Logger LOGGER = LoggerFactory.getLogger(DepInterpreter.class);

  public DepInterpreter(Properties property) {
    super(property);
  }

  public SparkDependencyContext getDependencyContext() {
    return depc;
  }

  public static String getSystemDefault(
      String envName,
      String propertyName,
      String defaultValue) {

    if (envName != null && !envName.isEmpty()) {
      String envValue = System.getenv().get(envName);
      if (envValue != null) {
        return envValue;
      }
    }

    if (propertyName != null && !propertyName.isEmpty()) {
      String propValue = System.getProperty(propertyName);
      if (propValue != null) {
        return propValue;
      }
    }
    return defaultValue;
  }

  @Override
  public void close() {
    if (intp != null) {
      intp.close();
    }
  }

  @Override
  public void open() {
    out = new ByteArrayOutputStream();
    createIMain();
  }


  private void createIMain() {
    Settings settings = new Settings();
    URL[] urls = getClassloaderUrls();

    // set classpath for scala compiler
    PathSetting pathSettings = settings.classpath();
    String classpath = "";
    List<File> paths = currentClassPath();
    for (File f : paths) {
      if (classpath.length() > 0) {
        classpath += File.pathSeparator;
      }
      classpath += f.getAbsolutePath();
    }

    if (urls != null) {
      for (URL u : urls) {
        if (classpath.length() > 0) {
          classpath += File.pathSeparator;
        }
        classpath += u.getFile();
      }
    }

    pathSettings.v_$eq(classpath);
    settings.scala$tools$nsc$settings$ScalaSettings$_setter_$classpath_$eq(pathSettings);

    // set classloader for scala compiler
    settings.explicitParentLoader_$eq(new Some<ClassLoader>(Thread.currentThread()
        .getContextClassLoader()));

    BooleanSetting b = (BooleanSetting) settings.usejavacp();
    b.v_$eq(true);
    settings.scala$tools$nsc$settings$StandardScalaSettings$_setter_$usejavacp_$eq(b);

    interpreter = new SparkILoop(null, new PrintWriter(out));
    interpreter.settings_$eq(settings);

    interpreter.createInterpreter();


    intp = interpreter.intp();
    intp.setContextClassLoader();
    intp.initializeSynchronous();

    depc = new SparkDependencyContext(getProperty("zeppelin.dep.localrepo"),
                                 getProperty("zeppelin.dep.additionalRemoteRepository"));
    completor = new SparkJLineCompletion(intp);
    intp.interpret("@transient var _binder = new java.util.HashMap[String, Object]()");
    Map<String, Object> binder = (Map<String, Object>) getValue("_binder");
    binder.put("depc", depc);

    intp.interpret("@transient val z = "
        + "_binder.get(\"depc\")"
        + ".asInstanceOf[org.apache.zeppelin.spark.dep.SparkDependencyContext]");

  }

  public Object getValue(String name) {
    Object ret = intp.valueOfTerm(name);
    if (ret instanceof None) {
      return null;
    } else if (ret instanceof Some) {
      return ((Some) ret).get();
    } else {
      return ret;
    }
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    PrintStream printStream = new PrintStream(out);
    Console.setOut(printStream);
    out.reset();

    SparkInterpreter sparkInterpreter = getSparkInterpreter();

    if (sparkInterpreter != null && sparkInterpreter.isSparkContextInitialized()) {
      return new InterpreterResult(Code.ERROR,
          "Must be used before SparkInterpreter (%spark) initialized\n" +
          "Hint: put this paragraph before any Spark code and " +
          "restart Zeppelin/Interpreter" );
    }

    scala.tools.nsc.interpreter.Results.Result ret = intp.interpret(st);
    Code code = getResultCode(ret);

    try {
      depc.fetch();
    } catch (MalformedURLException | DependencyResolutionException
        | ArtifactResolutionException e) {
      LOGGER.error("Exception in DepInterpreter while interpret ", e);
      return new InterpreterResult(Code.ERROR, e.toString());
    }

    if (code == Code.INCOMPLETE) {
      return new InterpreterResult(code, "Incomplete expression");
    } else if (code == Code.ERROR) {
      return new InterpreterResult(code, out.toString());
    } else {
      return new InterpreterResult(code, out.toString());
    }
  }

  private Code getResultCode(scala.tools.nsc.interpreter.Results.Result r) {
    if (r instanceof scala.tools.nsc.interpreter.Results.Success$) {
      return Code.SUCCESS;
    } else if (r instanceof scala.tools.nsc.interpreter.Results.Incomplete$) {
      return Code.INCOMPLETE;
    } else {
      return Code.ERROR;
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
  }


  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    ScalaCompleter c = completor.completer();
    Candidates ret = c.complete(buf, cursor);
    return scala.collection.JavaConversions.seqAsJavaList(ret.candidates());
  }

  private List<File> currentClassPath() {
    List<File> paths = classPath(Thread.currentThread().getContextClassLoader());
    String[] cps = System.getProperty("java.class.path").split(File.pathSeparator);
    if (cps != null) {
      for (String cp : cps) {
        paths.add(new File(cp));
      }
    }
    return paths;
  }

  private List<File> classPath(ClassLoader cl) {
    List<File> paths = new LinkedList<File>();
    if (cl == null) {
      return paths;
    }

    if (cl instanceof URLClassLoader) {
      URLClassLoader ucl = (URLClassLoader) cl;
      URL[] urls = ucl.getURLs();
      if (urls != null) {
        for (URL url : urls) {
          paths.add(new File(url.getFile()));
        }
      }
    }
    return paths;
  }

  private SparkInterpreter getSparkInterpreter() {
    InterpreterGroup intpGroup = getInterpreterGroup();
    if (intpGroup == null) {
      return null;
    }

    Interpreter p = getInterpreterInTheSameSessionByClassName(SparkInterpreter.class.getName());
    if (p == null) {
      return null;
    }

    while (p instanceof WrappedInterpreter) {
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }
    return (SparkInterpreter) p;
  }

  @Override
  public Scheduler getScheduler() {
    SparkInterpreter sparkInterpreter = getSparkInterpreter();
    if (sparkInterpreter != null) {
      return getSparkInterpreter().getScheduler();
    } else {
      return null;
    }
  }
}
