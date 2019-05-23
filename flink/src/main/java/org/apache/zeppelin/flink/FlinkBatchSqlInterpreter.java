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


import com.google.common.collect.Lists;
import org.apache.flink.table.api.Table;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class FlinkBatchSqlInterpreter extends FlinkSqlInterrpeter {

  private FlinkZeppelinContext z;

  public FlinkBatchSqlInterpreter(Properties properties) {
    super(properties);
  }


  @Override
  public void open() throws InterpreterException {
    super.open();
    this.tbenv = flinkInterpreter.getBatchTableEnvironment();
    this.z = flinkInterpreter.getZeppelinContext();
  }

  @Override
  public void close() throws InterpreterException {

  }

  @Override
  public void callSelect(String sql, InterpreterContext context) throws IOException {
    Table table = this.tbenv.sqlQuery(sql);
    z.setCurrentSql(sql);
    String result = z.showData(table);
    context.out.write(result);
  }

  protected void checkLocalProperties(Map<String, String> localProperties)
          throws InterpreterException {
    List<String> validLocalProperties = Lists.newArrayList("parallelism");
    for (String key : localProperties.keySet()) {
      if (!validLocalProperties.contains(key)) {
        throw new InterpreterException("Invalid property: " + key + ", Only the following " +
                "properties are valid: " + validLocalProperties);
      }
    }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    flinkInterpreter.getJobManager().cancelJob(context);
  }

  @Override
  public FormType getFormType() throws InterpreterException {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    int maxConcurrency = Integer.parseInt(
            getProperty("zeppelin.flink.concurrentBatchSql.max", "10"));
    return SchedulerFactory.singleton().createOrGetParallelScheduler(
            FlinkBatchSqlInterpreter.class.getName() + this.hashCode(), maxConcurrency);
  }
}
