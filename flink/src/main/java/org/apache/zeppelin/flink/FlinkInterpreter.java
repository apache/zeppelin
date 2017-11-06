/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.flink;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import org.apache.flink.api.common.JobID;
import org.apache.flink.api.scala.FlinkILoop;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.akka.AkkaUtils;
import org.apache.flink.runtime.instance.ActorGateway;
import org.apache.flink.runtime.messages.JobManagerMessages;
import org.apache.flink.runtime.minicluster.LocalFlinkMiniCluster;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterUtils;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Console;
import scala.Some;
import scala.collection.JavaConversions;
import scala.concurrent.duration.FiniteDuration;
import scala.runtime.AbstractFunction0;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.IMain;
import scala.tools.nsc.interpreter.Results;
import scala.tools.nsc.settings.MutableSettings;
import scala.tools.nsc.settings.MutableSettings.BooleanSetting;
import scala.tools.nsc.settings.MutableSettings.PathSetting;

/**
 * Interpreter for Apache Flink (http://flink.apache.org)
 */
public class FlinkInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(FlinkInterpreter.class);
  private ByteArrayOutputStream out;
  private Configuration flinkConf;
  private LocalFlinkMiniCluster localFlinkCluster;
  private FlinkILoop flinkIloop;
  private Map<String, Object> binder;
  private IMain imain;

  public FlinkInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    out = new ByteArrayOutputStream();
    flinkConf = new org.apache.flink.configuration.Configuration();
    Properties intpProperty = getProperties();
    for (Object k : intpProperty.keySet()) {
      String key = (String) k;
      String val = toString(intpProperty.get(key));
      flinkConf.setString(key, val);
    }

    if (localMode()) {
      startFlinkMiniCluster();
    }

    String[] externalJars = new String[0];
    String localRepo = getProperty("zeppelin.interpreter.localRepo");
    if (localRepo != null) {
      File localRepoDir = new File(localRepo);
      if (localRepoDir.exists()) {
        File[] files = localRepoDir.listFiles();
        if (files != null) {
          externalJars = new String[files.length];
          for (int i = 0; i < files.length; i++) {
            if (externalJars.length > 0) {
              externalJars[i] = files[i].getAbsolutePath();
            }
          }
        }
      }
    }

    flinkIloop = new FlinkILoop(getHost(),
        getPort(),
        flinkConf,
        new Some<>(externalJars),
        (BufferedReader) null,
        new PrintWriter(out));

    flinkIloop.settings_$eq(createSettings());
    flinkIloop.createInterpreter();

    imain = flinkIloop.intp();

    org.apache.flink.api.scala.ExecutionEnvironment benv =
            flinkIloop.scalaBenv();
            //new ExecutionEnvironment(remoteBenv)
    org.apache.flink.streaming.api.scala.StreamExecutionEnvironment senv =
            flinkIloop.scalaSenv();

    senv.getConfig().disableSysoutLogging();
    benv.getConfig().disableSysoutLogging();

    // prepare bindings
    imain.interpret("@transient var _binder = new java.util.HashMap[String, Object]()");
    Map<String, Object> binder = (Map<String, Object>) getLastObject();

    // import libraries
    imain.interpret("import scala.tools.nsc.io._");
    imain.interpret("import Properties.userHome");
    imain.interpret("import scala.compat.Platform.EOL");

    imain.interpret("import org.apache.flink.api.scala._");
    imain.interpret("import org.apache.flink.api.common.functions._");


    binder.put("benv", benv);
    imain.interpret("val benv = _binder.get(\"benv\").asInstanceOf["
            + benv.getClass().getName() + "]");

    binder.put("senv", senv);
    imain.interpret("val senv = _binder.get(\"senv\").asInstanceOf["
            + senv.getClass().getName() + "]");

  }

  private boolean localMode() {
    String host = getProperty("host");
    return host == null || host.trim().length() == 0 || host.trim().equals("local");
  }

  private String getHost() {
    if (localMode()) {
      return "localhost";
    } else {
      return getProperty("host");
    }
  }

  private int getPort() {
    if (localMode()) {
      return localFlinkCluster.getLeaderRPCPort();
    } else {
      return Integer.parseInt(getProperty("port"));
    }
  }

  private Settings createSettings() {
    URL[] urls = getClassloaderUrls();
    Settings settings = new Settings();

    // set classpath
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
    settings.explicitParentLoader_$eq(new Some<>(Thread.currentThread()
        .getContextClassLoader()));
    BooleanSetting b = (BooleanSetting) settings.usejavacp();
    b.v_$eq(true);
    settings.scala$tools$nsc$settings$StandardScalaSettings$_setter_$usejavacp_$eq(b);

    // To prevent 'File name too long' error on some file system.
    MutableSettings.IntSetting numClassFileSetting = settings.maxClassfileName();
    numClassFileSetting.v_$eq(128);
    settings.scala$tools$nsc$settings$ScalaSettings$_setter_$maxClassfileName_$eq(
            numClassFileSetting);

    return settings;
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
    List<File> paths = new LinkedList<>();
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

  public Object getLastObject() {
    Object obj = imain.lastRequest().lineRep().call(
        "$result",
        JavaConversions.asScalaBuffer(new LinkedList<>()));
    return obj;
  }

  @Override
  public void close() {
    flinkIloop.closeInterpreter();

    if (localMode()) {
      stopFlinkMiniCluster();
    }
  }

  @Override
  public InterpreterResult interpret(String line, InterpreterContext context) {
    if (line == null || line.trim().length() == 0) {
      return new InterpreterResult(Code.SUCCESS);
    }

    InterpreterResult result = interpret(line.split("\n"), context);
    return result;
  }

  public InterpreterResult interpret(String[] lines, InterpreterContext context) {
    final IMain imain = flinkIloop.intp();
    
    String[] linesToRun = new String[lines.length + 1];
    for (int i = 0; i < lines.length; i++) {
      linesToRun[i] = lines[i];
    }
    linesToRun[lines.length] = "print(\"\")";

    System.setOut(new PrintStream(out));
    out.reset();
    Code r = null;

    String incomplete = "";
    boolean inComment = false;

    for (int l = 0; l < linesToRun.length; l++) {
      final String s = linesToRun[l];
      // check if next line starts with "." (but not ".." or "./") it is treated as an invocation
      if (l + 1 < linesToRun.length) {
        String nextLine = linesToRun[l + 1].trim();
        boolean continuation = false;
        if (nextLine.isEmpty()
                || nextLine.startsWith("//")         // skip empty line or comment
                || nextLine.startsWith("}")
                || nextLine.startsWith("object")) { // include "} object" for Scala companion object
          continuation = true;
        } else if (!inComment && nextLine.startsWith("/*")) {
          inComment = true;
          continuation = true;
        } else if (inComment && nextLine.lastIndexOf("*/") >= 0) {
          inComment = false;
          continuation = true;
        } else if (nextLine.length() > 1
                && nextLine.charAt(0) == '.'
                && nextLine.charAt(1) != '.'     // ".."
                && nextLine.charAt(1) != '/') {  // "./"
          continuation = true;
        } else if (inComment) {
          continuation = true;
        }
        if (continuation) {
          incomplete += s + "\n";
          continue;
        }
      }

      final String currentCommand = incomplete;

      scala.tools.nsc.interpreter.Results.Result res = null;
      try {
        res = Console.withOut(
          System.out,
          new AbstractFunction0<Results.Result>() {
            @Override
            public Results.Result apply() {
              return imain.interpret(currentCommand + s);
            }
          });
      } catch (Exception e) {
        logger.info("Interpreter exception", e);
        return new InterpreterResult(Code.ERROR, InterpreterUtils.getMostRelevantMessage(e));
      }

      r = getResultCode(res);

      if (r == Code.ERROR) {
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
    if (localMode()) {
      // In localMode we can cancel all running jobs,
      // because the local cluster can only run one job at the time.
      for (JobID job : this.localFlinkCluster.getCurrentlyRunningJobsJava()) {
        logger.info("Stop job: " + job);
        cancelJobLocalMode(job);
      }
    }
  }

  private void cancelJobLocalMode(JobID jobID){
    FiniteDuration timeout = AkkaUtils.getTimeout(this.localFlinkCluster.configuration());
    ActorGateway leader = this.localFlinkCluster.getLeaderGateway(timeout);
    leader.ask(new JobManagerMessages.CancelJob(jobID), timeout);
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
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) {
    return new LinkedList<>();
  }

  private void startFlinkMiniCluster() {
    localFlinkCluster = new LocalFlinkMiniCluster(flinkConf, false);

    try {
      localFlinkCluster.start(true);
    } catch (Exception e){
      throw new RuntimeException("Could not start Flink mini cluster.", e);
    }
  }

  private void stopFlinkMiniCluster() {
    if (localFlinkCluster != null) {
      localFlinkCluster.stop();
      localFlinkCluster = null;
    }
  }

  static final String toString(Object o) {
    return (o instanceof String) ? (String) o : "";
  }
  
}
