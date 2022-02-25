/*
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

import org.apache.zeppelin.interpreter.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public abstract class FlinkSqlInterpreter extends AbstractInterpreter {

  protected static final Logger LOGGER = LoggerFactory.getLogger(FlinkSqlInterpreter.class);

  protected FlinkInterpreter flinkInterpreter;
  protected FlinkShims flinkShims;
  protected ZeppelinContext z;


  public FlinkSqlInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public void open() throws InterpreterException {
    this.flinkInterpreter =
            getInterpreterInTheSameSessionByClassName(FlinkInterpreter.class);
    this.flinkShims = flinkInterpreter.getFlinkShims();
  }

  @Override
  protected InterpreterResult internalInterpret(String st, InterpreterContext context) throws InterpreterException {
    LOGGER.debug("Interpret code: " + st);
    // set ClassLoader of current Thread to be the ClassLoader of Flink scala-shell,
    // otherwise codegen will fail to find classes defined in scala-shell
    ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(flinkInterpreter.getFlinkScalaShellLoader());
      flinkInterpreter.createPlannerAgain();
      flinkInterpreter.setParallelismIfNecessary(context);
      flinkInterpreter.setSavepointPathIfNecessary(context);
      return runSqlList(st, context);
    } finally {
      Thread.currentThread().setContextClassLoader(originClassLoader);
    }
  }

  @Override
  public ZeppelinContext getZeppelinContext() {
    if (flinkInterpreter != null) {
      return flinkInterpreter.getZeppelinContext();
    } else {
      return null;
    }
  }

  public abstract InterpreterResult runSqlList(String st, InterpreterContext context);

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    flinkInterpreter.cancel(context);
  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return FormType.SIMPLE;
  }

  @Override
  public void close() throws InterpreterException {
  }
}
