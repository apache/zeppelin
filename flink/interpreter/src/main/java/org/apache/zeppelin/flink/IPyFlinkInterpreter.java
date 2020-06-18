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

import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.python.IPythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * IPyFlinkInterpreter which use IPython underlying.
 */
public class IPyFlinkInterpreter extends IPythonInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(IPyFlinkInterpreter.class);

  private FlinkInterpreter flinkInterpreter;
  private InterpreterContext curInterpreterContext;
  private boolean opened = false;
  private ClassLoader originalClassLoader;

  public IPyFlinkInterpreter(Properties property) {
    super(property);
  }

  @Override
  public synchronized void open() throws InterpreterException {
    if (opened) {
      return;
    }
    FlinkInterpreter pyFlinkInterpreter =
        getInterpreterInTheSameSessionByClassName(FlinkInterpreter.class, false);
    setProperty("zeppelin.python",
            pyFlinkInterpreter.getProperty("zeppelin.pyflink.python", "python"));
    flinkInterpreter = getInterpreterInTheSameSessionByClassName(FlinkInterpreter.class);
    setAdditionalPythonInitFile("python/zeppelin_ipyflink.py");
    super.open();
    opened = true;
  }

  @Override
  public ZeppelinContext buildZeppelinContext() {
    return flinkInterpreter.getZeppelinContext();
  }

  @Override
  protected Map<String, String> setupKernelEnv() throws IOException {
    Map<String, String> envs = super.setupKernelEnv();
    String pythonPath = envs.getOrDefault("PYTHONPATH", "");
    String pyflinkPythonPath = flinkInterpreter.getFlinkShims().getPyFlinkPythonPath(properties);
    envs.put("PYTHONPATH", pythonPath + ":" + pyflinkPythonPath);
    return envs;
  }

  @Override
  public InterpreterResult internalInterpret(String st,
                                             InterpreterContext context)
          throws InterpreterException {
    try {
      // set InterpreterContext in the python thread first, otherwise flink job could not be
      // associated with paragraph in JobListener
      this.curInterpreterContext = context;
      InterpreterResult result =
              super.internalInterpret("intp.initJavaThread()", context);
      if (result.code() != InterpreterResult.Code.SUCCESS) {
        throw new InterpreterException("Fail to initJavaThread: " +
                result.toString());
      }
      flinkInterpreter.setSavePointIfNecessary(context);
      flinkInterpreter.setParallelismIfNecessary(context);
      return super.internalInterpret(st, context);
    } finally {
      if (getKernelProcessLauncher().isRunning()) {
        InterpreterResult result =
                super.internalInterpret("intp.resetClassLoaderInPythonThread()", context);
        if (result.code() != InterpreterResult.Code.SUCCESS) {
          LOGGER.warn("Fail to resetClassLoaderInPythonThread: " + result.toString());
        }
      }
    }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    flinkInterpreter.cancel(context);
    super.cancel(context);
  }
  
  /**
   * Called by python process.
   */
  public void initJavaThread() {
    InterpreterContext.set(curInterpreterContext);
    originalClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(flinkInterpreter.getFlinkScalaShellLoader());
    flinkInterpreter.createPlannerAgain();
  }

  /**
   * Called by python process.
   */
  public void resetClassLoaderInPythonThread() {
    if (originalClassLoader != null) {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return flinkInterpreter.getProgress(context);
  }

  public boolean isFlink110() {
    return flinkInterpreter.getFlinkVersion().isFlink110();
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
