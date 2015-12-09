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
 * Interpreter for Apache. Look at https://drill.apache.org for more details.
 */
public class DrillInterpreter
    extends Interpreter {

  private static final String RS_ROW_DELIM = "\n";

  private static final String RS_COL_DELIM = "\t";

  private static final Logger LOGGER = LoggerFactory.getLogger(DrillInterpreter.class);

  private static final String DRILL_JDBC_DRIVER = "org.apache.drill.jdbc.Driver";

  private static final String STORAGE_PLUGIN = "drill.storage.plugin";

  private static final String ZK_CONNECT = "drill.zk.connect";

  private static final String DIRECTORY = "drill.zk.directory";

  private static final String CLUSTER_ID = "drill.cluster.id";

  private static final String JDBC_URI_FORMAT = "jdbc:drill:schema=%s;zk=%s%s/%s";

  private Connection connection;

  private Statement statement;

  private String initError;

  private boolean initialised;

  private String jdbcURI;

  // Register Drill Interpreter
  static {
    Interpreter.register("drill", "drill", DrillInterpreter.class.getName(),
        new InterpreterPropertyBuilder().add(STORAGE_PLUGIN, "hive", "Storage Plugin")
            .add(ZK_CONNECT, "localhost:2181", "Zookeeper Connect")
            .add(DIRECTORY, "/Drill", "Drill Directory in Zookeeper")
            .add(CLUSTER_ID, "drillbits1", "Drill Cluster ID").build());
  }

  /**
   * @param properties
   *          Details of all Configured Properties
   */
  public DrillInterpreter(
    Properties properties) {

    super(properties);
    this.jdbcURI = String.format(JDBC_URI_FORMAT, properties.getProperty(STORAGE_PLUGIN),
        properties.getProperty(ZK_CONNECT), properties.getProperty(DIRECTORY),
        properties.getProperty(CLUSTER_ID));
    LOGGER.info("Created DrillInterpreter for JDBC URI {}", this.jdbcURI);
  }

  @Override
  public void open() {

    LOGGER.info("Initializing Drill JDBC Connection");
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
    InterpreterContext arg1) {

    ResultSet results = null;
    InterpreterResult interpreterResult = null;
    try {

      if (!this.initialised) {
        return new InterpreterResult(Code.ERROR, initError);
      }

      this.statement = this.connection.createStatement();
      StringBuilder interpreterResultStr = new StringBuilder("%table ");

      results = this.statement.executeQuery(sql);
      ResultSetMetaData rsMetaData = results.getMetaData();
      for (int i = 1; i < rsMetaData.getColumnCount() + 1; i++) {
        if (i == 1) {
          interpreterResultStr.append(rsMetaData.getColumnName(i));
        } else {
          interpreterResultStr.append(RS_COL_DELIM + rsMetaData.getColumnName(i));
        }
      }
      interpreterResultStr.append(RS_ROW_DELIM);

      while (results.next()) {
        for (int i = 1; i < rsMetaData.getColumnCount() + 1; i++) {
          interpreterResultStr.append(results.getString(i) + RS_COL_DELIM);
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
      if (this.initialised && this.connection != null) {
        this.connection.close();
        this.connection = null;
      }
    } catch (Exception e) {
      LOGGER.error("Error while Closing Drill JDBC Connection", e);
    }
  }

  @Override
  public FormType getFormType() {

    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(
    InterpreterContext context) {

    // TODO(malur): Return progress
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

    // TODO(malur): Support Cancel
  }
}
