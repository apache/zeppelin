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

package org.apache.zeppelin.flink;

import org.apache.flink.python.util.ResourceUtil;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.python.IPythonInterpreter;
import org.apache.zeppelin.python.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class PyFlinkInterpreter extends PythonInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(PyFlinkInterpreter.class);

  private FlinkInterpreter flinkInterpreter;
  private InterpreterContext curInterpreterContext;
  private boolean isOpened = false;

  public PyFlinkInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public void open() throws InterpreterException {
    this.flinkInterpreter = getInterpreterInTheSameSessionByClassName(FlinkInterpreter.class);

    setProperty("zeppelin.python", getProperty("zeppelin.pyflink.python", "python"));
    setProperty("zeppelin.python.useIPython", getProperty("zeppelin.pyflink.useIPython", "true"));
    URL[] urls = new URL[0];
    List<URL> urlList = new LinkedList<>();
    String localRepo = getProperty("zeppelin.interpreter.localRepo");
    if (localRepo != null) {
      File localRepoDir = new File(localRepo);
      if (localRepoDir.exists()) {
        File[] files = localRepoDir.listFiles();
        if (files != null) {
          for (File f : files) {
            try {
              urlList.add(f.toURI().toURL());
            } catch (MalformedURLException e) {
              LOGGER.error("Error", e);
            }
          }
        }
      }
    }

    urls = urlList.toArray(urls);
    ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
    try {
      URLClassLoader newCl = new URLClassLoader(urls, oldCl);
      Thread.currentThread().setContextClassLoader(newCl);
      // must create flink interpreter after ClassLoader is set, otherwise the additional jars
      // can not be loaded by flink repl.
      this.flinkInterpreter = getInterpreterInTheSameSessionByClassName(FlinkInterpreter.class);
      // create Python Process and JVM gateway
      super.open();
    } finally {
      Thread.currentThread().setContextClassLoader(oldCl);
    }

    if (!useIPython()) {
      // Initialize Flink in Python Process
      try {
        bootstrapInterpreter("python/zeppelin_pyflink.py");
      } catch (IOException e) {
        throw new InterpreterException("Fail to bootstrap pyflink", e);
      }
    }
    isOpened = true;
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) throws InterpreterException {
    if (isOpened) {
      // set InterpreterContext in the python thread first, otherwise flink job could not be
      // associated with paragraph in JobListener
      this.curInterpreterContext = context;
      InterpreterResult result =
              super.interpret("intp.setInterpreterContextInPythonThread()", context);
      if (result.code() != InterpreterResult.Code.SUCCESS) {
        throw new InterpreterException("Fail to setInterpreterContextInPythonThread: " +
                result.toString());
      }
    }
    return super.interpret(st, context);
  }

  public void setInterpreterContextInPythonThread() {
    InterpreterContext.set(curInterpreterContext);
  }

  @Override
  protected Map<String, String> setupPythonEnv() throws IOException {
    Map<String, String> envs = super.setupPythonEnv();
    String pythonPath = envs.getOrDefault("PYTHONPATH", "");
    String pyflinkPythonPath = getPyFlinkPythonPath(properties);
    envs.put("PYTHONPATH", pythonPath + ":" + pyflinkPythonPath);
    return envs;
  }

  public static String getPyFlinkPythonPath(Properties properties) throws IOException {
    String flinkHome = System.getenv("FLINK_HOME");
    if (flinkHome != null) {
      File tmpDir = Files.createTempDirectory("zeppelin").toFile();
      List<File> depFiles = null;
      try {
        depFiles = ResourceUtil.extractBuiltInDependencies(tmpDir.getAbsolutePath(), "pyflink", true);
      } catch (InterruptedException e) {
        throw new IOException(e);
      }
      StringBuilder builder = new StringBuilder();
      for (File file : depFiles) {
        LOGGER.info("Adding extracted file to PYTHONPATH: " + file.getAbsolutePath());
        builder.append(file.getAbsolutePath() + ":");
      }
      return builder.toString();
    } else {
      throw new IOException("No FLINK_HOME is specified");
    }
  }

  @Override
  protected IPythonInterpreter getIPythonInterpreter() throws InterpreterException {
    return getInterpreterInTheSameSessionByClassName(IPyFlinkInterpreter.class, false);
  }

  @Override
  public void close() throws InterpreterException {
    super.close();
    if (flinkInterpreter != null) {
      flinkInterpreter.close();
    }
  }

  @Override
  public ZeppelinContext getZeppelinContext() {
    return flinkInterpreter.getZeppelinContext();
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return flinkInterpreter.getProgress(context);
  }

  public org.apache.flink.api.java.ExecutionEnvironment getJavaExecutionEnvironment() {
    return flinkInterpreter.getExecutionEnvironment().getJavaEnv();
  }

  public org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
      getJavaStreamExecutionEnvironment() {
    return flinkInterpreter.getStreamExecutionEnvironment().getJavaEnv();
  }

  public TableEnvironment getJavaBatchTableEnvironment(String planner) {
    return flinkInterpreter.getJavaBatchTableEnvironment(planner);
  }

  public TableEnvironment getJavaStreamTableEnvironment(String planner) {
    return flinkInterpreter.getJavaStreamTableEnvironment(planner);
  }
}
