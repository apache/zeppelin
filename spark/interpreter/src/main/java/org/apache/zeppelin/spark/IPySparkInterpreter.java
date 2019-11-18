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
import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.WrappedInterpreter;
import org.apache.zeppelin.python.IPythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * PySparkInterpreter which use IPython underlying.
 */
public class IPySparkInterpreter extends IPythonInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(IPySparkInterpreter.class);

  private SparkInterpreter sparkInterpreter;

  public IPySparkInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() throws InterpreterException {
    setProperty("zeppelin.python",
        PySparkInterpreter.getPythonExec(getProperties()));
    sparkInterpreter = getSparkInterpreter();
    SparkConf conf = sparkInterpreter.getSparkContext().getConf();
    // only set PYTHONPATH in embedded, local or yarn-client mode.
    // yarn-cluster will setup PYTHONPATH automatically.
    if (!conf.contains("spark.submit.deployMode") ||
        !conf.get("spark.submit.deployMode").equals("cluster")) {
      setAdditionalPythonPath(PythonUtils.sparkPythonPath());
      setAddBulitinPy4j(false);
    }
    setAdditionalPythonInitFile("python/zeppelin_ipyspark.py");
    setProperty("zeppelin.py4j.useAuth",
        sparkInterpreter.getSparkVersion().isSecretSocketSupported() + "");
    super.open();
  }

  @Override
  protected Map<String, String> setupIPythonEnv() throws IOException {
    Map<String, String> env = super.setupIPythonEnv();
    // set PYSPARK_PYTHON
    SparkConf conf = sparkInterpreter.getSparkContext().getConf();
    if (conf.contains("spark.pyspark.python")) {
      env.put("PYSPARK_PYTHON", conf.get("spark.pyspark.python"));
    }
    return env;
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

  @Override
  public BaseZeppelinContext buildZeppelinContext() {
    return sparkInterpreter.getZeppelinContext();
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    InterpreterContext.set(context);
    sparkInterpreter.populateSparkWebUrl(context);
    String jobGroupId = Utils.buildJobGroupId(context);
    String jobDesc = "Started by: " + Utils.getUserName(context.getAuthenticationInfo());
    String setJobGroupStmt = "sc.setJobGroup('" +  jobGroupId + "', '" + jobDesc + "')";
    InterpreterResult result = super.interpret(setJobGroupStmt, context);
    if (result.code().equals(InterpreterResult.Code.ERROR)) {
      return new InterpreterResult(InterpreterResult.Code.ERROR, "Fail to setJobGroup");
    }
    return super.interpret(st, context);
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    if (sparkInterpreter != null) {
      super.cancel(context);
      sparkInterpreter.cancel(context);
    }
  }

  @Override
  public void close() throws InterpreterException {
    if (sparkInterpreter != null) {
      super.close();
      sparkInterpreter.close();
    }
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return sparkInterpreter.getProgress(context);
  }

  public boolean isSpark2() {
    return sparkInterpreter.getSparkVersion().newerThanEquals(SparkVersion.SPARK_2_0_0);
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
