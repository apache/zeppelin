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

import org.apache.zeppelin.flink.sql.AppendStreamSqlJob;
import org.apache.zeppelin.flink.sql.SingleRowStreamSqlJob;
import org.apache.zeppelin.flink.sql.UpdateStreamSqlJob;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

import java.io.IOException;
import java.util.Properties;

public class FlinkStreamSqlInterpreter extends FlinkSqlInterpreter {

  public FlinkStreamSqlInterpreter(Properties properties) {
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
            sql -> callInnerSelect(sql));

    flinkInterpreter.getFlinkShims().initInnerStreamSqlInterpreter(flinkSqlContext);
  }

  public void callInnerSelect(String sql) {
    InterpreterContext context = InterpreterContext.get();
    String streamType = context.getLocalProperties().getOrDefault("type", "update");
    if (streamType.equalsIgnoreCase("single")) {
      SingleRowStreamSqlJob streamJob = new SingleRowStreamSqlJob(
              flinkInterpreter.getStreamExecutionEnvironment(),
              flinkInterpreter.getJavaStreamTableEnvironment(),
              flinkInterpreter.getJobManager(),
              context,
              flinkInterpreter.getDefaultParallelism(),
              flinkInterpreter.getFlinkShims());
      try {
        streamJob.run(sql);
      } catch (IOException e) {
        throw new RuntimeException("Fail to run single type stream job", e);
      }
    } else if (streamType.equalsIgnoreCase("append")) {
      AppendStreamSqlJob streamJob = new AppendStreamSqlJob(
              flinkInterpreter.getStreamExecutionEnvironment(),
              flinkInterpreter.getStreamTableEnvironment(),
              flinkInterpreter.getJobManager(),
              context,
              flinkInterpreter.getDefaultParallelism(),
              flinkInterpreter.getFlinkShims());
      try {
        streamJob.run(sql);
      } catch (IOException e) {
        throw new RuntimeException("Fail to run append type stream job", e);
      }
    } else if (streamType.equalsIgnoreCase("update")) {
      UpdateStreamSqlJob streamJob = new UpdateStreamSqlJob(
              flinkInterpreter.getStreamExecutionEnvironment(),
              flinkInterpreter.getStreamTableEnvironment(),
              flinkInterpreter.getJobManager(),
              context,
              flinkInterpreter.getDefaultParallelism(),
              flinkInterpreter.getFlinkShims());
      try {
        streamJob.run(sql);
      } catch (IOException e) {
        throw new RuntimeException("Fail to run update type stream job", e);
      }
    } else {
      throw new RuntimeException("Unrecognized stream type: " + streamType);
    }
  }

  @Override
  public InterpreterResult runSqlList(String st, InterpreterContext context) {
    return flinkShims.runSqlList(st, context, false);
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    int maxConcurrency = Integer.parseInt(
            getProperty("zeppelin.flink.concurrentStreamSql.max", "10"));
    return SchedulerFactory.singleton().createOrGetParallelScheduler(
            FlinkStreamSqlInterpreter.class.getName() + this.hashCode(), maxConcurrency);
  }
}
