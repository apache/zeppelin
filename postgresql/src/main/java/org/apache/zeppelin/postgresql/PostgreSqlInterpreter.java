/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.postgresql;

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
 * PostgreSQL interpreter for Zeppelin. This interpreter can also be used for accessing HAWQ and
 * GreenplumDB
 */
public class PostgreSqlInterpreter extends Interpreter {

  private static final String UPDATE_COUNT_HEADER = "Update Count";

  private Logger logger = LoggerFactory.getLogger(PostgreSqlInterpreter.class);

  private static final char WITESPACE = ' ';
  private static final char NEWLINE = '\n';
  private static final char TAB = '\t';
  private static final String TABLE_MAGIC_TAG = "%table ";
  private static final String EXPLAIN_PREDICATE = "EXPLAIN ";

  private static final String POSTGRESQL_DRIVER_NAME = "org.postgresql.Driver";

  static final String POSTGRESQL_SERVER_URL = "postgresql.url";
  static final String POSTGRESQL_SERVER_USER = "postgresql.user";
  static final String POSTGRESQL_SERVER_PASSWORD = "postgresql.password";

  static {
    Interpreter.register(
        "sql",
        "psql",
        PostgreSqlInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add(POSTGRESQL_SERVER_URL, "jdbc:postgresql://localhost:5432/",
                "The URL for PostgreSQL.")
            .add(POSTGRESQL_SERVER_USER, "ambari", "The PostgreSQL user")
            .add(POSTGRESQL_SERVER_PASSWORD, "", "The password for the PostgreSQL user").build());
  }

  private Connection jdbcConnection;
  private Exception exceptionOnConnect;
  private Statement currentStatement;

  public PostgreSqlInterpreter(Properties property) {
    super(property);
  }

  // Test only method
  protected Connection getJdbcConnection() {
    return jdbcConnection;
  }

  @Override
  public void open() {
    logger.info("Jdbc open connection called!");

    try {
      Class.forName(POSTGRESQL_DRIVER_NAME);
    } catch (ClassNotFoundException e) {
      logger.error("Cannot open connection", e);
      exceptionOnConnect = e;
      return;
    }

    try {
      String url = getProperty(POSTGRESQL_SERVER_URL);
      String user = getProperty(POSTGRESQL_SERVER_USER);
      String password = getProperty(POSTGRESQL_SERVER_PASSWORD);

      jdbcConnection = DriverManager.getConnection(url, user, password);
      exceptionOnConnect = null;
      logger.info("Successfully created Jdbc connection");
    } catch (SQLException e) {
      logger.error("Cannot open connection", e);
      exceptionOnConnect = e;
    }
  }

  @Override
  public void close() {
    try {
      if (getJdbcConnection() != null) {
        getJdbcConnection().close();
      }
    } catch (SQLException e) {
      logger.error("Cannot close connection", e);
    } finally {
      exceptionOnConnect = null;
    }
  }

  private InterpreterResult executeSql(String sql) {
    try {
      
      if (exceptionOnConnect != null) {
        return new InterpreterResult(Code.ERROR, exceptionOnConnect.getMessage());
      }
      
      currentStatement = getJdbcConnection().createStatement();
      
      StringBuilder msg = null;
      boolean isTableType = false;
      
      if (StringUtils.containsIgnoreCase(sql, EXPLAIN_PREDICATE)) {
        msg = new StringBuilder();
      } else {
        msg = new StringBuilder(TABLE_MAGIC_TAG);
        isTableType = true;
      }

      ResultSet resultSet = null;
      try {

        boolean isResultSetAvailable = currentStatement.execute(sql);

        if (isResultSetAvailable) {
          resultSet = currentStatement.getResultSet();

          ResultSetMetaData md = resultSet.getMetaData();

          for (int i = 1; i < md.getColumnCount() + 1; i++) {
            if (i > 1) {
              msg.append(TAB);
            }
            msg.append(clean(isTableType, md.getColumnName(i)));
          }
          msg.append(NEWLINE);

          while (resultSet.next()) {
            for (int i = 1; i < md.getColumnCount() + 1; i++) {
              msg.append(clean(isTableType, resultSet.getString(i)));
              if (i != md.getColumnCount()) {
                msg.append(TAB);
              }
            }
            msg.append(NEWLINE);
          }
        } else { // it is an update count or there are no results
          int updateCount = currentStatement.getUpdateCount();
          msg.append(UPDATE_COUNT_HEADER).append(NEWLINE);
          msg.append(updateCount).append(NEWLINE);
        }
      } finally {
        try {
          if (resultSet != null) {
            resultSet.close();
          }
          currentStatement.close();
        } finally {
          currentStatement = null;
        }
      }

      InterpreterResult rett = new InterpreterResult(Code.SUCCESS, msg.toString());
      return rett;
    } catch (SQLException ex) {
      logger.error("Cannot run " + sql, ex);
      return new InterpreterResult(Code.ERROR, ex.getMessage());
    }
  }

  /**
   * For %table response replace Tab and Newline characters from the content.
   */
  private String clean(boolean isTableResponseType, String str) {
    return (!isTableResponseType) ? str : str.replace(TAB, WITESPACE).replace(NEWLINE, WITESPACE);
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    logger.info("Run SQL command '{}'", cmd);
    return executeSql(cmd);
  }

  @Override
  public void cancel(InterpreterContext context) {
    if (currentStatement != null) {
      try {
        currentStatement.cancel();
      } catch (SQLException ex) {
      } finally {
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
        PostgreSqlInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }
}
