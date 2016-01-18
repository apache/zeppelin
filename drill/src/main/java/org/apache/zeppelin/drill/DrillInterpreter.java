/*
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

package org.apache.zeppelin.drill;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

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
 * Interpreter for Apache Drill. Look at https://drill.apache.org for more details.
 * 
 * @author malur
 */
public class DrillInterpreter
    extends Interpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(DrillInterpreter.class);

  private static final String DRILL_JDBC_DRIVER = "org.apache.drill.jdbc.Driver";

  private static final String DEFAULT_ZK_CONNECT = "localhost:2181";

  private static final String DEFAULT_DRILL_DIRECTORY = "/Drill";

  private static final String DEFAULT_DRILL_CLUSTER_ID = "drillbits1";

  private static final String DEFAULT_STORAGE_PLUGIN = "hive";

  private static final String JDBC_URI_FORMAT = "jdbc:drill:schema=%s;zk=%s%s/%s";

  private static final char RS_ROW_DELIM = '\n';

  private static final char RS_COL_DELIM = '\t';

  private static final char WHITESPACE = ' ';

  static final String STORAGE_PLUGIN = "drill.storage.plugin";

  static final String ZK_CONNECT = "drill.zk.connect";

  static final String DRILL_DIRECTORY = "drill.zk.directory";

  static final String DRILL_CLUSTER_ID = "drill.cluster.id";

  static final String DRILL_MAX_RESULT = "drill.max.result";

  static final String DEFAULT_MAX_RESULT = "1000";

  static final String EMPTY_COLUMN_VALUE = "";

  private Connection connection;

  private Statement statement;

  private String initError;

  private boolean initialised;

  private String jdbcURI;

  private int maxResult;

  // Register Drill Interpreter
  static {
    Interpreter.register("drill", "drill", DrillInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add(STORAGE_PLUGIN, DEFAULT_STORAGE_PLUGIN, "Storage Plugin")
            .add(ZK_CONNECT, DEFAULT_ZK_CONNECT, "Zookeeper Connect")
            .add(DRILL_DIRECTORY, DEFAULT_DRILL_DIRECTORY, "Drill Directory in Zookeeper")
            .add(DRILL_CLUSTER_ID, DEFAULT_DRILL_CLUSTER_ID, "Drill Cluster ID")
            .add(DRILL_MAX_RESULT, DEFAULT_MAX_RESULT, "Max number of SQL result to display.")
            .build());
  }

  /**
   * @param properties
   *          Details of all Configured Properties
   */
  public DrillInterpreter(
    Properties properties) {

    super(properties);
    this.jdbcURI = String.format(JDBC_URI_FORMAT,
        properties.getProperty(STORAGE_PLUGIN, DEFAULT_STORAGE_PLUGIN),
        properties.getProperty(ZK_CONNECT, DEFAULT_ZK_CONNECT),
        properties.getProperty(DRILL_DIRECTORY, DEFAULT_DRILL_DIRECTORY),
        properties.getProperty(DRILL_CLUSTER_ID, DEFAULT_DRILL_CLUSTER_ID));
    this.maxResult = Integer.valueOf(properties.getProperty(DRILL_MAX_RESULT, DEFAULT_MAX_RESULT));
    LOGGER.info("Created DrillInterpreter for JDBC URI {}", this.jdbcURI);
  }

  @Override
  public void open() {

    LOGGER.info("Initializing Drill JDBC Connection");

    // Close if open already
    this.close();

    try {
      Class.forName(DRILL_JDBC_DRIVER);
      this.connection = DriverManager.getConnection(this.jdbcURI);
      this.initialised = true;
      LOGGER.info("Successfully Initialized Drill JDBC Connection");
    } catch (SQLException e) {
      this.initError = e.getMessage();
      LOGGER.error("Cannot Initialize Drill Connection. Reason: {}", this.initError, e);
    } catch (ClassNotFoundException e) {
      LOGGER.error("Drill Driver Class {} not found in classpath", DRILL_JDBC_DRIVER, e);
    }
  }

  @Override
  public InterpreterResult interpret(
    String sql,
    InterpreterContext interpreterContext) {

    ResultSet results = null;
    InterpreterResult interpreterResult = null;
    try {

      if (!this.initialised) {
        return new InterpreterResult(Code.ERROR, initError);
      }

      this.statement = getConnection().createStatement();
      this.statement.setMaxRows(this.maxResult);
      StringBuilder interpreterResultStr = new StringBuilder("%table ");

      results = this.statement.executeQuery(sql);

      ResultSetMetaData md = results.getMetaData();

      for (int i = 1; i < md.getColumnCount() + 1; i++) {
        if (i > 1) {
          interpreterResultStr.append(RS_COL_DELIM);
        }
        interpreterResultStr.append(replaceReservedChars(true, md.getColumnName(i)));
      }
      interpreterResultStr.append(RS_ROW_DELIM);

      while (results.next()) {
        for (int i = 1; i < md.getColumnCount() + 1; i++) {
          interpreterResultStr.append(replaceReservedChars(true, results.getString(i)));
          if (i != md.getColumnCount()) {
            interpreterResultStr.append(RS_COL_DELIM);
          }
        }
        interpreterResultStr.append(RS_ROW_DELIM);
      }

      interpreterResult = new InterpreterResult(Code.SUCCESS, interpreterResultStr.toString());
    } catch (SQLException ex) {
      LOGGER.error("Error running SQL: " + sql, ex);
      return new InterpreterResult(Code.ERROR, ex.getMessage());
    } finally {
      try {
        if (results != null) {
          results.close();
        }
        if (statement != null) {
          statement.close();
        }
      } catch (Exception e) {
        LOGGER.error("Error Closing Result Set. Ignoring.");
      }
    }
    return interpreterResult;
  }

  @Override
  public void close() {

    try {
      if (getConnection() != null) {
        getConnection().close();
      }
    } catch (Exception e) {
      LOGGER.error("Error while Closing Drill JDBC Connection", e);
    } finally {
      this.initError = null;
    }
  }

  @Override
  public FormType getFormType() {

    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(
    InterpreterContext context) {

    return 0;
  }

  @Override
  public Scheduler getScheduler() {

    return SchedulerFactory.singleton()
        .createOrGetFIFOScheduler(DrillInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<String> completion(
    String buf,
    int cursor) {

    return null;
  }

  @Override
  public void cancel(
    InterpreterContext arg0) {

    if (statement != null) {
      try {
        statement.cancel();
      } catch (SQLException e) {
        LOGGER.error("Error while Cancelling the SQL execution", e);
      }
    }
  }

  private String replaceReservedChars(
    boolean isTableResponseType,
    String str) {

    if (str == null) {
      return EMPTY_COLUMN_VALUE;
    }
    return (!isTableResponseType) ? str
        : str.replace(RS_COL_DELIM, WHITESPACE).replace(RS_ROW_DELIM, WHITESPACE);
  }

  boolean isInitialised() {

    return initialised;
  }

  // Test only methods
  protected Connection getConnection() {

    return connection;
  }

  String getJdbcURI() {

    return this.jdbcURI;
  }

  String getInitError() {

    return this.initError;
  }

  int getMaxResult() {

    return this.maxResult;
  }
}
