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

import static org.apache.commons.lang.StringEscapeUtils.escapeJavaScript;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.InterpreterUtils;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.ResultMessages;
import org.apache.zeppelin.interpreter.WrappedInterpreter;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 * Livy SparkSQL Interpreter for Zeppelin.
 */
public class LivySparkSQLInterpreter extends BaseLivyInterpreter {
  public static final String ZEPPELIN_LIVY_SPARK_SQL_FIELD_TRUNCATE =
      "zeppelin.livy.spark.sql.field.truncate";

  public static final String ZEPPELIN_LIVY_SPARK_SQL_MAX_RESULT =
      "zeppelin.livy.spark.sql.maxResult";

  private LivySparkInterpreter sparkInterpreter;
  private String codeType = null;

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
      InterpreterContext context = new InterpreterContext(
          "noteId",
          "paragraphId",
          "replName",
          "paragraphTitle",
          "paragraphText",
          new AuthenticationInfo(),
          new HashMap<String, Object>(),
          new GUI(),
          new GUI(),
          null,
          null,
          null,
          new InterpreterOutput(null));
      InterpreterResult result = sparkInterpreter.interpret("spark", context);
      if (result.code() == InterpreterResult.Code.SUCCESS &&
          result.message().get(0).getData().contains("org.apache.spark.sql.SparkSession")) {
        LOGGER.info("SparkSession is detected so we are using spark 2.x for session {}",
            sparkInterpreter.getSessionInfo().id);
        isSpark2 = true;
      } else {
        // spark 1.x
        result = sparkInterpreter.interpret("sqlContext", context);
        if (result.code() == InterpreterResult.Code.SUCCESS) {
          LOGGER.info("sqlContext is detected.");
        } else if (result.code() == InterpreterResult.Code.ERROR) {
          // create SqlContext if it is not available, as in livy 0.2 sqlContext
          // is not available.
          LOGGER.info("sqlContext is not detected, try to create SQLContext by ourselves");
          result = sparkInterpreter.interpret(
              "val sqlContext = new org.apache.spark.sql.SQLContext(sc)\n"
                  + "import sqlContext.implicits._", context);
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
      InterpreterResult result = sparkInterpreter.interpret(sqlQuery, context);
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
    // Get first line by breaking on \n. We can guarantee
    // that \n marks the end of the first line, but not for
    // subsequent lines (as it could be in the cells)
    String firstLine = output.split("\n", 2)[0];
    // at least 4 lines, even for empty sql output
    //    +---+---+
    //    |  a|  b|
    //    +---+---+
    //    +---+---+

    // use the first line to determine the position of each cell
    String[] tokens = StringUtils.split(firstLine, "\\+");
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

    // Use the header line to determine the position
    // of subsequent lines
    int lineStart = 0;
    int lineEnd = firstLine.length();
    while (lineEnd < output.length()) {
      // Only match format "|....|"
      // skip line like "+---+---+" and "only showing top 1 row"
      String line = output.substring(lineStart, lineEnd);
      // Use the DOTALL regex mode to match newlines
      if (line.matches("(?s)^\\|.*\\|$")) {
        List<String> cells = new ArrayList<>();
        for (Pair pair : pairs) {
          // strip the blank space around the cell and escape the string
          cells.add(escapeJavaScript(line.substring(pair.start, pair.end)).trim());
        }
        rows.add(StringUtils.join(cells, "\t"));
      }
      // Determine position of next line skipping newline
      lineStart += firstLine.length() + 1;
      lineEnd = lineStart + firstLine.length();
    }
    return rows;
  }

  /**
   * Represent the start and end index of each cell.
   */
  private static class Pair {
    private int start;
    private int end;

    Pair(int start, int end) {
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
    if (this.sparkInterpreter != null) {
      sparkInterpreter.cancel(context);
    }
  }

  @Override
  public void close() {
    if (this.sparkInterpreter != null) {
      this.sparkInterpreter.close();
    }
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
