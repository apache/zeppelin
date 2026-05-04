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

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.python.IPythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * PySpark Connect Interpreter which uses IPython underlying.
 * Reuses the Java SparkSession from SparkConnectInterpreter via Py4j.
 */
public class IPySparkConnectInterpreter extends IPythonInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(IPySparkConnectInterpreter.class);

  private SparkConnectInterpreter sparkConnectInterpreter;
  private PySparkConnectInterpreter pySparkConnectInterpreter;
  private boolean opened = false;
  private InterpreterContext curIntpContext;

  public IPySparkConnectInterpreter(Properties property) {
    super(property);
  }

  @Override
  public synchronized void open() throws InterpreterException {
    if (opened) {
      return;
    }

    this.sparkConnectInterpreter =
        getInterpreterInTheSameSessionByClassName(SparkConnectInterpreter.class);
    this.pySparkConnectInterpreter =
        getInterpreterInTheSameSessionByClassName(PySparkConnectInterpreter.class, false);

    sparkConnectInterpreter.open();

    setProperty("zeppelin.python", pySparkConnectInterpreter.getPythonExec());
    setUseBuiltinPy4j(true);
    setAdditionalPythonInitFile("python/zeppelin_isparkconnect.py");
    super.open();
    opened = true;
  }

  @Override
  public org.apache.zeppelin.interpreter.InterpreterResult interpret(String st,
      InterpreterContext context) throws InterpreterException {
    InterpreterContext.set(context);
    this.curIntpContext = context;
    String setInptContextStmt = "intp.setInterpreterContextInPython()";
    org.apache.zeppelin.interpreter.InterpreterResult result =
        super.interpret(setInptContextStmt, context);
    if (result.code().equals(org.apache.zeppelin.interpreter.InterpreterResult.Code.ERROR)) {
      return new org.apache.zeppelin.interpreter.InterpreterResult(
          org.apache.zeppelin.interpreter.InterpreterResult.Code.ERROR,
          "Fail to setCurIntpContext");
    }

    return super.interpret(st, context);
  }

  public void setInterpreterContextInPython() {
    InterpreterContext.set(curIntpContext);
  }

  public SparkSession getSparkSession() {
    if (sparkConnectInterpreter != null) {
      return sparkConnectInterpreter.getSparkSession();
    }
    return null;
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    super.cancel(context);
    if (sparkConnectInterpreter != null) {
      sparkConnectInterpreter.cancel(context);
    }
  }

  @Override
  public void close() throws InterpreterException {
    LOGGER.info("Close IPySparkConnectInterpreter (opened={})", opened);
    try {
      super.close();
    } finally {
      opened = false;
      sparkConnectInterpreter = null;
      pySparkConnectInterpreter = null;
      LOGGER.info("IPySparkConnectInterpreter closed and state reset — ready for re-open");
    }
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }

  public int getMaxResult() {
    if (sparkConnectInterpreter != null) {
      return sparkConnectInterpreter.getMaxResult();
    }
    return 1000;
  }

  @SuppressWarnings("unchecked")
  public String formatDataFrame(Object df, int maxResult) {
    return SparkConnectUtils.showDataFrame((Dataset<Row>) df, maxResult);
  }
}
