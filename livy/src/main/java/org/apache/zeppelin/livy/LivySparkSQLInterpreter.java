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

package org.apache.zeppelin.livy;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


/**
 * Livy SparkSQL Interpreter for Zeppelin.
 */
public class LivySparkSQLInterpreter extends BaseLivyInterpreter {

  public static final String ZEPPELIN_LIVY_SPARK_SQL_FIELD_TRUNCATE =
      "zeppelin.livy.spark.sql.field.truncate";

  public static final String ZEPPELIN_LIVY_SPARK_SQL_MAX_RESULT =
      "zeppelin.livy.spark.sql.maxResult";

  private LivySparkInterpreter sparkInterpreter;

  private boolean isSpark2 = false;
  private int maxResult = 1000;
  private boolean truncate = true;

  public LivySparkSQLInterpreter(Properties property) {
    super(property);
    this.maxResult = Integer.parseInt(property.getProperty(ZEPPELIN_LIVY_SPARK_SQL_MAX_RESULT));
    if (property.getProperty(ZEPPELIN_LIVY_SPARK_SQL_FIELD_TRUNCATE) != null) {
      this.truncate =
          Boolean.parseBoolean(property.getProperty(ZEPPELIN_LIVY_SPARK_SQL_FIELD_TRUNCATE));
    }
  }

  @Override
  public String getSessionKind() {
    return "spark";
  }

  @Override
  public void open() throws InterpreterException {
    this.sparkInterpreter = getSparkInterpreter();
    // As we don't know whether livyserver use spark2 or spark1, so we will detect SparkSession
    // to judge whether it is using spark2.
    try {
      InterpreterResult result = sparkInterpreter.interpret("spark", null, false, false);
      if (result.code() == InterpreterResult.Code.SUCCESS &&
          result.message().get(0).getData().contains("org.apache.spark.sql.SparkSession")) {
        LOGGER.info("SparkSession is detected so we are using spark 2.x for session {}",
            sparkInterpreter.getSessionInfo().id);
        isSpark2 = true;
      } else {
        // spark 1.x
        result = sparkInterpreter.interpret("sqlContext", null, false, false);
        if (result.code() == InterpreterResult.Code.SUCCESS) {
          LOGGER.info("sqlContext is detected.");
        } else if (result.code() == InterpreterResult.Code.ERROR) {
          // create SqlContext if it is not available, as in livy 0.2 sqlContext
          // is not available.
          LOGGER.info("sqlContext is not detected, try to create SQLContext by ourselves");
          result = sparkInterpreter.interpret(
              "val sqlContext = new org.apache.spark.sql.SQLContext(sc)\n"
                  + "import sqlContext.implicits._", null, false, false);
          if (result.code() == InterpreterResult.Code.ERROR) {
            throw new LivyException("Fail to create SQLContext," +
                result.message().get(0).getData());
          }
        }
      }
    } catch (LivyException e) {
      throw new RuntimeException("Fail to Detect SparkVersion", e);
    }
  }

  private LivySparkInterpreter getSparkInterpreter() throws InterpreterException {
    LazyOpenInterpreter lazy = null;
    LivySparkInterpreter spark = null;
    Interpreter p = getInterpreterInTheSameSessionByClassName(LivySparkInterpreter.class.getName());

    while (p instanceof WrappedInterpreter) {
      if (p instanceof LazyOpenInterpreter) {
        lazy = (LazyOpenInterpreter) p;
      }
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }
    spark = (LivySparkInterpreter) p;

    if (lazy != null) {
      lazy.open();
    }
    return spark;
  }

  @Override
  public InterpreterResult interpret(String line, InterpreterContext context) {
    try {
      if (StringUtils.isEmpty(line)) {
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, "");
      }

      // use triple quote so that we don't need to do string escape.
      String sqlQuery = null;
      if (isSpark2) {
        sqlQuery = "spark.sql(\"\"\"" + line + "\"\"\").show(" + maxResult + ", " +
            truncate + ")";
      } else {
        sqlQuery = "sqlContext.sql(\"\"\"" + line + "\"\"\").show(" + maxResult + ", " +
            truncate + ")";
      }
      InterpreterResult result = sparkInterpreter.interpret(sqlQuery, context.getParagraphId(),
          this.displayAppInfo, true);

      if (result.code() == InterpreterResult.Code.SUCCESS) {
        InterpreterResult result2 = new InterpreterResult(InterpreterResult.Code.SUCCESS);
        for (InterpreterResultMessage message : result.message()) {
          // convert Text type to Table type. We assume the text type must be the sql output. This
          // assumption is correct for now. Ideally livy should return table type. We may do it in
          // the future release of livy.
          if (message.getType() == InterpreterResult.Type.TEXT) {
            List<String> rows = parseSQLOutput(message.getData());
            result2.add(InterpreterResult.Type.TABLE, StringUtils.join(rows, "\n"));
            if (rows.size() >= (maxResult + 1)) {
              result2.add(ResultMessages.getExceedsLimitRowsMessage(maxResult,
                  ZEPPELIN_LIVY_SPARK_SQL_MAX_RESULT));
            }
          } else {
            result2.add(message.getType(), message.getData());
          }
        }
        return result2;
      } else {
        return result;
      }
    } catch (Exception e) {
      LOGGER.error("Exception in LivySparkSQLInterpreter while interpret ", e);
      return new InterpreterResult(InterpreterResult.Code.ERROR,
          InterpreterUtils.getMostRelevantMessage(e));
    }
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  protected List<String> parseSQLOutput(String output) {
    List<String> rows = new ArrayList<>();
    String[] lines = output.split("\n");
    // at least 4 lines, even for empty sql output
    //    +---+---+
    //    |  a|  b|
    //    +---+---+
    //    +---+---+

    // use the first line to determinte the position of feach cell
    String[] tokens = StringUtils.split(lines[0], "\\+");
    // pairs keeps the start/end position of each cell. We parse it from the first row
    // which use '+' as separator
    List<Pair> pairs = new ArrayList<>();
    int start = 0;
    int end = 0;
    for (String token : tokens) {
      start = end + 1;
      end = start + token.length();
      pairs.add(new Pair(start, end));
    }

    for (String line : lines) {
      // Only match format "|....|"
      // skip line like "+---+---+" and "only showing top 1 row"
      if (line.matches("^\\|.*\\|$")) {
        List<String> cells = new ArrayList<>();
        for (Pair pair : pairs) {
          // strip the blank space around the cell
          cells.add(line.substring(pair.start, pair.end).trim());
        }
        rows.add(StringUtils.join(cells, "\t"));
      }
    }
    return rows;
  }

  /**
   * Represent the start and end index of each cell
   */
  private static class Pair {
    private int start;
    private int end;
    public Pair(int start, int end) {
      this.start = start;
      this.end = end;
    }
  }

  public boolean concurrentSQL() {
    return Boolean.parseBoolean(getProperty("zeppelin.livy.concurrentSQL"));
  }

  @Override
  public Scheduler getScheduler() {
    if (concurrentSQL()) {
      int maxConcurrency = 10;
      return SchedulerFactory.singleton().createOrGetParallelScheduler(
          LivySparkInterpreter.class.getName() + this.hashCode(), maxConcurrency);
    } else {
      Interpreter intp =
          getInterpreterInTheSameSessionByClassName(LivySparkInterpreter.class.getName());
      if (intp != null) {
        return intp.getScheduler();
      } else {
        return null;
      }
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
    sparkInterpreter.cancel(context);
  }

  @Override
  public void close() {
    this.sparkInterpreter.close();
  }

  @Override
  public int getProgress(InterpreterContext context) {
    if (this.sparkInterpreter != null) {
      return this.sparkInterpreter.getProgress(context);
    } else {
      return 0;
    }
  }

  @Override
  protected String extractAppId() throws LivyException {
    // it wont' be called because it would delegate to LivySparkInterpreter
    throw new UnsupportedOperationException();
  }

  @Override
  protected String extractWebUIAddress() throws LivyException {
    // it wont' be called because it would delegate to LivySparkInterpreter
    throw new UnsupportedOperationException();
  }
}
