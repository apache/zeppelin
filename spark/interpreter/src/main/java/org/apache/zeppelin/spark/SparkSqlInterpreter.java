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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.spark.SparkContext;
import org.apache.spark.sql.AnalysisException;
import org.apache.zeppelin.interpreter.AbstractInterpreter;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.util.SqlSplitter;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;

/**
 * Spark SQL interpreter for Zeppelin.
 */
public class SparkSqlInterpreter extends AbstractInterpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(SparkSqlInterpreter.class);

  private SparkInterpreter sparkInterpreter;
  private SqlSplitter sqlSplitter;

  public SparkSqlInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() throws InterpreterException {
    this.sparkInterpreter = getInterpreterInTheSameSessionByClassName(SparkInterpreter.class);
    this.sqlSplitter = new SqlSplitter();
  }

  public boolean concurrentSQL() {
    return Boolean.parseBoolean(getProperty("zeppelin.spark.concurrentSQL"));
  }

  @Override
  public void close() {}

  @Override
  protected boolean isInterpolate() {
    return Boolean.parseBoolean(getProperty("zeppelin.spark.sql.interpolation", "false"));
  }

  @Override
  public ZeppelinContext getZeppelinContext() {
    return null;
  }

  @Override
  public InterpreterResult internalInterpret(String st, InterpreterContext context)
      throws InterpreterException {
    if (sparkInterpreter.isUnsupportedSparkVersion()) {
      return new InterpreterResult(Code.ERROR, "Spark "
          + sparkInterpreter.getSparkVersion().toString() + " is not supported");
    }
    Utils.printDeprecateMessage(sparkInterpreter.getSparkVersion(), context, properties);
    sparkInterpreter.getZeppelinContext().setInterpreterContext(context);
    Object sqlContext = sparkInterpreter.getSQLContext();
    SparkContext sc = sparkInterpreter.getSparkContext();

    List<String> sqls = sqlSplitter.splitSql(st);
    int maxResult = Integer.parseInt(context.getLocalProperties().getOrDefault("limit",
            "" + sparkInterpreter.getZeppelinContext().getMaxResult()));

    sc.setLocalProperty("spark.scheduler.pool", context.getLocalProperties().get("pool"));
    sc.setJobGroup(Utils.buildJobGroupId(context), Utils.buildJobDesc(context), false);
    String curSql = null;
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      if (!sparkInterpreter.isScala212()) {
        // TODO(zjffdu) scala 2.12 still doesn't work for codegen (ZEPPELIN-4627)
      Thread.currentThread().setContextClassLoader(sparkInterpreter.getScalaShellClassLoader());
      }
      Method method = sqlContext.getClass().getMethod("sql", String.class);
      for (String sql : sqls) {
        curSql = sql;
        // Put sql into InterpreterContext's local properties, so that ZeppelinContext#show
        // can use this context info
        context.getLocalProperties().put("sql", curSql);
        String result = sparkInterpreter.getZeppelinContext()
                .showData(method.invoke(sqlContext, sql), maxResult);
        context.out.write(result);
      }
      context.out.flush();
    } catch (Exception e) {
      try {
        if (e.getCause() instanceof AnalysisException) {
          // just return the error message from spark if it is AnalysisException
          context.out.write(e.getCause().getMessage());
          context.out.flush();
          return new InterpreterResult(Code.ERROR);
        } else {
          LOGGER.error("Error happens in sql: {}", curSql, e);
          context.out.write("\nError happens in sql: " + curSql + "\n");
          if (Boolean.parseBoolean(getProperty("zeppelin.spark.sql.stacktrace", "false"))) {
            if (e.getCause() != null) {
              context.out.write(ExceptionUtils.getStackTrace(e.getCause()));
            } else {
              context.out.write(ExceptionUtils.getStackTrace(e));
            }
          } else {
            StringBuilder msgBuilder = new StringBuilder();
            if (e.getCause() != null) {
              msgBuilder.append(e.getCause().getMessage());
            } else {
              msgBuilder.append(e.getMessage());
            }
            msgBuilder.append("\nset zeppelin.spark.sql.stacktrace = true to see full stacktrace");
            context.out.write(msgBuilder.toString());
          }
          context.out.flush();
          return new InterpreterResult(Code.ERROR);
        }
      } catch (IOException ex) {
        LOGGER.error("Fail to write output", ex);
        return new InterpreterResult(Code.ERROR);
      }
    } finally {
      sc.clearJobGroup();
      if (!sparkInterpreter.isScala212()) {
        Thread.currentThread().setContextClassLoader(originalClassLoader);
      }
    }

    return new InterpreterResult(Code.SUCCESS);
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
}
