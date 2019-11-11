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

import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
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

  public IPyFlinkInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() throws InterpreterException {
    FlinkInterpreter pyFlinkInterpreter =
        getInterpreterInTheSameSessionByClassName(FlinkInterpreter.class, false);
    setProperty("zeppelin.python",
            pyFlinkInterpreter.getProperty("zeppelin.pyflink.python", "python"));
    flinkInterpreter = getInterpreterInTheSameSessionByClassName(FlinkInterpreter.class);
    setAdditionalPythonInitFile("python/zeppelin_ipyflink.py");
    super.open();
  }

  @Override
  public BaseZeppelinContext buildZeppelinContext() {
    return flinkInterpreter.getZeppelinContext();
  }

  @Override
  protected Map<String, String> setupIPythonEnv() throws IOException {
    Map<String, String> envs = super.setupIPythonEnv();
    String pythonPath = envs.getOrDefault("PYTHONPATH", "");
    String pyflinkPythonPath = PyFlinkInterpreter.getPyFlinkPythonPath(properties);
    envs.put("PYTHONPATH", pythonPath + ":" + pyflinkPythonPath);
    return envs;
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    super.cancel(context);
    flinkInterpreter.cancel(context);
  }

  @Override
  public void close() throws InterpreterException {
    LOGGER.info("Close IPyFlinkInterpreter");
    super.close();
    if (flinkInterpreter != null) {
      flinkInterpreter.close();
    }
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
}
