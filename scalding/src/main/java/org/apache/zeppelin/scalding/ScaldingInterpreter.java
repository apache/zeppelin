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

package org.apache.zeppelin.scalding;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Console;
import scala.Some;
import scala.None;
import scala.tools.nsc.Settings;
import scala.tools.nsc.settings.MutableSettings.BooleanSetting;
import scala.tools.nsc.settings.MutableSettings.PathSetting;

/**
 * Scalding interpreter for Zeppelin. Based off the Spark interpreter code.
 *
 */
public class ScaldingInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(ScaldingInterpreter.class);

  public static final List<String> NO_COMPLETION = 
    Collections.unmodifiableList(new ArrayList<String>());

  static {
    Interpreter.register("scalding", ScaldingInterpreter.class.getName());
  }

  private ScaldingILoop interpreter;
  private ByteArrayOutputStream out;
  private Map<String, Object> binder;

  public ScaldingInterpreter(Properties property) {
    super(property);
    out = new ByteArrayOutputStream();
  }

  @Override
  public void open() {
    URL[] urls = getClassloaderUrls();

    // Very nice discussion about how scala compiler handle classpath
    // https://groups.google.com/forum/#!topic/scala-user/MlVwo2xCCI0

    /*
     * > val env = new nsc.Settings(errLogger) > env.usejavacp.value = true > val p = new
     * Interpreter(env) > p.setContextClassLoader > Alternatively you can set the class path through
     * nsc.Settings.classpath.
     *
     * >> val settings = new Settings() >> settings.usejavacp.value = true >>
     * settings.classpath.value += File.pathSeparator + >> System.getProperty("java.class.path") >>
     * val in = new Interpreter(settings) { >> override protected def parentClassLoader =
     * getClass.getClassLoader >> } >> in.setContextClassLoader()
     */
    Settings settings = new Settings();

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

    /* Scalding interpreter */
    PrintStream printStream = new PrintStream(out);
    interpreter = new ScaldingILoop(null, new PrintWriter(out));
    interpreter.settings_$eq(settings);
    interpreter.createInterpreter();

    interpreter.intp().
      interpret("@transient var _binder = new java.util.HashMap[String, Object]()");
    binder = (Map<String, Object>) getValue("_binder");
    binder.put("out", printStream);
  }

  private Object getValue(String name) {
    Object ret = interpreter.intp().valueOfTerm(name);
    if (ret instanceof None) {
      return null;
    } else if (ret instanceof Some) {
      return ((Some) ret).get();
    } else {
      return ret;
    }
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

  @Override
  public void close() {
    interpreter.intp().close();
  }


  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    logger.info("Running Scalding command '" + cmd + "'");

    if (cmd == null || cmd.trim().length() == 0) {
      return new InterpreterResult(Code.SUCCESS);
    }
    return interpret(cmd.split("\n"), contextInterpreter);
  }

  public InterpreterResult interpret(String[] lines, InterpreterContext context) {
    synchronized (this) {
      InterpreterResult r = interpretInput(lines);
      return r;
    }
  }

  public InterpreterResult interpretInput(String[] lines) {

    // add print("") to make sure not finishing with comment
    // see https://github.com/NFLabs/zeppelin/issues/151
    String[] linesToRun = new String[lines.length + 1];
    for (int i = 0; i < lines.length; i++) {
      linesToRun[i] = lines[i];
    }
    linesToRun[lines.length] = "print(\"\")";

    Console.setOut((java.io.PrintStream) binder.get("out"));
    out.reset();
    Code r = null;
    String incomplete = "";

    for (int l = 0; l < linesToRun.length; l++) {
      String s = linesToRun[l];
      // check if next line starts with "." (but not ".." or "./") it is treated as an invocation
      if (l + 1 < linesToRun.length) {
        String nextLine = linesToRun[l + 1].trim();
        if (nextLine.startsWith(".") && !nextLine.startsWith("..") && !nextLine.startsWith("./")) {
          incomplete += s + "\n";
          continue;
        }
      }

      scala.tools.nsc.interpreter.Results.Result res = null;
      try {
        res = interpreter.intp().interpret(incomplete + s);
      } catch (Exception e) {
        logger.error("Interpreter exception: ", e);
        return new InterpreterResult(Code.ERROR, e.getMessage());
      }

      r = getResultCode(res);

      if (r == Code.ERROR) {
        Console.flush();
        return new InterpreterResult(r, out.toString());
      } else if (r == Code.INCOMPLETE) {
        incomplete += s + "\n";
      } else {
        incomplete = "";
      }
    }

    if (r == Code.INCOMPLETE) {
      return new InterpreterResult(r, "Incomplete expression");
    } else {
      Console.flush();
      return new InterpreterResult(r, out.toString());
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
    // not implemented
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    // fine-grained progress not implemented - return 0
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
        ScaldingInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return NO_COMPLETION;
  }
}
