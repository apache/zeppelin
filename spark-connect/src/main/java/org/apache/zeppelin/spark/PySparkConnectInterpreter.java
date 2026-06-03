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

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.python.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * PySpark interpreter for Spark Connect.
 * Uses PySpark's native Spark Connect client — Python opens its own
 * Spark Connect session pointed at the same gRPC server as the Java/SQL
 * interpreter. No Py4j bridge.
 */
public class PySparkConnectInterpreter extends PythonInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(PySparkConnectInterpreter.class);

  private InterpreterContext curIntpContext;

  public PySparkConnectInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() throws InterpreterException {
    setProperty("zeppelin.python.useIPython",
        getProperty("zeppelin.pyspark.connect.useIPython", "true"));

    String pythonExec = getPythonExec();
    LOGGER.info("Python executable resolved: {}", pythonExec);

    super.open();

    if (!useIPython()) {
      try {
        bootstrapInterpreter("python/zeppelin_sparkconnect.py");
      } catch (IOException e) {
        LOGGER.error("Fail to bootstrap spark connect", e);
        throw new InterpreterException("Fail to bootstrap spark connect", e);
      }
    }
  }

  @Override
  public void close() throws InterpreterException {
    LOGGER.info("Close PySparkConnectInterpreter");
    super.close();
  }

  @Override
  protected org.apache.zeppelin.python.IPythonInterpreter getIPythonInterpreter()
      throws InterpreterException {
    return getInterpreterInTheSameSessionByClassName(IPySparkConnectInterpreter.class, false);
  }

  @Override
  public org.apache.zeppelin.interpreter.InterpreterResult interpret(String st,
      InterpreterContext context) throws InterpreterException {
    curIntpContext = context;
    return super.interpret(st, context);
  }

  @Override
  protected void preCallPython(InterpreterContext context) {
    callPython(new PythonInterpretRequest(
        "intp.setInterpreterContextInPython()", false, false));
  }

  public void setInterpreterContextInPython() {
    InterpreterContext.set(curIntpContext);
  }

  @Override
  protected Map<String, String> setupPythonEnv() throws IOException {
    Map<String, String> env = super.setupPythonEnv();

    String pythonExec = getPythonExec();
    env.put("PYSPARK_PYTHON", pythonExec);
    LOGGER.info("Set PYSPARK_PYTHON: {}", pythonExec);

    String remote = SparkConnectUtils.buildConnectionString(getProperties(), getUserName());
    env.put("SPARK_REMOTE", remote);
    LOGGER.info("Set SPARK_REMOTE for PySpark native Spark Connect client: {}",
        remote.replaceAll("token=[^;]*", "token=[REDACTED]")
              .replaceAll("user_id=[^;]*", "user_id=[REDACTED]"));

    setupCondaLibraryPath(env, pythonExec);

    LOGGER.info("LD_LIBRARY_PATH: {}", env.get("LD_LIBRARY_PATH"));
    return env;
  }

  /**
   * Get Python executable following Spark's PySpark detection pattern exactly:
   * 1. spark.pyspark.driver.python (from Spark Connect properties)
   * 2. spark.pyspark.python (from Spark Connect properties)
   * 3. PYSPARK_DRIVER_PYTHON (environment variable)
   * 4. PYSPARK_PYTHON (environment variable)
   * 5. zeppelin.python (Zeppelin property) - if set, validate it
   * 6. Default to "python" (let system PATH handle it, just like Spark does)
   */
  @Override
  protected String getPythonExec() {
    String driverPython = getProperty("spark.pyspark.driver.python", "");
    if (StringUtils.isNotBlank(driverPython)) {
      LOGGER.info("Using Python executable from spark.pyspark.driver.python: {}", driverPython);
      return driverPython;
    }

    String pysparkPython = getProperty("spark.pyspark.python", "");
    if (StringUtils.isNotBlank(pysparkPython)) {
      LOGGER.info("Using Python executable from spark.pyspark.python: {}", pysparkPython);
      return pysparkPython;
    }

    String envDriverPython = System.getenv("PYSPARK_DRIVER_PYTHON");
    if (StringUtils.isNotBlank(envDriverPython)) {
      LOGGER.info("Using Python executable from PYSPARK_DRIVER_PYTHON: {}", envDriverPython);
      return envDriverPython;
    }

    String envPysparkPython = System.getenv("PYSPARK_PYTHON");
    if (StringUtils.isNotBlank(envPysparkPython)) {
      LOGGER.info("Using Python executable from PYSPARK_PYTHON: {}", envPysparkPython);
      return envPysparkPython;
    }

    String zeppelinPython = getProperty("zeppelin.python", "");
    if (StringUtils.isNotBlank(zeppelinPython)) {
      LOGGER.info("Using Python executable from zeppelin.python property: {}", zeppelinPython);
      return zeppelinPython;
    }

    LOGGER.info("No Python executable configured, defaulting to 'python' (will use system PATH)");
    return "python";
  }

  private void setupCondaLibraryPath(Map<String, String> env, String pythonExec) {
    if (pythonExec != null && pythonExec.contains("/conda/")) {
      int binIndex = pythonExec.indexOf("/bin/");
      if (binIndex > 0) {
        String condaBase = pythonExec.substring(0, binIndex);
        String condaLib = condaBase + "/lib";
        java.io.File libDir = new java.io.File(condaLib);
        if (libDir.exists() && libDir.isDirectory()) {
          String ldLibraryPath = env.getOrDefault("LD_LIBRARY_PATH", "");
          if (ldLibraryPath.isEmpty()) {
            env.put("LD_LIBRARY_PATH", condaLib);
          } else if (!ldLibraryPath.contains(condaLib)) {
            env.put("LD_LIBRARY_PATH", condaLib + ":" + ldLibraryPath);
          }
          LOGGER.info("Added conda lib directory to LD_LIBRARY_PATH: {}", condaLib);
        }
      }
    }
  }
}
