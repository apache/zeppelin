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

import org.apache.spark.SparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;

/**
 * Spark SQL interpreter for Zeppelin.
 */
public class SparkSqlInterpreter extends Interpreter {
  private Logger logger = LoggerFactory.getLogger(SparkSqlInterpreter.class);

  private SparkInterpreter sparkInterpreter;

  public SparkSqlInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() throws InterpreterException {
    this.sparkInterpreter = getInterpreterInTheSameSessionByClassName(SparkInterpreter.class);
  }

  public boolean concurrentSQL() {
    return Boolean.parseBoolean(getProperty("zeppelin.spark.concurrentSQL"));
  }

  @Override
  public void close() {}

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context)
      throws InterpreterException {
    if (sparkInterpreter.isUnsupportedSparkVersion()) {
      return new InterpreterResult(Code.ERROR, "Spark "
          + sparkInterpreter.getSparkVersion().toString() + " is not supported");
    }

    sparkInterpreter.getZeppelinContext().setInterpreterContext(context);
    SQLContext sqlc = sparkInterpreter.getSQLContext();
    SparkContext sc = sqlc.sparkContext();
    sc.setLocalProperty("spark.scheduler.pool", context.getLocalProperties().get("pool"));
    sc.setJobGroup(Utils.buildJobGroupId(context), Utils.buildJobDesc(context), false);

    try {
      String effectiveSQL = Boolean.parseBoolean(getProperty("zeppelin.spark.sql.interpolation")) ?
          interpolate(st, context.getResourcePool()) : st;
      Method method = sqlc.getClass().getMethod("sql", String.class);
      String msg = sparkInterpreter.getZeppelinContext().showData(
          method.invoke(sqlc, effectiveSQL));
      return new InterpreterResult(Code.SUCCESS, msg);
    } catch (Exception e) {
      if (Boolean.parseBoolean(getProperty("zeppelin.spark.sql.stacktrace"))) {
        throw new InterpreterException(e);
      }
      logger.error("Invocation target exception", e);
      String msg = e.getMessage()
              + "\nset zeppelin.spark.sql.stacktrace = true to see full stacktrace";
      return new InterpreterResult(Code.ERROR, msg);
    } finally {
      sc.clearJobGroup();
    }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    SparkContext sc = sparkInterpreter.getSparkContext();
    sc.cancelJobGroup(Utils.buildJobGroupId(context));
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }


  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return sparkInterpreter.getProgress(context);
  }

  @Override
  public Scheduler getScheduler() {
    if (concurrentSQL()) {
      int maxConcurrency = Integer.parseInt(getProperty("zeppelin.spark.concurrentSQL.max", "10"));
      return SchedulerFactory.singleton().createOrGetParallelScheduler(
          SparkSqlInterpreter.class.getName() + this.hashCode(), maxConcurrency);
    } else {
      // getSparkInterpreter() calls open() inside.
      // That means if SparkInterpreter is not opened, it'll wait until SparkInterpreter open.
      // In this moment UI displays 'READY' or 'FINISHED' instead of 'PENDING' or 'RUNNING'.
      // It's because of scheduler is not created yet, and scheduler is created by this function.
      // Therefore, we can still use getSparkInterpreter() here, but it's better and safe
      // to getSparkInterpreter without opening it.
      try {
        return getInterpreterInTheSameSessionByClassName(SparkInterpreter.class, false)
            .getScheduler();
      } catch (InterpreterException e) {
        throw new RuntimeException("Fail to getScheduler", e);
      }
    }
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) {
    return null;
  }
}
