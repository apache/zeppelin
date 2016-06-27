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
package org.apache.zeppelin.ignite;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * Apache Ignite SQL interpreter (http://ignite.incubator.apache.org/).
 *
 * Use {@code ignite.jdbc.url} property to set up JDBC connection URL.
 * URL has the following pattern:
 * {@code jdbc:ignite://<hostname>:<port>/<cache_name>}
 *
 * <ul>
 *     <li>Hostname is required.</li>
 *     <li>If port is not defined, 11211 is used (default for Ignite client).</li>
 *     <li>Leave cache_name empty if you are connecting to a default cache.
 *     Note that the cache name is case sensitive.</li>
 * </ul>
 */
public class IgniteSqlInterpreter extends Interpreter {
  private static final String IGNITE_JDBC_DRIVER_NAME = "org.apache.ignite.IgniteJdbcDriver";

  static final String IGNITE_JDBC_URL = "ignite.jdbc.url";

  static {
    Interpreter.register(
        "ignitesql",
        "ignite",
        IgniteSqlInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add(IGNITE_JDBC_URL,
                "jdbc:ignite:cfg://default-ignite-jdbc.xml", "Ignite JDBC connection URL.")
            .build());
  }

  private Logger logger = LoggerFactory.getLogger(IgniteSqlInterpreter.class);

  private Connection conn;

  private Throwable connEx;

  private Statement curStmt;

  public IgniteSqlInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    try {
      Class.forName(IGNITE_JDBC_DRIVER_NAME);
    } catch (ClassNotFoundException e) {
      logger.error("Can't open connection", e);
      connEx = e;
      return;
    }

    try {
      logger.info("connect to " + getProperty(IGNITE_JDBC_URL));

      conn = DriverManager.getConnection(getProperty(IGNITE_JDBC_URL));
      connEx = null;

      logger.info("Successfully created JDBC connection");
    } catch (SQLException e) {
      logger.error("Can't open connection: ", e);
      connEx = e;
    }
  }

  @Override
  public void close() {
    try {
      if (conn != null) {
        conn.close();
      }
    } catch (SQLException e) {
      throw new InterpreterException(e);
    } finally {
      conn = null;
      connEx = null;
    }
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    if (connEx != null) {
      return new InterpreterResult(Code.ERROR, connEx.getMessage());
    }

    StringBuilder msg = new StringBuilder("%table ");

    try (Statement stmt = conn.createStatement()) {

      curStmt = stmt;

      try (ResultSet res = stmt.executeQuery(st)) {
        ResultSetMetaData md = res.getMetaData();

        for (int i = 1; i <= md.getColumnCount(); i++) {
          if (i > 1) {
            msg.append('\t');
          }

          msg.append(md.getColumnName(i));
        }

        msg.append('\n');

        while (res.next()) {
          for (int i = 1; i <= md.getColumnCount(); i++) {
            msg.append(res.getString(i));

            if (i != md.getColumnCount()) {
              msg.append('\t');
            }
          }

          msg.append('\n');
        }
      }
    } catch (Exception e) {
      logger.error("Exception in IgniteSqlInterpreter while InterpreterResult interpret: ", e);
      return IgniteInterpreterUtils.buildErrorResult(e);
    } finally {
      curStmt = null;
    }

    return new InterpreterResult(Code.SUCCESS, msg.toString());
  }

  @Override
  public void cancel(InterpreterContext context) {
    if (curStmt != null) {
      try {
        curStmt.cancel();
      } catch (SQLException e) {
        // No-op.
        logger.info("No-op while cancel in IgniteSqlInterpreter", e);
      } finally {
        curStmt = null;
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
            IgniteSqlInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    return new LinkedList<>();
  }
}
