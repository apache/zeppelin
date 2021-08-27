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

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.python.IPythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.Properties;

/**
 * PySparkInterpreter which use IPython underlying.
 */
public class IPySparkInterpreter extends IPythonInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(IPySparkInterpreter.class);

  private SparkInterpreter sparkInterpreter;
  private boolean opened = false;
  private InterpreterContext curIntpContext;

  public IPySparkInterpreter(Properties property) {
    super(property);
  }

  @Override
  public synchronized void open() throws InterpreterException {
    // IPySparkInterpreter may already be opened in PySparkInterpreter when ipython is available.
    if (opened) {
      return;
    }

    this.sparkInterpreter = getInterpreterInTheSameSessionByClassName(SparkInterpreter.class);
    PySparkInterpreter pySparkInterpreter =
            getInterpreterInTheSameSessionByClassName(PySparkInterpreter.class, false);
    setProperty("zeppelin.python", pySparkInterpreter.getPythonExec(sparkInterpreter.getSparkContext().conf()));

    setProperty("zeppelin.py4j.useAuth",
            sparkInterpreter.getSparkVersion().isSecretSocketSupported() + "");
    SparkConf conf = sparkInterpreter.getSparkContext().getConf();
    // only set PYTHONPATH in embedded, local or yarn-client mode.
    // yarn-cluster will setup PYTHONPATH automatically.
    if (!conf.contains(SparkStringConstants.SUBMIT_DEPLOY_MODE_PROP_NAME) ||
            !conf.get(SparkStringConstants.SUBMIT_DEPLOY_MODE_PROP_NAME).equals("cluster")) {
      setAdditionalPythonPath(PythonUtils.sparkPythonPath());
    }
    setUseBuiltinPy4j(false);
    setAdditionalPythonInitFile("python/zeppelin_ipyspark.py");
    setProperty("zeppelin.py4j.useAuth",
            sparkInterpreter.getSparkVersion().isSecretSocketSupported() + "");
    super.open();
    opened = true;
  }

  @Override
  protected Map<String, String> setupKernelEnv() throws IOException {
    Map<String, String> env = super.setupKernelEnv();
    // set PYSPARK_PYTHON
    SparkConf conf = sparkInterpreter.getSparkContext().getConf();
    if (conf.contains("spark.pyspark.python")) {
      env.put("PYSPARK_PYTHON", conf.get("spark.pyspark.python"));
    }
    return env;
  }

  @Override
  public ZeppelinContext buildZeppelinContext() {
    return sparkInterpreter.getZeppelinContext();
  }

  @Override
  public InterpreterResult interpret(String st,
                                     InterpreterContext context) throws InterpreterException {
    // redirect java stdout/stdout to interpreter output. Because pyspark may call java code.
    PrintStream originalStdout = System.out;
    PrintStream originalStderr = System.err;
    try {
      System.setOut(new PrintStream(context.out));
      System.setErr(new PrintStream(context.out));
      Utils.printDeprecateMessage(sparkInterpreter.getSparkVersion(), context, properties);
      InterpreterContext.set(context);
      String jobGroupId = Utils.buildJobGroupId(context);
      String jobDesc = Utils.buildJobDesc(context);
      String setJobGroupStmt = "sc.setJobGroup('" + jobGroupId + "', '" + jobDesc + "')";
      InterpreterResult result = super.interpret(setJobGroupStmt, context);
      if (result.code().equals(InterpreterResult.Code.ERROR)) {
        return new InterpreterResult(InterpreterResult.Code.ERROR, "Fail to setJobGroup");
      }
      String pool = "None";
      if (context.getLocalProperties().containsKey("pool")) {
        pool = "'" + context.getLocalProperties().get("pool") + "'";
      }
      String setPoolStmt = "sc.setLocalProperty('spark.scheduler.pool', " + pool + ")";
      result = super.interpret(setPoolStmt, context);
      if (result.code().equals(InterpreterResult.Code.ERROR)) {
        return new InterpreterResult(InterpreterResult.Code.ERROR, "Fail to setPool");
      }

      this.curIntpContext = context;
      String setInptContextStmt = "intp.setInterpreterContextInPython()";
      result = super.interpret(setInptContextStmt, context);
      if (result.code().equals(InterpreterResult.Code.ERROR)) {
        return new InterpreterResult(InterpreterResult.Code.ERROR, "Fail to setCurIntpContext");
      }

      return super.interpret(st, context);
    } finally {
      System.setOut(originalStdout);
      System.setErr(originalStderr);
    }
  }

  // Python side will call InterpreterContext.get() too, but it is in a different thread other than the
  // java interpreter thread. So we should call this method in python side as well.
  public void setInterpreterContextInPython() {
    InterpreterContext.set(curIntpContext);
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    super.cancel(context);
    sparkInterpreter.cancel(context);
  }

  @Override
  public void close() throws InterpreterException {
    LOGGER.info("Close IPySparkInterpreter");
    super.close();
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return sparkInterpreter.getProgress(context);
  }

  public boolean isSpark1() {
    return sparkInterpreter.getSparkVersion().getMajorVersion() == 1;
  }

  public boolean isSpark3() {
    return sparkInterpreter.getSparkVersion().getMajorVersion() == 3;
  }

  public JavaSparkContext getJavaSparkContext() {
    return sparkInterpreter.getJavaSparkContext();
  }

  public Object getSQLContext() {
    return sparkInterpreter.getSQLContext();
  }

  public Object getSparkSession() {
    return sparkInterpreter.getSparkSession();
  }
}
