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

import org.apache.commons.lang.StringUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.WrappedInterpreter;
import org.apache.zeppelin.python.IPythonInterpreter;
import org.apache.zeppelin.python.PythonInterpreter;
import org.apache.zeppelin.spark.dep.SparkDependencyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 *  Interpreter for PySpark, it is the first implementation of interpreter for PySpark, so with less
 *  features compared to IPySparkInterpreter, but requires less prerequisites than
 *  IPySparkInterpreter, only python is required.
 */
public class PySparkInterpreter extends PythonInterpreter {

  private static Logger LOGGER = LoggerFactory.getLogger(PySparkInterpreter.class);

  private SparkInterpreter sparkInterpreter;

  public PySparkInterpreter(Properties property) {
    super(property);
    this.useBuiltinPy4j = false;
  }

  @Override
  public void open() throws InterpreterException {
    setProperty("zeppelin.python.useIPython", getProperty("zeppelin.pyspark.useIPython", "true"));

    // create SparkInterpreter in JVM side TODO(zjffdu) move to SparkInterpreter
    DepInterpreter depInterpreter = getDepInterpreter();
    // load libraries from Dependency Interpreter
    URL [] urls = new URL[0];
    List<URL> urlList = new LinkedList<>();

    if (depInterpreter != null) {
      SparkDependencyContext depc = depInterpreter.getDependencyContext();
      if (depc != null) {
        List<File> files = depc.getFiles();
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
      // must create spark interpreter after ClassLoader is set, otherwise the additional jars
      // can not be loaded by spark repl.
      this.sparkInterpreter = getSparkInterpreter();
      setProperty("zeppelin.py4j.useAuth",
          sparkInterpreter.getSparkVersion().isSecretSocketSupported() + "");
      // create Python Process and JVM gateway
      super.open();
    } finally {
      Thread.currentThread().setContextClassLoader(oldCl);
    }

    if (!useIPython()) {
      // Initialize Spark in Python Process
      try {
        bootstrapInterpreter("python/zeppelin_pyspark.py");
      } catch (IOException e) {
        throw new InterpreterException("Fail to bootstrap pyspark", e);
      }
    }
  }

  @Override
  public void close() throws InterpreterException {
    super.close();
    if (sparkInterpreter != null) {
      sparkInterpreter.close();
    }
  }

  @Override
  protected BaseZeppelinContext createZeppelinContext() {
    return sparkInterpreter.getZeppelinContext();
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {
    return super.interpret(st, context);
  }

  @Override
  protected void preCallPython(InterpreterContext context) {
    String jobGroup = Utils.buildJobGroupId(context);
    String jobDesc = Utils.buildJobDesc(context);
    callPython(new PythonInterpretRequest(
        String.format("if 'sc' in locals():\n\tsc.setJobGroup('%s', '%s')", jobGroup, jobDesc),
        false, false));

    String pool = "None";
    if (context.getLocalProperties().containsKey("pool")) {
      pool = "'" + context.getLocalProperties().get("pool") + "'";
    }
    String setPoolStmt = "sc.setLocalProperty('spark.scheduler.pool', " + pool + ")";
    callPython(new PythonInterpretRequest(setPoolStmt, false, false));
  }

  // Run python shell
  // Choose python in the order of
  // spark.pyspark.driver.python > spark.pyspark.python > PYSPARK_DRIVER_PYTHON > PYSPARK_PYTHON
  @Override
  protected String getPythonExec() {
    if (!StringUtils.isBlank(getProperty("spark.pyspark.driver.python", ""))) {
      return properties.getProperty("spark.pyspark.driver.python");
    }
    if (!StringUtils.isBlank(getProperty("spark.pyspark.python", ""))) {
      return properties.getProperty("spark.pyspark.python");
    }
    if (System.getenv("PYSPARK_PYTHON") != null) {
      return System.getenv("PYSPARK_PYTHON");
    }
    if (System.getenv("PYSPARK_DRIVER_PYTHON") != null) {
      return System.getenv("PYSPARK_DRIVER_PYTHON");
    }
    return "python";
  }

  @Override
  protected IPythonInterpreter getIPythonInterpreter() {
    IPySparkInterpreter iPython = null;
    Interpreter p = getInterpreterInTheSameSessionByClassName(IPySparkInterpreter.class.getName());
    while (p instanceof WrappedInterpreter) {
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }
    iPython = (IPySparkInterpreter) p;
    return iPython;
  }

  private SparkInterpreter getSparkInterpreter() throws InterpreterException {
    LazyOpenInterpreter lazy = null;
    SparkInterpreter spark = null;
    Interpreter p = getInterpreterInTheSameSessionByClassName(SparkInterpreter.class.getName());

    while (p instanceof WrappedInterpreter) {
      if (p instanceof LazyOpenInterpreter) {
        lazy = (LazyOpenInterpreter) p;
      }
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }
    spark = (SparkInterpreter) p;

    if (lazy != null) {
      lazy.open();
    }
    return spark;
  }


  public SparkZeppelinContext getZeppelinContext() {
    if (sparkInterpreter != null) {
      return sparkInterpreter.getZeppelinContext();
    } else {
      return null;
    }
  }

  public JavaSparkContext getJavaSparkContext() {
    if (sparkInterpreter == null) {
      return null;
    } else {
      return new JavaSparkContext(sparkInterpreter.getSparkContext());
    }
  }

  public Object getSparkSession() {
    if (sparkInterpreter == null) {
      return null;
    } else {
      return sparkInterpreter.getSparkSession();
    }
  }

  public SparkConf getSparkConf() {
    JavaSparkContext sc = getJavaSparkContext();
    if (sc == null) {
      return null;
    } else {
      return sc.getConf();
    }
  }

  public SQLContext getSQLContext() {
    if (sparkInterpreter == null) {
      return null;
    } else {
      return sparkInterpreter.getSQLContext();
    }
  }

  private DepInterpreter getDepInterpreter() {
    Interpreter p = getInterpreterInTheSameSessionByClassName(DepInterpreter.class.getName());
    if (p == null) {
      return null;
    }

    while (p instanceof WrappedInterpreter) {
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }
    return (DepInterpreter) p;
  }

  public boolean isSpark2() {
    return sparkInterpreter.getSparkVersion().newerThanEquals(SparkVersion.SPARK_2_0_0);
  }
}
