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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Arrays;
import java.util.Map;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.InterpreterUtils;
import org.apache.zeppelin.interpreter.ResultMessages;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;


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
    this.sparkInterpreter = getInterpreterInTheSameSessionByClassName(LivySparkInterpreter.class);
    // As we don't know whether livyserver use spark2 or spark1, so we will detect SparkSession
    // to judge whether it is using spark2.
    try {
      InterpreterContext context = InterpreterContext.builder()
          .setInterpreterOut(new InterpreterOutput())
          .build();
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

  @Override
  public InterpreterResult interpret(String line, InterpreterContext context) {
    try {
      if (StringUtils.isEmpty(line)) {
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, "");
      }

      // use triple quote so that we don't need to do string escape.
      String sqlQuery = null;
      if (isSpark2) {
        if (tableWithUTFCharacter()) {
          sqlQuery = "val df = spark.sql(\"\"\"" + line + "\"\"\")\n"
              + "for ( col <- df.columns ) {\n"
              + "    print(col+\"\\t\")\n"
              + "}\n"
              + "println\n"
              + "df.toJSON.take(" + maxResult + ").foreach(println)";
        } else {
          sqlQuery = "spark.sql(\"\"\"" + line + "\"\"\").show(" + maxResult + ", " +
              truncate + ")";
        }
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
            List<String> rows;
            if (tableWithUTFCharacter()) {
              rows = parseSQLJsonOutput(message.getData());
            } else {
              rows = parseSQLOutput(message.getData());
            }
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

  protected List<String> parseSQLJsonOutput(String output) {
    List<String> rows = new ArrayList<>();

    String[] rowsOutput = output.split("(?<!\\\\)\\n");
    String[] header = rowsOutput[1].split("\t");
    List<String> cells = new ArrayList<>(Arrays.asList(header));
    rows.add(StringUtils.join(cells, "\t"));

    for (int i = 2; i < rowsOutput.length; i++) {
      Map<String, String> retMap = new Gson().fromJson(
          rowsOutput[i], new TypeToken<HashMap<String, String>>() {
          }.getType()
      );
      cells = new ArrayList<>();
      for (String s : header) {
        cells.add(retMap.getOrDefault(s, "null")
            .replace("\n", "\\n")
            .replace("\t", "\\t"));
      }
      rows.add(StringUtils.join(cells, "\t"));
    }
    return rows;
  }

  protected List<String> parseSQLOutput(String str) {
    // the regex is referred to org.apache.spark.util.Utils#fullWidthRegex
    // for spark every chinese character has two placeholder(one placeholder is one char)
    // for zeppelin it has only one placeholder.
    // insert a special character (/u0001) which never use after every chinese character
    String fullWidthRegex = "([" +
            "\u1100-\u115F" +
            "\u2E80-\uA4CF" +
            "\uAC00-\uD7A3" +
            "\uF900-\uFAFF" +
            "\uFE10-\uFE19" +
            "\uFE30-\uFE6F" +
            "\uFF00-\uFF60" +
            "\uFFE0-\uFFE6" +
            "])";
    String output = str.replaceAll(fullWidthRegex, "$1\u0001");
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
          // replace /u0001 with empty string just because we insert it before
          // escapeJavaStyleString is referred to
          // org.apache.commons.lang3.StringEscapeUtils.escapeEcmaScript
          // but make a little change that avoid chinese character is escaped
          cells.add(escapeJavaStyleString(line.substring(pair.start, pair.end)
                          .replaceAll("\u0001", "")).trim());
        }
        rows.add(StringUtils.join(cells, "\t"));
      }
      // Determine position of next line skipping newline
      lineStart += firstLine.length() + 1;
      lineEnd = lineStart + firstLine.length();
    }
    return rows;
  }

  private static String escapeJavaStyleString(String str) {
    if (str == null) {
      return null;
    }
    try {
      StringWriter writer = new StringWriter(str.length() * 2);
      escapeJavaStyleString(writer, str);
      return writer.toString();
    } catch (IOException ioe) {
      // this should never ever happen while writing to a StringWriter
      throw new RuntimeException(ioe);
    }
  }

  private static void escapeJavaStyleString(Writer out, String str) throws IOException {
    if (out == null) {
      throw new IllegalArgumentException("The Writer must not be null");
    }
    if (str == null) {
      return;
    }
    int sz;
    sz = str.length();
    for (int i = 0; i < sz; i++) {
      char ch = str.charAt(i);

      // handle unicode
      if (ch > 0xfff) {
        out.write(ch);
      } else if (ch > 0xff) {
        out.write("\\u0" + hex(ch));
      } else if (ch > 0x7f) {
        out.write("\\u00" + hex(ch));
      } else if (ch < 32) {
        switch (ch) {
          case '\b' :
            out.write('\\');
            out.write('b');
            break;
          case '\n' :
            out.write('\\');
            out.write('n');
            break;
          case '\t' :
            out.write('\\');
            out.write('t');
            break;
          case '\f' :
            out.write('\\');
            out.write('f');
            break;
          case '\r' :
            out.write('\\');
            out.write('r');
            break;
          default :
            if (ch > 0xf) {
              out.write("\\u00" + hex(ch));
            } else {
              out.write("\\u000" + hex(ch));
            }
            break;
        }
      } else {
        switch (ch) {
          case '\'' :
            out.write('\\');
            break;
          case '"' :
            out.write('\\');
            out.write('"');
            break;
          case '\\' :
            out.write('\\');
            out.write('\\');
            break;
          case '/' :
            out.write('\\');
            break;
          default :
            out.write(ch);
            break;
        }
      }
    }
  }

  private static String hex(char ch) {
    return Integer.toHexString(ch).toUpperCase(Locale.ENGLISH);
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

  public boolean tableWithUTFCharacter() {
    return Boolean.parseBoolean(getProperty("zeppelin.livy.tableWithUTFCharacter"));
  }

  @Override
  public Scheduler getScheduler() {
    if (concurrentSQL()) {
      int maxConcurrency = 10;
      return SchedulerFactory.singleton().createOrGetParallelScheduler(
          LivySparkInterpreter.class.getName() + this.hashCode(), maxConcurrency);
    } else {
      if (sparkInterpreter != null) {
        return sparkInterpreter.getScheduler();
      } else {
        return super.getScheduler();
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
