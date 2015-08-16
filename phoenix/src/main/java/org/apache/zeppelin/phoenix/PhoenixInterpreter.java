/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.phoenix;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phoenix interpreter for Zeppelin.
 */
public class PhoenixInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(PhoenixInterpreter.class);

  private static final String EXPLAIN_PREDICATE = "EXPLAIN ";
  private static final String UPDATE_HEADER = "UPDATES ";

  private static final String WS = " ";
  private static final String NEWLINE = "\n";
  private static final String TAB = "\t";
  private static final String TABLE_MAGIC_TAG = "%table ";

  static final String PHOENIX_JDBC_URL = "phoenix.jdbc.url";
  static final String PHOENIX_JDBC_USER = "phoenix.user";
  static final String PHOENIX_JDBC_PASSWORD = "phoenix.password";
  static final String PHOENIX_MAX_RESULT = "phoenix.max.result";
  static final String PHOENIX_JDBC_DRIVER_NAME = "phoenix.driver.name";

  static final String DEFAULT_JDBC_URL = "jdbc:phoenix:localhost:2181:/hbase-unsecure";
  static final String DEFAULT_JDBC_USER = "";
  static final String DEFAULT_JDBC_PASSWORD = "";
  static final String DEFAULT_MAX_RESULT = "1000";
  static final String DEFAULT_JDBC_DRIVER_NAME = "org.apache.phoenix.jdbc.PhoenixDriver";

  private Connection jdbcConnection;
  private Statement currentStatement;
  private Exception exceptionOnConnect;
  private int maxResult;

  static {
    Interpreter.register(
      "sql",
      "phoenix",
      PhoenixInterpreter.class.getName(),
      new InterpreterPropertyBuilder()
        .add(PHOENIX_JDBC_URL, DEFAULT_JDBC_URL, "Phoenix JDBC connection string")
        .add(PHOENIX_JDBC_USER, DEFAULT_JDBC_USER, "The Phoenix user")
        .add(PHOENIX_JDBC_PASSWORD, DEFAULT_JDBC_PASSWORD, "The password for the Phoenix user")
        .add(PHOENIX_MAX_RESULT, DEFAULT_MAX_RESULT, "Max number of SQL results to display.")
        .add(PHOENIX_JDBC_DRIVER_NAME, DEFAULT_JDBC_DRIVER_NAME, "Phoenix Driver classname.")
        .build()
    );
  }

  public PhoenixInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    logger.info("Jdbc open connection called!");
    close();

    try {
      Class.forName(getProperty(PHOENIX_JDBC_DRIVER_NAME));

      maxResult = Integer.valueOf(getProperty(PHOENIX_MAX_RESULT));
      jdbcConnection = DriverManager.getConnection(
        getProperty(PHOENIX_JDBC_URL),
        getProperty(PHOENIX_JDBC_USER),
        getProperty(PHOENIX_JDBC_PASSWORD)
      );
      exceptionOnConnect = null;
      logger.info("Successfully created Jdbc connection");
    }
    catch (ClassNotFoundException | SQLException e) {
      logger.error("Cannot open connection", e);
      exceptionOnConnect = e;
    }
  }

  @Override
  public void close() {
    logger.info("Jdbc close connection called!");

    try {
      if (getJdbcConnection() != null) {
        getJdbcConnection().close();
      }
    } catch (SQLException e) {
      logger.error("Cannot close connection", e);
    }
    finally {
      exceptionOnConnect = null;
    }
  }

  private String clean(boolean isExplain, String str){
    return (isExplain || str == null) ? str : str.replace(TAB, WS).replace(NEWLINE, WS);  
  }

  private InterpreterResult executeSql(String sql) {
    try {
      if (exceptionOnConnect != null) {
        return new InterpreterResult(Code.ERROR, exceptionOnConnect.getMessage());
      }

      currentStatement = getJdbcConnection().createStatement();

      boolean isExplain = StringUtils.containsIgnoreCase(sql, EXPLAIN_PREDICATE);
      StringBuilder msg = (isExplain) ? new StringBuilder() : new StringBuilder(TABLE_MAGIC_TAG);

      ResultSet res = null;
      try {
        boolean hasResult = currentStatement.execute(sql);
        if (hasResult){ //If query had results
          res = currentStatement.getResultSet();
          //Append column names
          ResultSetMetaData md = res.getMetaData();
          String row = clean(isExplain, md.getColumnName(1));
          for (int i = 2; i < md.getColumnCount() + 1; i++)
            row += TAB + clean(isExplain, md.getColumnName(i));
          msg.append(row + NEWLINE);

          //Append rows
          int rowCount = 0;
          while (res.next() && rowCount < getMaxResult()) {
            row = clean(isExplain, res.getString(1));
            for (int i = 2; i < md.getColumnCount() + 1; i++)
              row += TAB + clean(isExplain, res.getString(i));
            msg.append(row + NEWLINE);
            rowCount++;
          }
        }
        else { // May have been upsert or DDL
          msg.append(UPDATE_HEADER + NEWLINE +
            "Rows affected: " + currentStatement.getUpdateCount()
            + NEWLINE);
        }

      } finally {
        try {
          if (res != null) res.close();
          getJdbcConnection().commit();
          currentStatement.close();
        } finally {
          currentStatement = null;
        }
      }

      return new InterpreterResult(Code.SUCCESS, msg.toString());
    }
    catch (SQLException ex) {
      logger.error("Can not run " + sql, ex);
      return new InterpreterResult(Code.ERROR, ex.getMessage());
    }
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    logger.info("Run SQL command '" + cmd + "'");
    return executeSql(cmd);
  }

  @Override
  public void cancel(InterpreterContext context) {
    if (currentStatement != null) {
      try {
        currentStatement.cancel();
      }
      catch (SQLException ex) {
      }
      finally {
        currentStatement = null;
      }
    }
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
        PhoenixInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }

  public Connection getJdbcConnection() {
    return jdbcConnection;
  }

  public int getMaxResult() {
    return maxResult;
  }

}
