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

import com.google.common.base.Joiner;
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

import static java.lang.String.format;

/**
 * Hive interpreter for Zeppelin.
 */
public class HiveInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(HiveInterpreter.class);

  static final String DEFAULT_KEY = "default";
  static final String DRIVER_KEY = "driver";
  static final String URL_KEY = "url";
  static final String USER_KEY = "user";
  static final String PASSWORD_KEY = "password";
  static final String DOT = ".";

  static final char TSV = '\t';
  static final char LINEFEED = '\n';

  static final String DEFAULT_DRIVER = DEFAULT_KEY + DOT + DRIVER_KEY;
  static final String DEFAULT_URL = DEFAULT_KEY + DOT + URL_KEY;
  static final String DEFAULT_USER = DEFAULT_KEY + DOT + USER_KEY;
  static final String DEFAULT_PASSWORD = DEFAULT_KEY + DOT + PASSWORD_KEY;

  private final HashMap<String, Properties> propertiesMap;
  private final Map<String, Connection> keyConnectionMap;
  private final Map<String, Statement> paragraphIdStatementMap;

  static {
    Interpreter.register(
        "hql",
        "hive",
        HiveInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add(DEFAULT_DRIVER, "org.apache.hive.jdbc.HiveDriver", "Hive JDBC driver")
            .add(DEFAULT_URL, "jdbc:hive2://localhost:10000", "The URL for HiveServer2.")
            .add(DEFAULT_USER, "hive", "The hive user")
            .add(DEFAULT_PASSWORD, "", "The password for the hive user").build());
  }

  public HiveInterpreter(Properties property) {
    super(property);
    propertiesMap = new HashMap<>();
    keyConnectionMap = new HashMap<>();
    paragraphIdStatementMap = new HashMap<>();
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
      Properties properties = propertiesMap.get(key);
      if (!properties.containsKey(DRIVER_KEY) || !properties.containsKey(URL_KEY)) {
        logger.error("{} will be ignored. {}.{} and {}.{} is mandatory.", key, DRIVER_KEY, key, key, URL_KEY);
        removeKeySet.add(key);
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
      for (Statement statement : paragraphIdStatementMap.values()) {
        statement.close();
      }

      for (Connection connection : keyConnectionMap.values()) {
        connection.close();
      }
    } catch (SQLException e) {
      logger.error("Error while closing...", e);
    }
  }

  public Connection getConnection(String propertyKey) throws ClassNotFoundException, SQLException {
    Connection connection = null;
    if (keyConnectionMap.containsKey(propertyKey)) {
      connection = keyConnectionMap.get(propertyKey);
      if (connection.isClosed() || connection.isValid(10)) {
        connection.close();
        connection = null;
        keyConnectionMap.remove(propertyKey);
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
      keyConnectionMap.put(propertyKey, connection);
    }
    return connection;
  }

  public Statement getStatement(String propertyKey, String paragraphId)
      throws SQLException, ClassNotFoundException {
    Statement statement = null;
    if (paragraphIdStatementMap.containsKey(paragraphId)) {
      statement = paragraphIdStatementMap.get(paragraphId);
      if (statement.isClosed()) {
        statement = null;
        paragraphIdStatementMap.remove(paragraphId);
      }
    }
    if (null == statement) {
      statement = getConnection(propertyKey).createStatement();
      paragraphIdStatementMap.put(paragraphId, statement);
    }
    return statement;
  }

  public ResultSet executeSql(String propertyKey,
                               String sql,
                               InterpreterContext interpreterContext)
      throws SQLException, ClassNotFoundException {
    String paragraphId = interpreterContext.getParagraphId();

    Statement statement = getStatement(propertyKey, paragraphId);
    ResultSet resultSet = statement.executeQuery(sql);
    return resultSet;
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    String propertyKey = getPropertyKey(cmd);

    if (null != propertyKey) {
      cmd = cmd.substring(propertyKey.length() + 2);
    } else {
      propertyKey = DEFAULT_KEY;
    }

    cmd = cmd.trim();

    logger.info("PropertyKey: {}, SQL command: '{}'", propertyKey, cmd);

    try {
      ResultSet resultSet = executeSql(propertyKey, cmd, contextInterpreter);
      ResultSetMetaData resultSetMetaData = resultSet.getMetaData();

      StringBuilder sb = new StringBuilder();

      if (!StringUtils.containsIgnoreCase(cmd, "explain")) {
        sb.append("%table");
      }
      int columnCount = resultSetMetaData.getColumnCount();
      ArrayList<String> fields = new ArrayList<>();
      for (int i = 0; i < columnCount; i++) {
        fields.add(resultSetMetaData.getColumnName(i + 1));
      }

      sb.append(Joiner.on(TSV).join(fields));
      sb.append(LINEFEED);

      while (resultSet.next()) {
        fields.clear();
        for (int i = 0; i < columnCount; i++) {
          fields.add(resultSet.getString(i + 1));
        }
        sb.append(Joiner.on(TSV).join(fields));
        sb.append(LINEFEED);
      }

      return new InterpreterResult(Code.SUCCESS, sb.toString());

    } catch (ClassNotFoundException | SQLException e) {
      return new InterpreterResult(Code.ERROR,
          format("%s%c%s", e.getClass().getName(), LINEFEED, e.getMessage()));
    }
  }

  public String getPropertyKey(String cmd) {
    int firstLineIndex = cmd.indexOf("\n");
    if (-1 == firstLineIndex) {
      firstLineIndex = cmd.length();
    }
    int configStartIndex = cmd.indexOf("(");
    int configLastIndex = cmd.indexOf(")");
    if (configStartIndex != -1 && configLastIndex != -1
        && configLastIndex < firstLineIndex && configLastIndex < firstLineIndex) {
      return cmd.substring(configStartIndex + 1, configLastIndex);
    }
    return null;
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
