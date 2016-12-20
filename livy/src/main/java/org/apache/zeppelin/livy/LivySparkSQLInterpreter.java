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

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

import java.util.Properties;


/**
 * Livy SparkSQL Interpreter for Zeppelin.
 */
public class LivySparkSQLInterpreter extends BaseLivyInterprereter {

  private LivySparkInterpreter sparkInterpreter;

  private boolean isSpark2 = false;
  private int maxResult = 1000;

  public LivySparkSQLInterpreter(Properties property) {
    super(property);
    this.maxResult = Integer.parseInt(property.getProperty("zeppelin.livy.spark.sql.maxResult"));
  }

  @Override
  public String getSessionKind() {
    return "spark";
  }

  @Override
  public void open() {
    this.sparkInterpreter = getSparkInterpreter();
    // As we don't know whether livyserver use spark2 or spark1, so we will detect SparkSession
    // to judge whether it is using spark2.
    try {
      InterpreterResult result = sparkInterpreter.interpret("spark", false);
      if (result.code() == InterpreterResult.Code.SUCCESS &&
          result.message().get(0).getData().contains("org.apache.spark.sql.SparkSession")) {
        LOGGER.info("SparkSession is detected so we are using spark 2.x for session {}",
            sparkInterpreter.getSessionInfo().id);
        isSpark2 = true;
      } else {
        // spark 1.x
        result = sparkInterpreter.interpret("sqlContext", false);
        if (result.code() == InterpreterResult.Code.SUCCESS) {
          LOGGER.info("sqlContext is detected.");
        } else if (result.code() == InterpreterResult.Code.ERROR) {
          // create SqlContext if it is not available, as in livy 0.2 sqlContext
          // is not available.
          LOGGER.info("sqlContext is not detected, try to create SQLContext by ourselves");
          result = sparkInterpreter.interpret(
              "val sqlContext = new org.apache.spark.sql.SQLContext(sc)\n"
                  + "import sqlContext.implicits._", false);
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

  private LivySparkInterpreter getSparkInterpreter() {
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
        sqlQuery = "spark.sql(\"\"\"" + line + "\"\"\").show(" + maxResult + ")";
      } else {
        sqlQuery = "sqlContext.sql(\"\"\"" + line + "\"\"\").show(" + maxResult + ")";
      }
      InterpreterResult res = sparkInterpreter.interpret(sqlQuery, this.displayAppInfo);

      if (res.code() == InterpreterResult.Code.SUCCESS) {
        StringBuilder resMsg = new StringBuilder();
        resMsg.append("%table ");
        String[] rows = res.message().get(0).getData().split("\n");
        String[] headers = rows[1].split("\\|");
        for (int head = 1; head < headers.length; head++) {
          resMsg.append(headers[head].trim()).append("\t");
        }
        resMsg.append("\n");
        if (rows[3].indexOf("+") == 0) {

        } else {
          for (int cols = 3; cols < rows.length - 1; cols++) {
            String[] col = rows[cols].split("\\|");
            for (int data = 1; data < col.length; data++) {
              resMsg.append(col[data].trim()).append("\t");
            }
            resMsg.append("\n");
          }
        }
        if (rows[rows.length - 1].indexOf("only") == 0) {
          resMsg.append("<font color=red>" + rows[rows.length - 1] + ".</font>");
        }

        return new InterpreterResult(InterpreterResult.Code.SUCCESS,
            resMsg.toString()
        );
      } else {
        return res;
      }
    } catch (Exception e) {
      LOGGER.error("Exception in LivySparkSQLInterpreter while interpret ", e);
      return new InterpreterResult(InterpreterResult.Code.ERROR,
          InterpreterUtils.getMostRelevantMessage(e));
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
  public void close() {
    this.sparkInterpreter.close();
  }
}
