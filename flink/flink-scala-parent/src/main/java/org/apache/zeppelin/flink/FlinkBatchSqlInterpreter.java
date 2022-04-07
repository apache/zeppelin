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

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

import java.util.Properties;

public class FlinkBatchSqlInterpreter extends FlinkSqlInterpreter {

  public FlinkBatchSqlInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public void open() throws InterpreterException {
    super.open();
    FlinkSqlContext flinkSqlContext = new FlinkSqlContext(
            flinkInterpreter.getExecutionEnvironment().getJavaEnv(),
            flinkInterpreter.getStreamExecutionEnvironment().getJavaEnv(),
            flinkInterpreter.getJavaBatchTableEnvironment("blink"),
            flinkInterpreter.getJavaStreamTableEnvironment(),
            flinkInterpreter.getZeppelinContext(),
            null);
    flinkInterpreter.getFlinkShims().initInnerBatchSqlInterpreter(flinkSqlContext);
  }

  @Override
  public InterpreterResult runSqlList(String st, InterpreterContext context) {
    return flinkShims.runSqlList(st, context, true);
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return flinkInterpreter.getProgress(context);
  }

  @Override
  public Scheduler getScheduler() {
    int maxConcurrency = Integer.parseInt(properties.getProperty(
            "zeppelin.flink.concurrentBatchSql.max", "10"));
    return SchedulerFactory.singleton().createOrGetParallelScheduler(
            FlinkBatchSqlInterpreter.class.getName(), maxConcurrency);
  }
}
