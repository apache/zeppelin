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

import org.apache.zeppelin.flink.sql.RetractStreamSqlJob;
import org.apache.zeppelin.flink.sql.SingleRowStreamSqlJob;
import org.apache.zeppelin.flink.sql.TimeSeriesStreamSqlJob;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public class FlinkStreamSqlInterpreter extends FlinkSqlInterrpeter {

  public FlinkStreamSqlInterpreter(Properties properties) {
    super(properties);
  }


  @Override
  public void open() throws InterpreterException {
    this.flinkInterpreter =
            getInterpreterInTheSameSessionByClassName(FlinkInterpreter.class);
    this.tbenv = flinkInterpreter.getStreamTableEnvironment();
  }

  @Override
  public void close() throws InterpreterException {

  }

  @Override
  protected void checkLocalProperties(Map<String, String> localProperties)
          throws InterpreterException {

  }

  @Override
  public void callSelect(String sql, InterpreterContext context) throws IOException {
    String streamType = context.getLocalProperties().get("type");
    if (streamType == null) {
      throw new IOException("type must be specified for stream sql");
    }
    if (streamType.equalsIgnoreCase("single")) {
      SingleRowStreamSqlJob streamJob = new SingleRowStreamSqlJob(
              flinkInterpreter.getStreamExecutionEnvironment(),
              flinkInterpreter.getStreamTableEnvironment(), context,
              flinkInterpreter.getDefaultParallelism());
      streamJob.run(sql);
    } else if (streamType.equalsIgnoreCase("ts")) {
      TimeSeriesStreamSqlJob streamJob = new TimeSeriesStreamSqlJob(
              flinkInterpreter.getStreamExecutionEnvironment(),
              flinkInterpreter.getStreamTableEnvironment(), context,
              flinkInterpreter.getDefaultParallelism());
      streamJob.run(sql);
    } else if (streamType.equalsIgnoreCase("retract")) {
      RetractStreamSqlJob streamJob = new RetractStreamSqlJob(
              flinkInterpreter.getStreamExecutionEnvironment(),
              flinkInterpreter.getStreamTableEnvironment(), context,
              flinkInterpreter.getDefaultParallelism());
      streamJob.run(sql);
    } else {
      throw new IOException("Unrecognized stream type: " + streamType);
    }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    this.flinkInterpreter.getZeppelinContext().setInterpreterContext(context);
    this.flinkInterpreter.getZeppelinContext().setNoteGui(context.getNoteGui());
    this.flinkInterpreter.getZeppelinContext().setGui(context.getGui());
    this.flinkInterpreter.getJobManager().cancelJob(context);
  }

  @Override
  public Interpreter.FormType getFormType() throws InterpreterException {
    return Interpreter.FormType.SIMPLE;
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
