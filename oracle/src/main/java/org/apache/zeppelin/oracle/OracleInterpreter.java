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

package org.apache.zeppelin.oracle;

import oracle.ucp.admin.UniversalConnectionPoolManager;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import org.apache.zeppelin.interpreter.util.SqlSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import oracle.ucp.jdbc.PoolDataSourceFactory;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.UniversalConnectionPoolStatistics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Oracle interpreter for Zeppelin - JDBC-based implementation
 */
public class OracleInterpreter extends Interpreter {

  private static final Logger LOGGER =
    LoggerFactory.getLogger(OracleInterpreter.class);

  // Driver Properties
  private static final String ORACLE_DRIVER_NAME = "oracle.jdbc.OracleDriver";
  private static final String ORACLE_URL_KEY = "oracle.connection.url";
  private static final String ORACLE_USER_KEY = "oracle.connection.username";
  private static final String ORACLE_PASSWORD_KEY =
    "oracle.connection.password";
  private static final String ORACLE_DRIVER_KEY = "oracle.connection.driver";
  private static final String MAX_RESULT_KEY = "oracle.maxResults";

  // UCP Properties
  private static final String UCP_CONNECTION_POOL_NAME =
    "oracleucp.connectionPoolName";
  private static final String UCP_INITIAL_POOL_SIZE =
    "oracleucp.initialPoolSize";
  private static final String UCP_MIN_POOL_SIZE =
    "oracleucp.minPoolSize";
  private static final String UCP_MAX_POOL_SIZE =
    "oracleucp.maxPoolSize";
  private static final String UCP_CONNECTION_WAIT_TIMEOUT =
    "oracleucp.connectionWaitTimeout";
  private static final String UCP_INACTIVE_CONNECTION_TIMEOUT =
    "oracleucp.inactiveConnectionTimeout";
  private static final String UCP_VALIDATE_CONNECTION_ON_BORROW =
    "oracleucp.validateConnectionOnBorrow";
  private static final String UCP_ABANDONED_CONNECTION_TIMEOUT =
    "oracleucp.abandonedConnectionTimeout";
  private static final String UCP_TIME_TO_LIVE_CONNECTION_TIMEOUT =
    "oracleucp.timeToLiveConnectionTimeout";
  private static final String UCP_MAX_STATEMENTS =
    "oracleucp.maxStatements";

  // Wallet Properties
  private static final String ORACLE_WALLET_LOCATION =
    "oracle.connection.wallet.location";
  private static final String ORACLE_TNS_ALIAS = "oracle.connection.tns.alias";

  private int maxResults;
  private PoolDataSource pds;

  private Map<String, Statement> paragraphStatements = new ConcurrentHashMap<>();

  public OracleInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public void open() throws InterpreterException {
    LOGGER.info("Opening Oracle Interpreter");

    maxResults = Integer.parseInt(getProperty(MAX_RESULT_KEY, "1000"));

    try {
      String driverName = getProperty(ORACLE_DRIVER_KEY, ORACLE_DRIVER_NAME);
      Class.forName(driverName);
      LOGGER.info("Oracle JDBC Driver loaded successfully: {}", driverName);
    } catch (ClassNotFoundException e) {
      LOGGER.error("Failed to load Oracle JDBC driver", e);
      throw new InterpreterException("Oracle JDBC driver not found", e);
    }

    LOGGER.info("Oracle Interpreter opened successfully!");

    // Initialize UCP
    try {
      pds = PoolDataSourceFactory.getPoolDataSource();
      pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");

      String walletLocation = getProperty(ORACLE_WALLET_LOCATION);
      String tnsAlias = getProperty(ORACLE_TNS_ALIAS);
      String url = getProperty(ORACLE_URL_KEY);
      String user = getProperty(ORACLE_USER_KEY);
      String password = getProperty(ORACLE_PASSWORD_KEY);

      if (walletLocation != null && !walletLocation.trim().isEmpty()) {
        // Wallet-based connection
        System.setProperty("oracle.net.tns_admin", walletLocation);
        if (tnsAlias == null || tnsAlias.trim().isEmpty()) {
          throw new InterpreterException("TNS alias must be provided when using wallet");
        }
        url = "jdbc:oracle:thin:@" + tnsAlias + "?TNS_ADMIN=" + walletLocation;
        pds.setConnectionProperty("oracle.net.ssl_server_dn_match", "true");
      }

      pds.setURL(url);
      pds.setUser(user);
      pds.setPassword(password);

      /*
       * Configure pool properties using interpreter settings
       * Ensure the properties are within valid ranges
       */
      pds.setConnectionPoolName(
        getProperty(UCP_CONNECTION_POOL_NAME, "ZEPPELIN_UCP_JDBC"));

      pds.setInitialPoolSize(Integer.parseInt(
        getProperty(UCP_INITIAL_POOL_SIZE, "0")));

      pds.setMinPoolSize(Integer.parseInt(
        getProperty(UCP_MIN_POOL_SIZE, "1")));

      String maxPoolSizeStr = getProperty(UCP_MAX_POOL_SIZE);
      if (maxPoolSizeStr != null && !maxPoolSizeStr.trim().isEmpty()) {
        pds.setMaxPoolSize(Integer.parseInt(maxPoolSizeStr.trim()));
      }
      pds.setConnectionWaitTimeout(Integer.parseInt(
        getProperty(UCP_CONNECTION_WAIT_TIMEOUT, "3")));

      pds.setInactiveConnectionTimeout(Integer.parseInt(
        getProperty(UCP_INACTIVE_CONNECTION_TIMEOUT, "0")));

      pds.setValidateConnectionOnBorrow(Boolean.parseBoolean(
        getProperty(UCP_VALIDATE_CONNECTION_ON_BORROW, "false")));
      if (pds.getValidateConnectionOnBorrow()) {
        pds.setSQLForValidateConnection("SELECT 1 FROM DUAL");
      }

      pds.setAbandonedConnectionTimeout(Integer.parseInt(
        getProperty(UCP_ABANDONED_CONNECTION_TIMEOUT, "0")));

      pds.setTimeToLiveConnectionTimeout(Integer.parseInt(
        getProperty(UCP_TIME_TO_LIVE_CONNECTION_TIMEOUT, "0")));

      pds.setMaxStatements(Integer.parseInt(
        getProperty(UCP_MAX_STATEMENTS, "0")));

      // Log initial pool statistics
      logPoolStatistics();
    } catch (SQLException e) {
      LOGGER.error("Failed to initialize UCP", e);
      throw new InterpreterException("UCP initialization failed", e);
    } catch (NumberFormatException e) {
      LOGGER.error("Invalid number format in UCP properties", e);
      throw new InterpreterException("Invalid UCP configuration", e);
    }
  }

  private void logPoolStatistics() throws SQLException {
    UniversalConnectionPoolStatistics stats = pds.getStatistics();
    if (stats == null) {
      LOGGER.warn(
        "UCP statistics not available yet (pool may not be initialized)");
      return;
    }
    LOGGER.info(
      "UCP Pool Statistics: " + "Total Connections: " +
        stats.getTotalConnectionsCount() + ", Available: " +
        stats.getAvailableConnectionsCount() + ", Borrowed: " +
        stats.getBorrowedConnectionsCount() + ", Abandoned Connections: " +
        stats.getAbandonedConnectionsCount());
  }

  private Connection getConnection() throws SQLException {
    return pds.getConnection();
  }

  @Override
  public InterpreterResult interpret(String sql, InterpreterContext context) {
    LOGGER.info("Executing SQL: {}", sql);
    if (sql == null || sql.trim().isEmpty()) {
      return new InterpreterResult(Code.SUCCESS, "");
    }

    sql = sql.trim();

    try {
      Connection conn = getConnection();

      // Execute SQL
      return executeSql(sql, conn, context);

    } catch (SQLException e) {
      LOGGER.error("Error executing SQL", e);
      String errorMsg =
        "SQL Error: " + e.getMessage() + "\n" + "SQLState: " + e.getSQLState() + "\n" +
          "Error Code: " + e.getErrorCode() + "\n\n";
      return new InterpreterResult(Code.ERROR, errorMsg);
    } catch (Exception e) {
      LOGGER.error("Unexpected error", e);
      return new InterpreterResult(Code.ERROR, e.getMessage());
    }
  }

  private InterpreterResult executeSql(String sql, Connection conn, InterpreterContext context)
    throws SQLException {
    List<String> sqlList = new ArrayList<>();
    String[] lines = sql.split("\\r?\\n");
    StringBuilder buffer = new StringBuilder();
    for (String line : lines) {
      if (line.trim().equals("/")) {
        parseSqlBlocks(buffer.toString(), sqlList);
        buffer = new StringBuilder();
      } else {
        buffer.append(line).append("\n");
      }
    }
    /*
     * Handle any remaining SQL or PL/SQL code if the input doesn't end with a delimiter
     * ensures the final block is classified and executed so no user input is lost.
     */
    parseSqlBlocks(buffer.toString(), sqlList);
    StringBuilder resultMsg = new StringBuilder();

    try (Connection autoClosedConn = conn) {
      for (String s : sqlList) {
        if (s.trim().isEmpty()) {
          continue;
        }
        try (Statement enableStmt = autoClosedConn.createStatement();
             Statement statement = autoClosedConn.createStatement()) {

          // Enable DBMS_OUTPUT
          enableStmt.execute("BEGIN dbms_output.enable(null); END;");

          // Execute the SQL
          paragraphStatements.put(context.getParagraphId(), statement);
          statement.setMaxRows(maxResults);
          boolean isResultSet = statement.execute(s);

          String regularResult;
          if (isResultSet) {
            try (ResultSet resultSet = statement.getResultSet()) {
              regularResult = formatAsTable(resultSet);
            }
          } else {
            int updateCount = statement.getUpdateCount();
            if (updateCount >= 0) {
              regularResult = "%text " + updateCount + " row" + (updateCount > 1 ? "s" : "") + " affected.\n";
            } else {
              regularResult = "%text Executed successfully.\n";
            }
          }

          // Fetch DBMS_OUTPUT
          String output = getDbmsOutput(autoClosedConn);
          resultMsg.append(regularResult);
          if (output != null && !output.trim().isEmpty()) {
            resultMsg.append("%text ").append(output).append("\n");
          }
        } finally {
          paragraphStatements.remove(context.getParagraphId());
        }

        // Log pool statistics after each execution for monitoring
        try {
          logPoolStatistics();
        } catch (SQLException e) {
          LOGGER.warn("Failed to log pool statistics", e);
        }
      }
    }

    return new InterpreterResult(Code.SUCCESS, resultMsg.toString());
  }

  private void parseSqlBlocks(String bufferedSql, List<String> sqlList) {
    String trimmed = bufferedSql.trim();
    if (trimmed.isEmpty()) return;

    String normalized = trimmed.replaceAll("\\s+", " ").toUpperCase();

    boolean isPlSql = normalized.startsWith("BEGIN") ||
      normalized.startsWith("DECLARE") ||
      normalized.matches("^CREATE\\s+(OR\\s+REPLACE\\s+)?" +
          "(PROCEDURE|FUNCTION|PACKAGE|TRIGGER|TYPE|PACKAGE|VIEW\\s+BODY)\\b.*");

    if (isPlSql) {
      sqlList.add(bufferedSql);
    } else {
      SqlSplitter sqlSplitter = new SqlSplitter();
      sqlList.addAll(sqlSplitter.splitSql(bufferedSql));
    }
  }

  private String formatAsTable(ResultSet resultSet) throws SQLException {
    ResultSetMetaData md = resultSet.getMetaData();
    int columnCount = md.getColumnCount();

    StringBuilder msg = new StringBuilder();

    // Build header
    for (int i = 1; i <= columnCount; i++) {
      if (i > 1) {
        msg.append("\t");
      }
      msg.append(md.getColumnName(i));
    }
    msg.append("\n");

    // Build rows
    int rowCount = 0;
    while (resultSet.next() && rowCount < maxResults) {
      for (int i = 1; i <= columnCount; i++) {
        if (i > 1) {
          msg.append("\t");
        }
        Object value = resultSet.getObject(i);
        msg.append(value != null ? value.toString() : "null");
      }
      msg.append("\n");
      rowCount++;
    }

    if (rowCount == maxResults) {
      msg.append("\n... (Results limited to ")
         .append(maxResults)
         .append(" rows)");
    }

    LOGGER.info("Query returned {} rows", rowCount);

    return "%table " + msg.toString();
  }

  private String getDbmsOutput(Connection conn) throws SQLException {
    StringBuilder output = new StringBuilder();
    try (CallableStatement cs = conn.prepareCall(
      "{call dbms_output.get_line(?, ?)}")) {
      cs.registerOutParameter(1, Types.VARCHAR); // The line
      cs.registerOutParameter(2, Types.INTEGER); // Status: 0=line, 1=none

      while (true) {
        cs.execute();
        int status = cs.getInt(2);
        if (status != 0)
          break;
        String line = cs.getString(1);
        if (line != null) {
          output.append(line).append("\n");
        }
      }
    }
    return output.toString();
  }

  @Override
  public void close() {
    LOGGER.info("Closing Oracle Interpreter");

    // Cancel statements
    for (Map.Entry<String, Statement> entry : paragraphStatements.entrySet()) {
      try {
        entry.getValue().cancel();
        LOGGER.info("Cancelled active statement for paragraph: {}", entry.getKey());
      } catch (SQLException e) {
        LOGGER.error("Error cancelling active statement", e);
      }
    }
    paragraphStatements.clear();

    // Destroy the pool
    if (pds != null) {
      try {
        UniversalConnectionPoolManager mgr =
          UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager();
        mgr.destroyConnectionPool(pds.getConnectionPoolName());
        LOGGER.info("UCP pool destroyed");
      } catch (Exception e) {
        LOGGER.error("Failed to destroy UCP pool", e);
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
  public void cancel(InterpreterContext context) {
    LOGGER.info("Cancel called");
    String paragraphId = context.getParagraphId();
    Statement stmt = paragraphStatements.get(paragraphId);
    if (stmt != null) {
      try {
        stmt.cancel();
        LOGGER.info("Cancelled statement for paragraph: " + paragraphId);
      } catch (SQLException e) {
        LOGGER.error("Error cancelling statement", e);
      } finally {
        paragraphStatements.remove(paragraphId);
      }
    }
  }
}
