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
package org.apache.zeppelin.presto;

import java.sql.*;
import java.util.List;
import java.util.Properties;

import org.apache.zeppelin.interpreter.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;

/**
 * Presto interpreter for Zeppelin.
 */
public class PrestoInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(PrestoInterpreter.class);
  int commandTimeOut = 600000;

  static final String PRESTOSERVER_URL = "presto.url";
  static final String PRESTOSERVER_USER = "presto.user";
  static final String PRESTOSERVER_PASSWORD = "presto.password";

  static {
    Interpreter.register(
      "presto",
      "presto",
      PrestoInterpreter.class.getName(),
      new InterpreterPropertyBuilder()
        .add(PRESTOSERVER_URL, "jdbc:presto://localhost:8089", "The URL for Presto")
        .add(PRESTOSERVER_USER, "Presto", "The Presto user")
        .add(PRESTOSERVER_PASSWORD, "", "The password for the Presto user").build());
  }

  public PrestoInterpreter(Properties property) {
    super(property);
  }

  Connection jdbcConnection;
  Exception exceptionOnConnect;

  //Test only method
  public Connection getJdbcConnection()
      throws SQLException {
    String url = getProperty(PRESTOSERVER_URL);
    String user = getProperty(PRESTOSERVER_USER);
    String password = getProperty(PRESTOSERVER_PASSWORD);

    return DriverManager.getConnection(url, user, password);
  }

  @Override
  public void open() {
    logger.info("Jdbc open connection called!");
    try {
      String driverName = "com.facebook.presto.jdbc.PrestoDriver";
      Class.forName(driverName);
    } catch (ClassNotFoundException e) {
      logger.error("Can not open connection", e);
      exceptionOnConnect = e;
      return;
    }
    try {
      jdbcConnection = getJdbcConnection();
      exceptionOnConnect = null;
      logger.info("Successfully created Jdbc connection");
    }
    catch (SQLException e) {
      logger.error("Cannot open connection", e);
      exceptionOnConnect = e;
    }
  }

  @Override
  public void close() {
    try {
      if (jdbcConnection != null) {
        jdbcConnection.close();
      }
    }
    catch (SQLException e) {
      logger.error("Cannot close connection", e);
    }
    finally {
      jdbcConnection = null;
      exceptionOnConnect = null;
    }
  }

  Statement currentStatement;
  private InterpreterResult executeSql(String sql) {
    try {
      if (exceptionOnConnect != null) {
        return new InterpreterResult(Code.ERROR, exceptionOnConnect.getMessage());
      }
      currentStatement = jdbcConnection.createStatement();
      StringBuilder msg = null;
      if (StringUtils.containsIgnoreCase(sql, "EXPLAIN ")) {
        //return the explain as text, make this visual explain later
        msg = new StringBuilder();
      }
      else {
        msg = new StringBuilder("%table ");
      }
      ResultSet res = currentStatement.executeQuery(sql);
      try {
        ResultSetMetaData md = res.getMetaData();
        for (int i = 1; i < md.getColumnCount() + 1; i++) {
          if (i == 1) {
            msg.append(md.getColumnName(i));
          } else {
            msg.append("\t" + md.getColumnName(i));
          }
        }
        msg.append("\n");
        while (res.next()) {
          for (int i = 1; i < md.getColumnCount() + 1; i++) {
            msg.append(res.getString(i) + "\t");
          }
          msg.append("\n");
        }
      }
      finally {
        try {
          res.close();
          currentStatement.close();
        }
        finally {
          currentStatement = null;
        }
      }

      InterpreterResult rett = new InterpreterResult(Code.SUCCESS, msg.toString());
      return rett;
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
        PrestoInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }

}
