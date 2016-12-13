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
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Livy SparkSQL Interpreter for Zeppelin.
 */
public class LivySparkSQLInterpreter extends BaseLivyInterprereter {

  private LivySparkInterpreter sparkInterpreter;

  private boolean sqlContextCreated = false;

  public LivySparkSQLInterpreter(Properties property) {
    super(property);
  }

  @Override
  public String getSessionKind() {
    return "spark";
  }

  @Override
  public void open() {
    super.open();
    this.sparkInterpreter =
        (LivySparkInterpreter) getInterpreterInTheSameSessionByClassName(
            LivySparkInterpreter.class.getName());
  }

  @Override
  public InterpreterResult interpret(String line, InterpreterContext context) {
    try {
      if (StringUtils.isEmpty(line)) {
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, "");
      }

      // create sqlContext implicitly if it is not available, as in livy 0.2 sqlContext
      // is not available.
      synchronized (this) {
        if (!sqlContextCreated) {
          InterpreterResult result = sparkInterpreter.interpret("sqlContext", context);
          if (result.code() == InterpreterResult.Code.ERROR) {
            result = sparkInterpreter.interpret(
                "val sqlContext = new org.apache.spark.sql.SQLContext(sc)\n"
                    + "import sqlContext.implicits._", context);
            if (result.code() == InterpreterResult.Code.ERROR) {
              return new InterpreterResult(InterpreterResult.Code.ERROR,
                  "Fail to create sqlContext," + result.message());
            }
          }
          sqlContextCreated = true;
        }
      }

      // delegate the work to LivySparkInterpreter in the same session.
      // TODO(zjffdu), we may create multiple session for the same user here. This can be fixed
      // after we move session creation to open()
      InterpreterResult res = sparkInterpreter.interpret("sqlContext.sql(\"" +
          line.replaceAll("\"", "\\\\\"")
              .replaceAll("\\n", " ")
          + "\").show(" +
          property.get("zeppelin.livy.spark.sql.maxResult") + ")", context);

      if (res.code() == InterpreterResult.Code.SUCCESS) {
        StringBuilder resMsg = new StringBuilder();
        resMsg.append("%table ");
        String[] rows = new String(context.out.toByteArray()).split("\n");
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
