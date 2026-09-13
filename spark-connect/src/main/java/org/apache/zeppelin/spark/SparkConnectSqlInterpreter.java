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
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.zeppelin.interpreter.AbstractInterpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.util.SqlSplitter;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Spark Connect SQL interpreter for Zeppelin.
 * Delegates to SparkConnectInterpreter for the SparkSession, providing
 * dedicated SQL execution with concurrent query support.
 */
public class SparkConnectSqlInterpreter extends AbstractInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkConnectSqlInterpreter.class);

  private SparkConnectInterpreter sparkConnectInterpreter;
  private SqlSplitter sqlSplitter;

  public SparkConnectSqlInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public void open() throws InterpreterException {
    this.sparkConnectInterpreter =
        getInterpreterInTheSameSessionByClassName(SparkConnectInterpreter.class);
    this.sqlSplitter = new SqlSplitter();
  }

  @Override
  public void close() throws InterpreterException {
    sparkConnectInterpreter = null;
  }

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
    SparkSession sparkSession = sparkConnectInterpreter.getSparkSession();
    if (sparkSession == null) {
      return new InterpreterResult(Code.ERROR,
          "Spark Connect session is not initialized. Check connection settings.");
    }

    // Get noteId from context for notebook-level synchronization
    String noteId = context.getNoteId();
    if (StringUtils.isBlank(noteId)) {
      return new InterpreterResult(Code.ERROR,
          "Note ID is missing from interpreter context.");
    }

    // Get or create lock for this notebook to ensure sequential execution
    // This ensures one query at a time per notebook, even with concurrentSQL enabled
    ReentrantLock notebookLock = NotebookLockManager.getNotebookLock(noteId);

    // Acquire lock to ensure only one query executes at a time for this notebook
    notebookLock.lock();
    try {
      List<String> sqls = sqlSplitter.splitSql(st);
      int maxResult = Integer.parseInt(context.getLocalProperties().getOrDefault("limit",
          String.valueOf(sparkConnectInterpreter.getMaxResult())));

      boolean useStreaming = Boolean.parseBoolean(
          getProperty("zeppelin.spark.connect.streamResults", "false"));

      String curSql = null;
      try {
        for (String sql : sqls) {
          curSql = sql;
          if (StringUtils.isBlank(sql)) {
            continue;
          }
          Dataset<Row> df = sparkSession.sql(sql);
          if (useStreaming) {
            SparkConnectUtils.streamDataFrame(df, maxResult, context.out);
          } else {
            String result = SparkConnectUtils.showDataFrame(df, maxResult);
            context.out.write(result);
          }
        }
        context.out.flush();
      } catch (Exception e) {
        try {
          LOGGER.error("Error executing SQL: {}", curSql, e);
          context.out.write("\nError in SQL: " + curSql + "\n");
          if (Boolean.parseBoolean(getProperty("zeppelin.spark.sql.stacktrace", "true"))) {
            if (e.getCause() != null) {
              context.out.write(ExceptionUtils.getStackTrace(e.getCause()));
            } else {
              context.out.write(ExceptionUtils.getStackTrace(e));
            }
          } else {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            context.out.write(msg
                + "\nSet zeppelin.spark.sql.stacktrace = true to see full stacktrace");
          }
          context.out.flush();
        } catch (IOException ex) {
          LOGGER.error("Failed to write error output", ex);
        }
        return new InterpreterResult(Code.ERROR);
      }

      return new InterpreterResult(Code.SUCCESS);
    } finally {
      notebookLock.unlock();
    }
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    SparkSession sparkSession = sparkConnectInterpreter.getSparkSession();
    if (sparkSession != null) {
      try {
        sparkSession.interruptAll();
      } catch (Exception e) {
        LOGGER.warn("Error interrupting Spark Connect session", e);
      }
    }
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) throws InterpreterException {
    return new ArrayList<>();
  }

  @Override
  public Scheduler getScheduler() {
    if (concurrentSQL()) {
      int maxConcurrency = Integer.parseInt(
          getProperty("zeppelin.spark.concurrentSQL.max", "10"));
      return SchedulerFactory.singleton().createOrGetParallelScheduler(
          SparkConnectSqlInterpreter.class.getName() + this.hashCode(), maxConcurrency);
    } else {
      try {
        return getInterpreterInTheSameSessionByClassName(
            SparkConnectInterpreter.class, false).getScheduler();
      } catch (InterpreterException e) {
        throw new RuntimeException("Failed to get scheduler", e);
      }
    }
  }

  private boolean concurrentSQL() {
    return Boolean.parseBoolean(getProperty("zeppelin.spark.concurrentSQL"));
  }
}
