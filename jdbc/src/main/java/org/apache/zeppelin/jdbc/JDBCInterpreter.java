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
package org.apache.zeppelin.jdbc;

import static org.apache.commons.lang.StringUtils.containsIgnoreCase;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.security.PrivilegedExceptionAction;
import java.sql.*;
import java.util.*;

import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDriver;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.jdbc.security.JDBCSecurityImpl;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.user.UserCredentials;
import org.apache.zeppelin.user.UsernamePassword;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

/**
 * JDBC interpreter for Zeppelin. This interpreter can also be used for accessing HAWQ,
 * GreenplumDB, MariaDB, MySQL, Postgres and Redshift.
 *
 * <ul>
 * <li>{@code default.url} - JDBC URL to connect to.</li>
 * <li>{@code default.user} - JDBC user name..</li>
 * <li>{@code default.password} - JDBC password..</li>
 * <li>{@code default.driver.name} - JDBC driver name.</li>
 * <li>{@code common.max.result} - Max number of SQL result to display.</li>
 * </ul>
 *
 * <p>
 * How to use: <br/>
 * {@code %jdbc.sql} <br/>
 * {@code
 * SELECT store_id, count(*)
 * FROM retail_demo.order_lineitems_pxf
 * GROUP BY store_id;
 * }
 * </p>
 */
public class JDBCInterpreter extends Interpreter {

  private Logger logger = LoggerFactory.getLogger(JDBCInterpreter.class);

  static final String INTERPRETER_NAME = "jdbc";
  static final String JDBC_DEFAULT_USER_KEY = "default.user";
  static final String JDBC_DEFAULT_PASSWORD_KEY = "default.password";
  static final String COMMON_KEY = "common";
  static final String MAX_LINE_KEY = "max_count";
  static final String MAX_LINE_DEFAULT = "1000";

  static final String DEFAULT_KEY = "default";
  static final String DRIVER_KEY = "driver";
  static final String URL_KEY = "url";
  static final String USER_KEY = "user";
  static final String PASSWORD_KEY = "password";
  static final String DOT = ".";

  private static final char WHITESPACE = ' ';
  private static final char NEWLINE = '\n';
  private static final char TAB = '\t';
  private static final String TABLE_MAGIC_TAG = "%table ";
  private static final String EXPLAIN_PREDICATE = "EXPLAIN ";
  private static final String UPDATE_COUNT_HEADER = "Update Count";

  static final String COMMON_MAX_LINE = COMMON_KEY + DOT + MAX_LINE_KEY;

  static final String DEFAULT_DRIVER = DEFAULT_KEY + DOT + DRIVER_KEY;
  static final String DEFAULT_URL = DEFAULT_KEY + DOT + URL_KEY;
  static final String DEFAULT_USER = DEFAULT_KEY + DOT + USER_KEY;
  static final String DEFAULT_PASSWORD = DEFAULT_KEY + DOT + PASSWORD_KEY;

  static final String EMPTY_COLUMN_VALUE = "";

  private final String CONCURRENT_EXECUTION_KEY = "zeppelin.jdbc.concurrent.use";
  private final String CONCURRENT_EXECUTION_COUNT = "zeppelin.jdbc.concurrent.max_connection";
  private final String DBCP_STRING = "jdbc:apache:commons:dbcp:";

  private final HashMap<String, Properties> basePropretiesMap;
  private final HashMap<String, JDBCUserConfigurations> jdbcUserConfigurationsMap;
  private final Map<String, SqlCompleter> propertyKeySqlCompleterMap;

  private static final Function<CharSequence, InterpreterCompletion> sequenceToStringTransformer =
      new Function<CharSequence, InterpreterCompletion>() {
        public InterpreterCompletion apply(CharSequence seq) {
          return new InterpreterCompletion(seq.toString(), seq.toString());
        }
      };

  private static final List<InterpreterCompletion> NO_COMPLETION = new ArrayList<>();

  public JDBCInterpreter(Properties property) {
    super(property);
    jdbcUserConfigurationsMap = new HashMap<>();
    propertyKeySqlCompleterMap = new HashMap<>();
    basePropretiesMap = new HashMap<>();
  }

  public HashMap<String, Properties> getPropertiesMap() {
    return basePropretiesMap;
  }

  @Override
  public void open() {
    for (String propertyKey : property.stringPropertyNames()) {
      logger.debug("propertyKey: {}", propertyKey);
      String[] keyValue = propertyKey.split("\\.", 2);
      if (2 == keyValue.length) {
        logger.info("key: {}, value: {}", keyValue[0], keyValue[1]);

        Properties prefixProperties;
        if (basePropretiesMap.containsKey(keyValue[0])) {
          prefixProperties = basePropretiesMap.get(keyValue[0]);
        } else {
          prefixProperties = new Properties();
          basePropretiesMap.put(keyValue[0], prefixProperties);
        }
        prefixProperties.put(keyValue[1], property.getProperty(propertyKey));
      }
    }

    Set<String> removeKeySet = new HashSet<>();
    for (String key : basePropretiesMap.keySet()) {
      if (!COMMON_KEY.equals(key)) {
        Properties properties = basePropretiesMap.get(key);
        if (!properties.containsKey(DRIVER_KEY) || !properties.containsKey(URL_KEY)) {
          logger.error("{} will be ignored. {}.{} and {}.{} is mandatory.",
              key, DRIVER_KEY, key, key, URL_KEY);
          removeKeySet.add(key);
        }
      }
    }

    for (String key : removeKeySet) {
      basePropretiesMap.remove(key);
    }
    logger.debug("JDBC PropretiesMap: {}", basePropretiesMap);

    if (!StringUtils.isEmpty(property.getProperty("zeppelin.jdbc.auth.type"))) {
      JDBCSecurityImpl.createSecureConfiguration(property);
    }
    for (String propertyKey : basePropretiesMap.keySet()) {
      propertyKeySqlCompleterMap.put(propertyKey, createSqlCompleter(null));
    }
  }

  private SqlCompleter createSqlCompleter(Connection jdbcConnection) {

    SqlCompleter completer = null;
    try {
      Set<String> keywordsCompletions = SqlCompleter.getSqlKeywordsCompletions(jdbcConnection);
      Set<String> dataModelCompletions =
          SqlCompleter.getDataModelMetadataCompletions(jdbcConnection);
      SetView<String> allCompletions = Sets.union(keywordsCompletions, dataModelCompletions);
      completer = new SqlCompleter(allCompletions, dataModelCompletions);

    } catch (IOException | SQLException e) {
      logger.error("Cannot create SQL completer", e);
    }

    return completer;
  }

  private void initStatementMap() {
    for (JDBCUserConfigurations configurations : jdbcUserConfigurationsMap.values()) {
      try {
        configurations.initStatementMap();
      } catch (Exception e) {
        logger.error("Error while closing paragraphIdStatementMap statement...", e);
      }
    }
  }

  private void initConnectionPoolMap() {
    for (JDBCUserConfigurations configurations : jdbcUserConfigurationsMap.values()) {
      try {
        configurations.initConnectionPoolMap();
      } catch (Exception e) {
        logger.error("Error while closing initConnectionPoolMap...", e);
      }
    }
  }

  @Override
  public void close() {
    try {
      initStatementMap();
      initConnectionPoolMap();
    } catch (Exception e) {
      logger.error("Error while closing...", e);
    }
  }

  private String getEntityName(String replName) {
    StringBuffer entityName = new StringBuffer();
    entityName.append(INTERPRETER_NAME);
    entityName.append(".");
    entityName.append(replName);
    return entityName.toString();
  }

  private String getJDBCDriverName(String user, String propertyKey) {
    StringBuffer driverName = new StringBuffer();
    driverName.append(DBCP_STRING);
    driverName.append(propertyKey);
    driverName.append(user);
    return driverName.toString();
  }

  private boolean existAccountInBaseProperty() {
    return property.containsKey(JDBC_DEFAULT_USER_KEY) &&
      property.containsKey(JDBC_DEFAULT_PASSWORD_KEY);
  }

  private UsernamePassword getUsernamePassword(InterpreterContext interpreterContext,
                                               String replName) {
    UserCredentials uc = interpreterContext.getAuthenticationInfo().getUserCredentials();
    if (uc != null) {
      return uc.getUsernamePassword(replName);
    }
    return null;
  }

  public JDBCUserConfigurations getJDBCConfiguration(String user) {
    JDBCUserConfigurations jdbcUserConfigurations =
      jdbcUserConfigurationsMap.get(user);

    if (jdbcUserConfigurations == null) {
      jdbcUserConfigurations = new JDBCUserConfigurations();
      jdbcUserConfigurationsMap.put(user, jdbcUserConfigurations);
    }

    return jdbcUserConfigurations;
  }

  private void closeDBPool(String user, String propertyKey) throws SQLException {
    PoolingDriver poolingDriver = getJDBCConfiguration(user).removeDBDriverPool(propertyKey);
    if (poolingDriver != null) {
      poolingDriver.closePool(propertyKey + user);
    }
  }

  private void setUserProperty(String propertyKey, InterpreterContext interpreterContext)
      throws SQLException {

    String user = interpreterContext.getAuthenticationInfo().getUser();

    JDBCUserConfigurations jdbcUserConfigurations =
      getJDBCConfiguration(user);
    jdbcUserConfigurations.setPropertyMap(propertyKey, basePropretiesMap.get(propertyKey));

    if (existAccountInBaseProperty()) {
      return;
    }
    jdbcUserConfigurations.cleanUserProperty(propertyKey);

    UsernamePassword usernamePassword = getUsernamePassword(interpreterContext,
      getEntityName(interpreterContext.getReplName()));
    if (usernamePassword != null) {
      jdbcUserConfigurations.setUserProperty(propertyKey, usernamePassword);
    } else {
      closeDBPool(user, propertyKey);
    }
  }

  private void createConnectionPool(String url, String user, String propertyKey,
      Properties properties) throws SQLException, ClassNotFoundException {
    ConnectionFactory connectionFactory =
      new DriverManagerConnectionFactory(url, properties);

    PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(
      connectionFactory, null);
    ObjectPool connectionPool = new GenericObjectPool(poolableConnectionFactory);

    poolableConnectionFactory.setPool(connectionPool);
    Class.forName(properties.getProperty(DRIVER_KEY));
    PoolingDriver driver = new PoolingDriver();
    driver.registerPool(propertyKey + user, connectionPool);
    getJDBCConfiguration(user).saveDBDriverPool(propertyKey, driver);
  }

  private Connection getConnectionFromPool(String url, String user, String propertyKey,
      Properties properties) throws SQLException, ClassNotFoundException {
    String jdbcDriver = getJDBCDriverName(user, propertyKey);

    if (!getJDBCConfiguration(user).isConnectionInDBDriverPool(propertyKey)) {
      createConnectionPool(url, user, propertyKey, properties);
    }
    return DriverManager.getConnection(jdbcDriver);
  }

  public Connection getConnection(String propertyKey, InterpreterContext interpreterContext)
      throws ClassNotFoundException, SQLException, InterpreterException {
    final String user =  interpreterContext.getAuthenticationInfo().getUser();
    Connection connection;
    if (propertyKey == null || basePropretiesMap.get(propertyKey) == null) {
      return null;
    }

    JDBCUserConfigurations jdbcUserConfigurations = getJDBCConfiguration(user);
    setUserProperty(propertyKey, interpreterContext);

    final Properties properties = jdbcUserConfigurations.getPropertyMap(propertyKey);
    final String url = properties.getProperty(URL_KEY);

    if (StringUtils.isEmpty(property.getProperty("zeppelin.jdbc.auth.type"))) {
      connection = getConnectionFromPool(url, user, propertyKey, properties);
    } else {
      UserGroupInformation.AuthenticationMethod authType = JDBCSecurityImpl.getAuthtype(property);

      switch (authType) {
          case KERBEROS:
            if (user == null) {
              connection = getConnectionFromPool(url, user, propertyKey, properties);
            } else {
              if ("hive".equalsIgnoreCase(propertyKey)) {
                connection = getConnectionFromPool(url + ";hive.server2.proxy.user=" + user,
                  user, propertyKey, properties);
              } else {
                UserGroupInformation ugi = null;
                try {
                  ugi = UserGroupInformation.createProxyUser(user,
                    UserGroupInformation.getCurrentUser());
                } catch (Exception e) {
                  logger.error("Error in createProxyUser", e);
                  StringBuilder stringBuilder = new StringBuilder();
                  stringBuilder.append(e.getMessage()).append("\n");
                  stringBuilder.append(e.getCause());
                  throw new InterpreterException(stringBuilder.toString());
                }

                final String poolKey = propertyKey;
                try {
                  connection = ugi.doAs(new PrivilegedExceptionAction<Connection>() {
                    @Override
                    public Connection run() throws Exception {
                      return getConnectionFromPool(url, user, poolKey, properties);
                    }
                  });
                } catch (Exception e) {
                  logger.error("Error in doAs", e);
                  StringBuilder stringBuilder = new StringBuilder();
                  stringBuilder.append(e.getMessage()).append("\n");
                  stringBuilder.append(e.getCause());
                  throw new InterpreterException(stringBuilder.toString());
                }
              }
            }
            break;

          default:
            connection = getConnectionFromPool(url, user, propertyKey, properties);
      }
    }
    propertyKeySqlCompleterMap.put(propertyKey, createSqlCompleter(connection));
    return connection;
  }

  private String getResults(ResultSet resultSet, boolean isTableType)
      throws SQLException {
    ResultSetMetaData md = resultSet.getMetaData();
    StringBuilder msg;
    if (isTableType) {
      msg = new StringBuilder(TABLE_MAGIC_TAG);
    } else {
      msg = new StringBuilder();
    }

    for (int i = 1; i < md.getColumnCount() + 1; i++) {
      if (i > 1) {
        msg.append(TAB);
      }
      msg.append(replaceReservedChars(md.getColumnName(i)));
    }
    msg.append(NEWLINE);

    int displayRowCount = 0;
    while (resultSet.next() && displayRowCount < getMaxResult()) {
      for (int i = 1; i < md.getColumnCount() + 1; i++) {
        Object resultObject;
        String resultValue;
        resultObject = resultSet.getObject(i);
        if (resultObject == null) {
          resultValue = "null";
        } else {
          resultValue = resultSet.getString(i);
        }
        msg.append(replaceReservedChars(resultValue));
        if (i != md.getColumnCount()) {
          msg.append(TAB);
        }
      }
      msg.append(NEWLINE);
      displayRowCount++;
    }
    return msg.toString();
  }

  private boolean isDDLCommand(int updatedCount, int columnCount) throws SQLException {
    return updatedCount < 0 && columnCount <= 0 ? true : false;
  }

  private InterpreterResult executeSql(String propertyKey, String sql,
      InterpreterContext interpreterContext) {
    Connection connection;
    Statement statement;
    ResultSet resultSet = null;
    String paragraphId = interpreterContext.getParagraphId();
    String user = interpreterContext.getAuthenticationInfo().getUser();

    try {
      String results = null;
      connection = getConnection(propertyKey, interpreterContext);

      if (connection == null) {
        return new InterpreterResult(Code.ERROR, "Prefix not found.");
      }

      statement = connection.createStatement();
      if (statement == null) {
        return new InterpreterResult(Code.ERROR, "Prefix not found.");
      }

      try {
        getJDBCConfiguration(user).saveStatement(paragraphId, statement);

        boolean isResultSetAvailable = statement.execute(sql);
        if (isResultSetAvailable) {
          resultSet = statement.getResultSet();

          // Regards that the command is DDL.
          if (isDDLCommand(statement.getUpdateCount(), resultSet.getMetaData().getColumnCount())) {
            results = "Query executed successfully.";
          } else {
            results = getResults(resultSet, !containsIgnoreCase(sql, EXPLAIN_PREDICATE));
          }
        } else {
          // Response contains either an update count or there are no results.
          int updateCount = statement.getUpdateCount();
          results = "Query executed successfully. Affected rows : " + updateCount;
        }
        //In case user ran an insert/update/upsert statement
        if (connection.getAutoCommit() != true) connection.commit();

      } finally {
        if (resultSet != null) {
          try {
            resultSet.close();
          } catch (SQLException e) { /*ignored*/ }
        }
        if (statement != null) {
          try {
            statement.close();
          } catch (SQLException e) { /*ignored*/ }
        }
        if (connection != null) {
          try {
            connection.close();
          } catch (SQLException e) { /*ignored*/ }
        }
        getJDBCConfiguration(user).removeStatement(paragraphId);
      }
      return new InterpreterResult(Code.SUCCESS, results);

    } catch (Exception e) {
      logger.error("Cannot run " + sql, e);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintStream ps = new PrintStream(baos);
      e.printStackTrace(ps);
      String errorMsg = new String(baos.toByteArray(), StandardCharsets.UTF_8);

      try {
        closeDBPool(user, propertyKey);
      } catch (SQLException e1) {
        e1.printStackTrace();
      }

      return new InterpreterResult(Code.ERROR, errorMsg);
    }
  }

  /**
   * For %table response replace Tab and Newline characters from the content.
   */
  private String replaceReservedChars(String str) {
    if (str == null) {
      return EMPTY_COLUMN_VALUE;
    }
    return str.replace(TAB, WHITESPACE).replace(NEWLINE, WHITESPACE);
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    logger.info("Run SQL command '{}'", cmd);
    String propertyKey = getPropertyKey(cmd);

    if (null != propertyKey && !propertyKey.equals(DEFAULT_KEY)) {
      cmd = cmd.substring(propertyKey.length() + 2);
    }

    cmd = cmd.trim();

    logger.info("PropertyKey: {}, SQL command: '{}'", propertyKey, cmd);
    return executeSql(propertyKey, cmd, contextInterpreter);
  }

  @Override
  public void cancel(InterpreterContext context) {
    logger.info("Cancel current query statement.");
    String paragraphId = context.getParagraphId();
    JDBCUserConfigurations jdbcUserConfigurations =
      getJDBCConfiguration(context.getAuthenticationInfo().getUser());
    try {
      jdbcUserConfigurations.cancelStatement(paragraphId);
    } catch (SQLException e) {
      logger.error("Error while cancelling...", e);
    }
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
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    String schedulerName = JDBCInterpreter.class.getName() + this.hashCode();
    return isConcurrentExecution() ?
            SchedulerFactory.singleton().createOrGetParallelScheduler(schedulerName,
                getMaxConcurrentConnection())
            : SchedulerFactory.singleton().createOrGetFIFOScheduler(schedulerName);
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    List<CharSequence> candidates = new ArrayList<>();
    SqlCompleter sqlCompleter = propertyKeySqlCompleterMap.get(getPropertyKey(buf));
    if (sqlCompleter != null && sqlCompleter.complete(buf, cursor, candidates) >= 0) {
      List<InterpreterCompletion> completion;
      completion = Lists.transform(candidates, sequenceToStringTransformer);

      return completion;
    } else {
      return NO_COMPLETION;
    }
  }

  public int getMaxResult() {
    return Integer.valueOf(
        basePropretiesMap.get(COMMON_KEY).getProperty(MAX_LINE_KEY, MAX_LINE_DEFAULT));
  }

  boolean isConcurrentExecution() {
    return Boolean.valueOf(getProperty(CONCURRENT_EXECUTION_KEY));
  }

  int getMaxConcurrentConnection() {
    try {
      return Integer.valueOf(getProperty(CONCURRENT_EXECUTION_COUNT));
    } catch (Exception e) {
      return 10;
    }
  }
}

