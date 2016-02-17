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

package org.apache.zeppelin.hive;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.commons.lang.StringUtils.containsIgnoreCase;

/**
 * Hive interpreter for Zeppelin.
 */
public class HiveInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(HiveInterpreter.class);

  static final String COMMON_KEY = "common";
  static final String MAX_LINE_KEY = "max_count";
  static final String MAX_LINE_DEFAULT = "1000";

  static final String DEFAULT_KEY = "default";
  static final String DRIVER_KEY = "driver";
  static final String URL_KEY = "url";
  static final String USER_KEY = "user";
  static final String PASSWORD_KEY = "password";
  static final String DOT = ".";

  static final char TAB = '\t';
  static final char NEWLINE = '\n';
  static final String EXPLAIN_PREDICATE = "EXPLAIN ";
  static final String TABLE_MAGIC_TAG = "%table ";
  static final String UPDATE_COUNT_HEADER = "Update Count";

  static final String COMMON_MAX_LINE = COMMON_KEY + DOT + MAX_LINE_KEY;

  static final String DEFAULT_DRIVER = DEFAULT_KEY + DOT + DRIVER_KEY;
  static final String DEFAULT_URL = DEFAULT_KEY + DOT + URL_KEY;
  static final String DEFAULT_USER = DEFAULT_KEY + DOT + USER_KEY;
  static final String DEFAULT_PASSWORD = DEFAULT_KEY + DOT + PASSWORD_KEY;

  private final HashMap<String, Properties> propertiesMap;
  private final Map<String, Statement> paragraphIdStatementMap;

  private final Map<String, ArrayList<Connection>> propertyKeyUnusedConnectionListMap;
  private final Map<String, Connection> paragraphIdConnectionMap;

  static {
    Interpreter.register(
        "hql",
        "hive",
        HiveInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add(COMMON_MAX_LINE, MAX_LINE_DEFAULT, "Maximum line of results")
            .add(DEFAULT_DRIVER, "org.apache.hive.jdbc.HiveDriver", "Hive JDBC driver")
            .add(DEFAULT_URL, "jdbc:hive2://localhost:10000", "The URL for HiveServer2.")
            .add(DEFAULT_USER, "hive", "The hive user")
            .add(DEFAULT_PASSWORD, "", "The password for the hive user").build());
  }

  public HiveInterpreter(Properties property) {
    super(property);
    propertiesMap = new HashMap<>();
    propertyKeyUnusedConnectionListMap = new HashMap<>();
    paragraphIdStatementMap = new HashMap<>();
    paragraphIdConnectionMap = new HashMap<>();
  }

  public HashMap<String, Properties> getPropertiesMap() {
    return propertiesMap;
  }

  @Override
  public void open() {
    logger.debug("property: {}", property);

    for (String propertyKey : property.stringPropertyNames()) {
      logger.debug("propertyKey: {}", propertyKey);
      String[] keyValue = propertyKey.split("\\.", 2);
      if (2 == keyValue.length) {
        logger.debug("key: {}, value: {}", keyValue[0], keyValue[1]);
        Properties prefixProperties;
        if (propertiesMap.containsKey(keyValue[0])) {
          prefixProperties = propertiesMap.get(keyValue[0]);
        } else {
          prefixProperties = new Properties();
          propertiesMap.put(keyValue[0], prefixProperties);
        }
        prefixProperties.put(keyValue[1], property.getProperty(propertyKey));
      }
    }

    Set<String> removeKeySet = new HashSet<>();
    for (String key : propertiesMap.keySet()) {
      if (!COMMON_KEY.equals(key)) {
        Properties properties = propertiesMap.get(key);
        if (!properties.containsKey(DRIVER_KEY) || !properties.containsKey(URL_KEY)) {
          logger.error("{} will be ignored. {}.{} and {}.{} is mandatory.",
              key, DRIVER_KEY, key, key, URL_KEY);
          removeKeySet.add(key);
        }
      }
    }

    for (String key : removeKeySet) {
      propertiesMap.remove(key);
    }

    logger.debug("propertiesMap: {}", propertiesMap);
  }

  @Override
  public void close() {
    try {
      for (List<Connection> connectionList : propertyKeyUnusedConnectionListMap.values()) {
        for (Connection c : connectionList) {
          c.close();
        }
      }

      for (Statement statement : paragraphIdStatementMap.values()) {
        statement.close();
      }
      paragraphIdStatementMap.clear();

      for (Connection connection : paragraphIdConnectionMap.values()) {
        connection.close();
      }
      paragraphIdConnectionMap.clear();

    } catch (SQLException e) {
      logger.error("Error while closing...", e);
    }
  }

  public Connection getConnection(String propertyKey) throws ClassNotFoundException, SQLException {
    Connection connection = null;
    if (propertyKey == null || propertiesMap.get(propertyKey) == null) {
      return null;
    }
    if (propertyKeyUnusedConnectionListMap.containsKey(propertyKey)) {
      ArrayList<Connection> connectionList = propertyKeyUnusedConnectionListMap.get(propertyKey);
      if (0 != connectionList.size()) {
        connection = propertyKeyUnusedConnectionListMap.get(propertyKey).remove(0);
        if (null != connection && connection.isClosed()) {
          connection.close();
          connection = null;
        }
      }
    }
    if (null == connection) {
      Properties properties = propertiesMap.get(propertyKey);
      Class.forName(properties.getProperty(DRIVER_KEY));
      String url = properties.getProperty(URL_KEY);
      String user = properties.getProperty(USER_KEY);
      String password = properties.getProperty(PASSWORD_KEY);
      if (null != user && null != password) {
        connection = DriverManager.getConnection(url, user, password);
      } else {
        connection = DriverManager.getConnection(url, properties);
      }
    }
    return connection;
  }

  public Statement getStatement(String propertyKey, String paragraphId)
      throws SQLException, ClassNotFoundException {
    Connection connection;
    if (paragraphIdConnectionMap.containsKey(paragraphId)) {
      // Never enter for now.
      connection = paragraphIdConnectionMap.get(paragraphId);
    } else {
      connection = getConnection(propertyKey);
    }
    
    if (connection == null) {
      return null;
    }

    Statement statement = connection.createStatement();
    if (isStatementClosed(statement)) {
      connection = getConnection(propertyKey);
      statement = connection.createStatement();
    }
    paragraphIdConnectionMap.put(paragraphId, connection);
    paragraphIdStatementMap.put(paragraphId, statement);

    return statement;
  }

  private boolean isStatementClosed(Statement statement) {
    try {
      return statement.isClosed();
    } catch (Throwable t) {
      logger.debug("{} doesn't support isClosed method", statement);
      return false;
    }
  }

  public InterpreterResult executeSql(String propertyKey, String sql,
                                      InterpreterContext interpreterContext) {
    String paragraphId = interpreterContext.getParagraphId();

    try {

      Statement statement = getStatement(propertyKey, paragraphId);

      if (statement == null) {
        return new InterpreterResult(Code.ERROR, "Prefix not found.");
      }

      statement.setMaxRows(getMaxResult());

      StringBuilder msg;

      if (containsIgnoreCase(sql, EXPLAIN_PREDICATE)) {
        msg = new StringBuilder();
      } else {
        msg = new StringBuilder(TABLE_MAGIC_TAG);
      }

      ResultSet resultSet = null;

      try {
        boolean isResultSetAvailable = statement.execute(sql);

        if (isResultSetAvailable) {
          resultSet = statement.getResultSet();

          ResultSetMetaData md = resultSet.getMetaData();

          for (int i = 1; i < md.getColumnCount() + 1; i++) {
            if (i > 1) {
              msg.append(TAB);
            }
            msg.append(md.getColumnName(i));
          }
          msg.append(NEWLINE);

          int displayRowCount = 0;
          while (resultSet.next() && displayRowCount < getMaxResult()) {
            for (int i = 1; i < md.getColumnCount() + 1; i++) {
              msg.append(resultSet.getString(i));
              if (i != md.getColumnCount()) {
                msg.append(TAB);
              }
            }
            msg.append(NEWLINE);
            displayRowCount++;
          }
        } else {
          // Response contains either an update count or there are no results.
          int updateCount = statement.getUpdateCount();
          msg.append(UPDATE_COUNT_HEADER).append(NEWLINE);
          msg.append(updateCount).append(NEWLINE);
        }
      } finally {
        try {
          if (resultSet != null) {
            resultSet.close();
          }
          statement.close();
        } finally {
          moveConnectionToUnused(propertyKey, paragraphId);
        }
      }

      return new InterpreterResult(Code.SUCCESS, msg.toString());

    } catch (SQLException | ClassNotFoundException ex) {
      logger.error("Cannot run " + sql, ex);
      return new InterpreterResult(Code.ERROR, ex.getMessage());
    }
  }

  private void moveConnectionToUnused(String propertyKey, String paragraphId) {
    if (paragraphIdConnectionMap.containsKey(paragraphId)) {
      Connection connection = paragraphIdConnectionMap.remove(paragraphId);
      if (null != connection) {
        if (propertyKeyUnusedConnectionListMap.containsKey(propertyKey)) {
          propertyKeyUnusedConnectionListMap.get(propertyKey).add(connection);
        } else {
          ArrayList<Connection> connectionList = new ArrayList<>();
          connectionList.add(connection);
          propertyKeyUnusedConnectionListMap.put(propertyKey, connectionList);
        }
      }
    }
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    String propertyKey = getPropertyKey(cmd);

    if (null != propertyKey && !propertyKey.equals(DEFAULT_KEY)) {
      cmd = cmd.substring(propertyKey.length() + 2);
    }

    cmd = cmd.trim();

    logger.info("PropertyKey: {}, SQL command: '{}'", propertyKey, cmd);

    return executeSql(propertyKey, cmd, contextInterpreter);
  }

  private int getMaxResult() {
    return Integer.valueOf(
        propertiesMap.get(COMMON_KEY).getProperty(MAX_LINE_KEY, MAX_LINE_DEFAULT));
  }

  public String getPropertyKey(String cmd) {
    boolean firstLineIndex = cmd.startsWith("(");

    if (firstLineIndex) {
      int configStartIndex = cmd.indexOf("(");
      int configLastIndex = cmd.indexOf(")");
      if (configStartIndex != -1 && configLastIndex != -1) {
        return cmd.substring(configStartIndex + 1, configLastIndex);
      } else {
        return null;
      }
    } else {
      return DEFAULT_KEY;
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
    String paragraphId = context.getParagraphId();
    try {
      paragraphIdStatementMap.get(paragraphId).cancel();
    } catch (SQLException e) {
      logger.error("Error while cancelling...", e);
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
    return SchedulerFactory.singleton().createOrGetParallelScheduler(
        HiveInterpreter.class.getName() + this.hashCode(), 10);
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }
}
