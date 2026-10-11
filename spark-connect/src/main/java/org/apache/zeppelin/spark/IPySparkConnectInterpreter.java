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

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.python.IPythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * PySpark Connect Interpreter which uses IPython underlying.
 * Uses PySpark's native Spark Connect client — IPython opens its own
 * Spark Connect session pointed at the same gRPC server as the Java/SQL
 * interpreter. No Py4j bridge.
 */
public class IPySparkConnectInterpreter extends IPythonInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(IPySparkConnectInterpreter.class);

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

    this.pySparkConnectInterpreter =
        getInterpreterInTheSameSessionByClassName(PySparkConnectInterpreter.class, false);

    if (pySparkConnectInterpreter != null) {
      setProperty("zeppelin.python", pySparkConnectInterpreter.getPythonExec());
    }
    setAdditionalPythonInitFile("python/zeppelin_sparkconnect.py");
    super.open();
    opened = true;
  }

  @Override
  protected Map<String, String> setupKernelEnv() throws IOException {
    Map<String, String> envs = super.setupKernelEnv();
    String remote = SparkConnectUtils.buildConnectionString(getProperties(), getUserName());
    envs.put("SPARK_REMOTE", remote);
    LOGGER.info("Set SPARK_REMOTE for IPython native Spark Connect client: {}",
        remote.replaceAll("token=[^;]*", "token=[REDACTED]")
              .replaceAll("user_id=[^;]*", "user_id=[REDACTED]"));
    return envs;
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

  @Override
  public void close() throws InterpreterException {
    LOGGER.info("Close IPySparkConnectInterpreter (opened={})", opened);
    try {
      super.close();
    } finally {
      opened = false;
      pySparkConnectInterpreter = null;
      LOGGER.info("IPySparkConnectInterpreter closed and state reset — ready for re-open");
    }
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }
}
