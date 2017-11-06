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

import java.io.IOException;
import java.security.PrivilegedExceptionAction;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDriver;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.alias.CredentialProvider;
import org.apache.hadoop.security.alias.CredentialProviderFactory;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.KerberosInterpreter;
import org.apache.zeppelin.interpreter.ResultMessages;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.jdbc.security.JDBCSecurityImpl;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.user.UserCredentials;
import org.apache.zeppelin.user.UsernamePassword;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.commons.lang.StringUtils.containsIgnoreCase;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isNotEmpty;
import static org.apache.hadoop.security.UserGroupInformation.AuthenticationMethod.KERBEROS;

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
public class JDBCInterpreter extends KerberosInterpreter {

  private Logger logger = LoggerFactory.getLogger(JDBCInterpreter.class);

  static final String INTERPRETER_NAME = "jdbc";
  static final String COMMON_KEY = "common";
  static final String MAX_LINE_KEY = "max_count";
  static final int MAX_LINE_DEFAULT = 1000;

  static final String DEFAULT_KEY = "default";
  static final String DRIVER_KEY = "driver";
  static final String URL_KEY = "url";
  static final String USER_KEY = "user";
  static final String PASSWORD_KEY = "password";
  static final String PRECODE_KEY = "precode";
  static final String COMPLETER_SCHEMA_FILTERS_KEY = "completer.schemaFilters";
  static final String COMPLETER_TTL_KEY = "completer.ttlInSeconds";
  static final String DEFAULT_COMPLETER_TTL = "120";
  static final String SPLIT_QURIES_KEY = "splitQueries";
  static final String JDBC_JCEKS_FILE = "jceks.file";
  static final String JDBC_JCEKS_CREDENTIAL_KEY = "jceks.credentialKey";
  static final String PRECODE_KEY_TEMPLATE = "%s.precode";
  static final String DOT = ".";

  private static final char WHITESPACE = ' ';
  private static final char NEWLINE = '\n';
  private static final char TAB = '\t';
  private static final String TABLE_MAGIC_TAG = "%table ";
  private static final String EXPLAIN_PREDICATE = "EXPLAIN ";

  static final String COMMON_MAX_LINE = COMMON_KEY + DOT + MAX_LINE_KEY;

  static final String DEFAULT_DRIVER = DEFAULT_KEY + DOT + DRIVER_KEY;
  static final String DEFAULT_URL = DEFAULT_KEY + DOT + URL_KEY;
  static final String DEFAULT_USER = DEFAULT_KEY + DOT + USER_KEY;
  static final String DEFAULT_PASSWORD = DEFAULT_KEY + DOT + PASSWORD_KEY;
  static final String DEFAULT_PRECODE = DEFAULT_KEY + DOT + PRECODE_KEY;

  static final String EMPTY_COLUMN_VALUE = "";

  private final String CONCURRENT_EXECUTION_KEY = "zeppelin.jdbc.concurrent.use";
  private final String CONCURRENT_EXECUTION_COUNT = "zeppelin.jdbc.concurrent.max_connection";
  private final String DBCP_STRING = "jdbc:apache:commons:dbcp:";

  private final HashMap<String, Properties> basePropretiesMap;
  private final HashMap<String, JDBCUserConfigurations> jdbcUserConfigurationsMap;
  private final HashMap<String, SqlCompleter> sqlCompletersMap;

  private int maxLineResults;

  public JDBCInterpreter(Properties property) {
    super(property);
    jdbcUserConfigurationsMap = new HashMap<>();
    basePropretiesMap = new HashMap<>();
    sqlCompletersMap = new HashMap<>();
    maxLineResults = MAX_LINE_DEFAULT;
  }

  @Override
  protected boolean runKerberosLogin() {
    try {
      if (UserGroupInformation.isLoginKeytabBased()) {
        UserGroupInformation.getLoginUser().reloginFromKeytab();
        return true;
      } else if (UserGroupInformation.isLoginTicketBased()) {
        UserGroupInformation.getLoginUser().reloginFromTicketCache();
        return true;
      }
    } catch (Exception e) {
      logger.error("Unable to run kinit for zeppelin", e);
    }
    return false;
  }

  public HashMap<String, Properties> getPropertiesMap() {
    return basePropretiesMap;
  }

  @Override
  public void open() {
    super.open();
    for (String propertyKey : properties.stringPropertyNames()) {
      logger.debug("propertyKey: {}", propertyKey);
      String[] keyValue = propertyKey.split("\\.", 2);
      if (2 == keyValue.length) {
        logger.debug("key: {}, value: {}", keyValue[0], keyValue[1]);

        Properties prefixProperties;
        if (basePropretiesMap.containsKey(keyValue[0])) {
          prefixProperties = basePropretiesMap.get(keyValue[0]);
        } else {
          prefixProperties = new Properties();
          basePropretiesMap.put(keyValue[0].trim(), prefixProperties);
        }
        prefixProperties.put(keyValue[1].trim(), getProperty(propertyKey));
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

    setMaxLineResults();
  }


  protected boolean isKerboseEnabled() {
    if (!isEmpty(getProperty("zeppelin.jdbc.auth.type"))) {
      UserGroupInformation.AuthenticationMethod authType = JDBCSecurityImpl.getAuthtype(properties);
      if (authType.equals(KERBEROS)) {
        return true;
      }
    }
    return false;
  }


  private void setMaxLineResults() {
    if (basePropretiesMap.containsKey(COMMON_KEY) &&
        basePropretiesMap.get(COMMON_KEY).containsKey(MAX_LINE_KEY)) {
      maxLineResults = Integer.valueOf(basePropretiesMap.get(COMMON_KEY).getProperty(MAX_LINE_KEY));
    }
  }

  private SqlCompleter createOrUpdateSqlCompleter(SqlCompleter sqlCompleter,
      final Connection connection, String propertyKey, final String buf, final int cursor) {
    String schemaFiltersKey = String.format("%s.%s", propertyKey, COMPLETER_SCHEMA_FILTERS_KEY);
    String sqlCompleterTtlKey = String.format("%s.%s", propertyKey, COMPLETER_TTL_KEY);
    final String schemaFiltersString = getProperty(schemaFiltersKey);
    int ttlInSeconds = Integer.valueOf(
        StringUtils.defaultIfEmpty(getProperty(sqlCompleterTtlKey), DEFAULT_COMPLETER_TTL)
    );
    final SqlCompleter completer;
    if (sqlCompleter == null) {
      completer = new SqlCompleter(ttlInSeconds);
    } else {
      completer = sqlCompleter;
    }
    ExecutorService executorService = Executors.newFixedThreadPool(1);
    executorService.execute(new Runnable() {
      @Override
      public void run() {
        completer.createOrUpdateFromConnection(connection, schemaFiltersString, buf, cursor);
      }
    });

    executorService.shutdown();

    try {
      // protection to release connection
      executorService.awaitTermination(3, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      logger.warn("Completion timeout", e);
      if (connection != null) {
        try {
          connection.close();
        } catch (SQLException e1) {
          logger.warn("Error close connection", e1);
        }
      }
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
    for (String key : jdbcUserConfigurationsMap.keySet()) {
      try {
        closeDBPool(key, DEFAULT_KEY);
      } catch (SQLException e) {
        logger.error("Error while closing database pool.", e);
      }
      try {
        JDBCUserConfigurations configurations = jdbcUserConfigurationsMap.get(key);
        configurations.initConnectionPoolMap();
      } catch (SQLException e) {
        logger.error("Error while closing initConnectionPoolMap.", e);
      }
    }
  }

  @Override
  public void close() {
    super.close();
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

  private boolean existAccountInBaseProperty(String propertyKey) {
    return basePropretiesMap.get(propertyKey).containsKey(USER_KEY) &&
        !isEmpty((String) basePropretiesMap.get(propertyKey).get(USER_KEY)) &&
        basePropretiesMap.get(propertyKey).containsKey(PASSWORD_KEY);
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
      throws SQLException, IOException, InterpreterException {

    String user = interpreterContext.getAuthenticationInfo().getUser();

    JDBCUserConfigurations jdbcUserConfigurations =
      getJDBCConfiguration(user);
    if (basePropretiesMap.get(propertyKey).containsKey(USER_KEY) &&
        !basePropretiesMap.get(propertyKey).getProperty(USER_KEY).isEmpty()) {
      String password = getPassword(basePropretiesMap.get(propertyKey));
      if (!isEmpty(password)) {
        basePropretiesMap.get(propertyKey).setProperty(PASSWORD_KEY, password);
      }
    }
    jdbcUserConfigurations.setPropertyMap(propertyKey, basePropretiesMap.get(propertyKey));
    if (existAccountInBaseProperty(propertyKey)) {
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
      throws ClassNotFoundException, SQLException, InterpreterException, IOException {
    final String user =  interpreterContext.getAuthenticationInfo().getUser();
    Connection connection;
    if (propertyKey == null || basePropretiesMap.get(propertyKey) == null) {
      return null;
    }

    JDBCUserConfigurations jdbcUserConfigurations = getJDBCConfiguration(user);
    setUserProperty(propertyKey, interpreterContext);

    final Properties properties = jdbcUserConfigurations.getPropertyMap(propertyKey);
    final String url = properties.getProperty(URL_KEY);

    if (isEmpty(getProperty("zeppelin.jdbc.auth.type"))) {
      connection = getConnectionFromPool(url, user, propertyKey, properties);
    } else {
      UserGroupInformation.AuthenticationMethod authType =
          JDBCSecurityImpl.getAuthtype(getProperties());

      final String connectionUrl = appendProxyUserToURL(url, user, propertyKey);

      JDBCSecurityImpl.createSecureConfiguration(getProperties(), authType);
      switch (authType) {
        case KERBEROS:
          if (user == null || "false".equalsIgnoreCase(
              getProperty("zeppelin.jdbc.auth.kerberos.proxy.enable"))) {
            connection = getConnectionFromPool(connectionUrl, user, propertyKey, properties);
          } else {
            if (basePropretiesMap.get(propertyKey).containsKey("proxy.user.property")) {
              connection = getConnectionFromPool(connectionUrl, user, propertyKey, properties);
            } else {
              UserGroupInformation ugi = null;
              try {
                ugi = UserGroupInformation.createProxyUser(
                    user, UserGroupInformation.getCurrentUser());
              } catch (Exception e) {
                logger.error("Error in getCurrentUser", e);
                throw new InterpreterException("Error in getCurrentUser", e);
              }

              final String poolKey = propertyKey;
              try {
                connection = ugi.doAs(new PrivilegedExceptionAction<Connection>() {
                  @Override
                  public Connection run() throws Exception {
                    return getConnectionFromPool(connectionUrl, user, poolKey, properties);
                  }
                });
              } catch (Exception e) {
                logger.error("Error in doAs", e);
                throw new InterpreterException("Error in doAs", e);
              }
            }
          }
          break;

        default:
          connection = getConnectionFromPool(connectionUrl, user, propertyKey, properties);
      }
    }

    return connection;
  }

  private String appendProxyUserToURL(String url, String user, String propertyKey) {
    StringBuilder connectionUrl = new StringBuilder(url);

    if (user != null && !user.equals("anonymous") &&
        basePropretiesMap.get(propertyKey).containsKey("proxy.user.property")) {

      Integer lastIndexOfUrl = connectionUrl.indexOf("?");
      if (lastIndexOfUrl == -1) {
        lastIndexOfUrl = connectionUrl.length();
      }
      logger.info("Using proxy user as :" + user);
      logger.info("Using proxy property for user as :" +
          basePropretiesMap.get(propertyKey).getProperty("proxy.user.property"));
      connectionUrl.insert(lastIndexOfUrl, ";" +
          basePropretiesMap.get(propertyKey).getProperty("proxy.user.property") + "=" + user + ";");
    } else if (user != null && !user.equals("anonymous") && url.contains("hive")) {
      logger.warn("User impersonation for hive has changed please refer: http://zeppelin.apache" +
          ".org/docs/latest/interpreter/jdbc.html#apache-hive");
    }

    return connectionUrl.toString();
  }

  private String getPassword(Properties properties) throws IOException, InterpreterException {
    if (isNotEmpty(properties.getProperty(PASSWORD_KEY))) {
      return properties.getProperty(PASSWORD_KEY);
    } else if (isNotEmpty(properties.getProperty(JDBC_JCEKS_FILE))
        && isNotEmpty(properties.getProperty(JDBC_JCEKS_CREDENTIAL_KEY))) {
      try {
        Configuration configuration = new Configuration();
        configuration.set(CredentialProviderFactory.CREDENTIAL_PROVIDER_PATH,
            properties.getProperty(JDBC_JCEKS_FILE));
        CredentialProvider provider = CredentialProviderFactory.getProviders(configuration).get(0);
        CredentialProvider.CredentialEntry credEntry =
            provider.getCredentialEntry(properties.getProperty(JDBC_JCEKS_CREDENTIAL_KEY));
        if (credEntry != null) {
          return new String(credEntry.getCredential());
        } else {
          throw new InterpreterException("Failed to retrieve password from JCEKS from key: "
              + properties.getProperty(JDBC_JCEKS_CREDENTIAL_KEY));
        }
      } catch (Exception e) {
        logger.error("Failed to retrieve password from JCEKS \n" +
            "For file: " + properties.getProperty(JDBC_JCEKS_FILE) +
            "\nFor key: " + properties.getProperty(JDBC_JCEKS_CREDENTIAL_KEY), e);
        throw e;
      }
    }
    return null;
  }

  private String getResults(ResultSet resultSet, boolean isTableType, MutableBoolean isComplete)
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
    while (resultSet.next()) {
      if (displayRowCount >= getMaxResult()) {
        isComplete.setValue(false);
        break;
      }
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

  /*
  inspired from https://github.com/postgres/pgadmin3/blob/794527d97e2e3b01399954f3b79c8e2585b908dd/
    pgadmin/dlg/dlgProperty.cpp#L999-L1045
   */
  protected ArrayList<String> splitSqlQueries(String sql) {
    ArrayList<String> queries = new ArrayList<>();
    StringBuilder query = new StringBuilder();
    char character;

    Boolean multiLineComment = false;
    Boolean singleLineComment = false;
    Boolean quoteString = false;
    Boolean doubleQuoteString = false;

    for (int item = 0; item < sql.length(); item++) {
      character = sql.charAt(item);

      if ((singleLineComment && (character == '\n' || item == sql.length() - 1))
          || (multiLineComment && character == '/' && sql.charAt(item - 1) == '*')) {
        singleLineComment = false;
        multiLineComment = false;
        if (item == sql.length() - 1 && query.length() > 0) {
          queries.add(StringUtils.trim(query.toString()));
        }
        continue;
      }

      if (singleLineComment || multiLineComment) {
        continue;
      }

      if (character == '\'') {
        if (quoteString) {
          quoteString = false;
        } else if (!doubleQuoteString) {
          quoteString = true;
        }
      }

      if (character == '"') {
        if (doubleQuoteString && item > 0) {
          doubleQuoteString = false;
        } else if (!quoteString) {
          doubleQuoteString = true;
        }
      }

      if (!quoteString && !doubleQuoteString && !multiLineComment && !singleLineComment
          && sql.length() > item + 1) {
        if (character == '-' && sql.charAt(item + 1) == '-') {
          singleLineComment = true;
          continue;
        }

        if (character == '/' && sql.charAt(item + 1) == '*') {
          multiLineComment = true;
          continue;
        }
      }

      if (character == ';' && !quoteString && !doubleQuoteString) {
        queries.add(StringUtils.trim(query.toString()));
        query = new StringBuilder();
      } else if (item == sql.length() - 1) {
        query.append(character);
        queries.add(StringUtils.trim(query.toString()));
      } else {
        query.append(character);
      }
    }

    return queries;
  }

  public InterpreterResult executePrecode(InterpreterContext interpreterContext) {
    InterpreterResult interpreterResult = null;
    for (String propertyKey : basePropretiesMap.keySet()) {
      String precode = getProperty(String.format("%s.precode", propertyKey));
      if (StringUtils.isNotBlank(precode)) {
        interpreterResult = executeSql(propertyKey, precode, interpreterContext);
        if (interpreterResult.code() != Code.SUCCESS) {
          break;
        }
      }
    }

    return interpreterResult;
  }

  private InterpreterResult executeSql(String propertyKey, String sql,
      InterpreterContext interpreterContext) {
    Connection connection = null;
    Statement statement;
    ResultSet resultSet = null;
    String paragraphId = interpreterContext.getParagraphId();
    String user = interpreterContext.getAuthenticationInfo().getUser();

    boolean splitQuery = false;
    String splitQueryProperty = getProperty(String.format("%s.%s", propertyKey, SPLIT_QURIES_KEY));
    if (StringUtils.isNotBlank(splitQueryProperty) && splitQueryProperty.equalsIgnoreCase("true")) {
      splitQuery = true;
    }

    InterpreterResult interpreterResult = new InterpreterResult(InterpreterResult.Code.SUCCESS);
    try {
      connection = getConnection(propertyKey, interpreterContext);
    } catch (Exception e) {
      String errorMsg = ExceptionUtils.getStackTrace(e);
      try {
        closeDBPool(user, propertyKey);
      } catch (SQLException e1) {
        logger.error("Cannot close DBPool for user, propertyKey: " + user + propertyKey, e1);
      }
      interpreterResult.add(errorMsg);
      return new InterpreterResult(Code.ERROR, interpreterResult.message());
    }
    if (connection == null) {
      return new InterpreterResult(Code.ERROR, "Prefix not found.");
    }

    try {
      List<String> sqlArray;
      if (splitQuery) {
        sqlArray = splitSqlQueries(sql);
      } else {
        sqlArray = Arrays.asList(sql);
      }

      for (int i = 0; i < sqlArray.size(); i++) {
        String sqlToExecute = sqlArray.get(i);
        statement = connection.createStatement();

        // fetch n+1 rows in order to indicate there's more rows available (for large selects)
        statement.setFetchSize(getMaxResult());
        statement.setMaxRows(getMaxResult() + 1);

        if (statement == null) {
          return new InterpreterResult(Code.ERROR, "Prefix not found.");
        }

        try {
          getJDBCConfiguration(user).saveStatement(paragraphId, statement);

          boolean isResultSetAvailable = statement.execute(sqlToExecute);
          getJDBCConfiguration(user).setConnectionInDBDriverPoolSuccessful(propertyKey);
          if (isResultSetAvailable) {
            resultSet = statement.getResultSet();

            // Regards that the command is DDL.
            if (isDDLCommand(statement.getUpdateCount(),
                resultSet.getMetaData().getColumnCount())) {
              interpreterResult.add(InterpreterResult.Type.TEXT,
                  "Query executed successfully.");
            } else {
              MutableBoolean isComplete = new MutableBoolean(true);
              String results = getResults(resultSet,
                  !containsIgnoreCase(sqlToExecute, EXPLAIN_PREDICATE), isComplete);
              interpreterResult.add(results);
              if (!isComplete.booleanValue()) {
                interpreterResult.add(ResultMessages.getExceedsLimitRowsMessage(getMaxResult(),
                    String.format("%s.%s", COMMON_KEY, MAX_LINE_KEY)));
              }
            }
          } else {
            // Response contains either an update count or there are no results.
            int updateCount = statement.getUpdateCount();
            interpreterResult.add(InterpreterResult.Type.TEXT,
                "Query executed successfully. Affected rows : " +
                    updateCount);
          }
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
        }
      }
    } catch (Throwable e) {
      logger.error("Cannot run " + sql, e);
      String errorMsg = ExceptionUtils.getStackTrace(e);
      interpreterResult.add(errorMsg);
      return new InterpreterResult(Code.ERROR, interpreterResult.message());
    } finally {
      //In case user ran an insert/update/upsert statement
      if (connection != null) {
        try {
          if (!connection.getAutoCommit()) {
            connection.commit();
          }
          connection.close();
        } catch (SQLException e) { /*ignored*/ }
      }
      getJDBCConfiguration(user).removeStatement(paragraphId);
    }
    return interpreterResult;
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
    logger.debug("Run SQL command '{}'", cmd);
    String propertyKey = getPropertyKey(cmd);

    if (null != propertyKey && !propertyKey.equals(DEFAULT_KEY)) {
      cmd = cmd.substring(propertyKey.length() + 2);
    }

    cmd = cmd.trim();
    logger.debug("PropertyKey: {}, SQL command: '{}'", propertyKey, cmd);
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
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) throws InterpreterException {
    List<InterpreterCompletion> candidates = new ArrayList<>();
    String propertyKey = getPropertyKey(buf);
    String sqlCompleterKey =
        String.format("%s.%s", interpreterContext.getAuthenticationInfo().getUser(), propertyKey);
    SqlCompleter sqlCompleter = sqlCompletersMap.get(sqlCompleterKey);

    Connection connection = null;
    try {
      if (interpreterContext != null) {
        connection = getConnection(propertyKey, interpreterContext);
      }
    } catch (ClassNotFoundException | SQLException | IOException e) {
      logger.warn("SQLCompleter will created without use connection");
    }

    sqlCompleter = createOrUpdateSqlCompleter(sqlCompleter, connection, propertyKey, buf, cursor);
    sqlCompletersMap.put(sqlCompleterKey, sqlCompleter);
    sqlCompleter.complete(buf, cursor, candidates);

    return candidates;
  }

  public int getMaxResult() {
    return maxLineResults;
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
