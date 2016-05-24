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

import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Properties;


/**
 * Livy PySpark interpreter for Zeppelin.
 */
public class LivySparkSQLInterpreter extends Interpreter {

  Logger LOGGER = LoggerFactory.getLogger(LivySparkSQLInterpreter.class);
  static String DEFAULT_MAX_RESULT = "1000";

  static {
    Interpreter.register(
        "sql",
        "livy",
        LivySparkSQLInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add("zeppelin.livy.spark.maxResult",
                DEFAULT_MAX_RESULT,
                "Max number of SparkSQL result to display.")
            .build()
    );
  }

  protected Map<String, Integer> userSessionMap;
  private LivyHelper livyHelper;

  public LivySparkSQLInterpreter(Properties property) {
    super(property);
    livyHelper = new LivyHelper(property);
    userSessionMap = LivySparkInterpreter.getUserSessionMap();
  }

  @Override
  public void open() {
  }

  @Override
  public void close() {
    livyHelper.closeSession(userSessionMap);
  }

  @Override
  public InterpreterResult interpret(String line, InterpreterContext interpreterContext) {
    try {
      if (userSessionMap.get(interpreterContext.getAuthenticationInfo().getUser()) == null) {
        try {
          userSessionMap.put(
              interpreterContext.getAuthenticationInfo().getUser(),
              livyHelper.createSession(
                  interpreterContext,
                  "spark")
          );
          livyHelper.initializeSpark(interpreterContext, userSessionMap);
        } catch (Exception e) {
          LOGGER.error("Exception in LivySparkSQLInterpreter while interpret ", e);
          return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage());
        }
      }

      if (line == null || line.trim().length() == 0) {
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, "");
      }

      InterpreterResult res = livyHelper.interpret("sqlContext.sql(\"" +
              line.replaceAll("\"", "\\\\\"")
                  .replaceAll("\\n", " ")
              + "\").show(" +
              property.get("zeppelin.livy.spark.maxResult") + ")",
          interpreterContext, userSessionMap);

      if (res.code() == InterpreterResult.Code.SUCCESS) {
        StringBuilder resMsg = new StringBuilder();
        resMsg.append("%table ");
        String[] rows = res.message().split("\n");

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

  @Override
  public void cancel(InterpreterContext context) {
    livyHelper.cancelHTTP(context.getParagraphId());
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
        LivySparkInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }

}
