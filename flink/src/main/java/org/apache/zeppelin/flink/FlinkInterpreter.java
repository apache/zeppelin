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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.minicluster.LocalFlinkMiniCluster;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Console;
import scala.None;
import scala.Some;
import scala.tools.nsc.Settings;
import scala.tools.nsc.settings.MutableSettings.BooleanSetting;
import scala.tools.nsc.settings.MutableSettings.PathSetting;

/**
 * Interpreter for Apache Flink (http://flink.apache.org)
 */
public class FlinkInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(FlinkInterpreter.class);
  private Settings settings;
  private ByteArrayOutputStream out;
  private FlinkIMain imain;
  private Map<String, Object> binder;
  private ExecutionEnvironment env;
  private Configuration flinkConf;
  private LocalFlinkMiniCluster localFlinkCluster;

  public FlinkInterpreter(Properties property) {
    super(property);
  }

  static {
    Interpreter.register(
        "flink",
        "flink",
        FlinkInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
          .add("local", "true", "Run flink locally")
          .add("jobmanager.rpc.address", "localhost", "Flink cluster")
          .add("jobmanager.rpc.port", "6123", "Flink cluster")
          .build()
    );
  }

  @Override
  public void open() {
    URL[] urls = getClassloaderUrls();
    this.settings = new Settings();

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
    settings.explicitParentLoader_$eq(new Some<ClassLoader>(Thread.currentThread()
        .getContextClassLoader()));
    BooleanSetting b = (BooleanSetting) settings.usejavacp();
    b.v_$eq(true);
    settings.scala$tools$nsc$settings$StandardScalaSettings$_setter_$usejavacp_$eq(b);

    out = new ByteArrayOutputStream();
    imain = new FlinkIMain(settings, new PrintWriter(out));

    initializeFlinkEnv();
  }

  private boolean localMode() {
    return Boolean.parseBoolean(getProperty("local"));
  }

  private String getRpcAddress() {
    if (localMode()) {
      return "localhost";
    } else {
      return getProperty("jobmanager.rpc.address");
    }
  }

  private int getRpcPort() {
    if (localMode()) {
      return localFlinkCluster.getJobManagerRPCPort();
    } else {
      return Integer.parseInt(getProperty("jobmanager.rpc.port"));
    }
  }

  private void initializeFlinkEnv() {
    // prepare bindings
    imain.interpret("@transient var _binder = new java.util.HashMap[String, Object]()");
    binder = (Map<String, Object>) getValue("_binder");

    flinkConf = new org.apache.flink.configuration.Configuration();
    Properties intpProperty = getProperty();
    for (Object k : intpProperty.keySet()) {
      String key = (String) k;
      String val = toString(intpProperty.get(key));
      flinkConf.setString(key, val);
    }

    if (localMode()) {
      startFlinkMiniCluster();
    }

    env = new FlinkEnvironment(getRpcAddress(), getRpcPort(), imain);
    binder.put("env", new org.apache.flink.api.scala.ExecutionEnvironment(env));

    // do import and create val
    imain.interpret("@transient val env = "
        + "_binder.get(\"env\")"
        + ".asInstanceOf[org.apache.flink.api.scala.ExecutionEnvironment]");

    imain.interpret("import org.apache.flink.api.scala._");
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

  public Object getValue(String name) {
    Object ret = imain.valueOfTerm(name);
    if (ret instanceof None) {
      return null;
    } else if (ret instanceof Some) {
      return ((Some) ret).get();
    } else {
      return ret;
    }
  }

  @Override
  public void close() {
    imain.close();

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
    String[] linesToRun = new String[lines.length + 1];
    for (int i = 0; i < lines.length; i++) {
      linesToRun[i] = lines[i];
    }
    linesToRun[lines.length] = "print(\"\")";

    Console.setOut(out);
    out.reset();
    Code r = null;

    String incomplete = "";
    for (String s : linesToRun) {
      scala.tools.nsc.interpreter.Results.Result res = null;
      try {
        res = imain.interpret(incomplete + s);
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
    return new LinkedList<String>();
  }

  private void startFlinkMiniCluster() {
    localFlinkCluster = new LocalFlinkMiniCluster(flinkConf, false);
    localFlinkCluster.waitForTaskManagersToBeRegistered();
  }

  private void stopFlinkMiniCluster() {
    if (localFlinkCluster != null) {
      localFlinkCluster.shutdown();
      localFlinkCluster = null;
    }
  }

  static final String toString(Object o) {
    return (o instanceof String) ? (String) o : "";
  }

}
