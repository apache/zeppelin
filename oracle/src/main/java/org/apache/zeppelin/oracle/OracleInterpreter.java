package org.apache.zeppelin.oracle;

import oracle.ucp.admin.UniversalConnectionPoolManager;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

import java.sql.CallableStatement;

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
  private static final String MAX_RESULT_KEY = "oracle.max.result";

  // UCP Properties
  private static final String ORACLE_UCP_CONNECTION_POOL_NAME =
    "zeppelin.oracleucp.connectionPoolName";
  private static final String ORACLE_UCP_INITIAL_POOL_SIZE =
    "zeppelin.oracleucp.initialPoolSize";
  private static final String ORACLE_UCP_MIN_POOL_SIZE =
    "zeppelin.oracleucp.minPoolSize";
  private static final String ORACLE_UCP_MAX_POOL_SIZE =
    "zeppelin.oracleucp.maxPoolSize";
  private static final String ORACLE_UCP_CONNECTION_WAIT_TIMEOUT =
    "zeppelin.oracleucp.connectionWaitTimeout";
  private static final String ORACLE_UCP_INACTIVE_CONNECTION_TIMEOUT =
    "zeppelin.oracleucp.inactiveConnectionTimeout";
  private static final String ORACLE_UCP_VALIDATE_CONNECTION_ON_BORROW =
    "zeppelin.oracleucp.validateConnectionOnBorrow";
  private static final String ORACLE_UCP_ABANDONED_CONNECTION_TIMEOUT =
    "zeppelin.oracleucp.abandonedConnectionTimeout";
  private static final String ORACLE_UCP_TIME_TO_LIVE_CONNECTION_TIMEOUT =
    "zeppelin.oracleucp.timeToLiveConnectionTimeout";
  private static final String ORACLE_UCP_MAX_STATEMENTS =
    "zeppelin.oracleucp.maxStatements";

  // Wallet Properties
  private static final String ORACLE_WALLET_LOCATION =
    "oracle.connection.wallet.location";
  private static final String ORACLE_TNS_ALIAS = "oracle.connection.tns.alias";

  private int maxResult;
  private PoolDataSource pds;

  private Map<String, Statement> paragraphStatements = new ConcurrentHashMap<>();

  public OracleInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public void open() {
    LOGGER.info("Opening Oracle Interpreter");

    maxResult =
      Math.max(1, Integer.parseInt(getProperty(MAX_RESULT_KEY, "1000")));

    try {
      String driverName = getProperty(ORACLE_DRIVER_KEY, ORACLE_DRIVER_NAME);
      Class.forName(driverName);
      LOGGER.info("Oracle JDBC Driver loaded successfully: {}", driverName);
    } catch (ClassNotFoundException e) {
      LOGGER.error("Failed to load Oracle JDBC driver", e);
      throw new RuntimeException("Oracle JDBC driver not found", e);
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

      if (StringUtils.isNotBlank(walletLocation)) {
        // Wallet-based connection
        System.setProperty("oracle.net.tns_admin", walletLocation);
        if (StringUtils.isBlank(tnsAlias)) {
          throw new RuntimeException(
            "TNS alias must be provided when using wallet");
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

      pds.setConnectionPoolName(getProperty(ORACLE_UCP_CONNECTION_POOL_NAME));

      pds.setInitialPoolSize(Math.max(0, Integer.parseInt(
        getProperty(ORACLE_UCP_INITIAL_POOL_SIZE))));

      pds.setMinPoolSize(
        Math.max(0, Integer.parseInt(getProperty(ORACLE_UCP_MIN_POOL_SIZE))));

      pds.setMaxPoolSize(
        Math.max(1, Integer.parseInt(getProperty(ORACLE_UCP_MAX_POOL_SIZE))));

      pds.setConnectionWaitTimeout(Math.max(0, Integer.parseInt(
        getProperty(ORACLE_UCP_CONNECTION_WAIT_TIMEOUT))));

      pds.setInactiveConnectionTimeout(Math.max(0, Integer.parseInt(
        getProperty(ORACLE_UCP_INACTIVE_CONNECTION_TIMEOUT))));

      pds.setValidateConnectionOnBorrow(Boolean.parseBoolean(
        getProperty(ORACLE_UCP_VALIDATE_CONNECTION_ON_BORROW)));
      if (pds.getValidateConnectionOnBorrow()) {
        pds.setSQLForValidateConnection("SELECT 1 FROM DUAL");
      }

      pds.setAbandonedConnectionTimeout(Math.max(0, Integer.parseInt(
        getProperty(ORACLE_UCP_ABANDONED_CONNECTION_TIMEOUT))));

      pds.setTimeToLiveConnectionTimeout(Math.max(0, Integer.parseInt(
        getProperty(ORACLE_UCP_TIME_TO_LIVE_CONNECTION_TIMEOUT))));

      pds.setMaxStatements(
        Math.max(0, Integer.parseInt(getProperty(ORACLE_UCP_MAX_STATEMENTS))));

      // Log initial pool statistics
      logPoolStatistics();
    } catch (SQLException e) {
      LOGGER.error("Failed to initialize UCP", e);
      throw new RuntimeException("UCP initialization failed", e);
    } catch (NumberFormatException e) {
      LOGGER.error("Invalid number format in UCP properties", e);
      throw new RuntimeException("Invalid UCP configuration", e);
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
    // TODO : Additional stats can be added
  }

  private Connection getConnection() throws SQLException {
    return pds.getConnection();
  }


  @Override
  public InterpreterResult interpret(String sql, InterpreterContext context) {
    LOGGER.info("Executing SQL: {}", sql);

    if (StringUtils.isBlank(sql)) {
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
        "SQL Error: " + e.getMessage() + "\n" + "SQLState: " + e.getSQLState() + "\n" + "Error Code: " + e.getErrorCode() + "\n\n" + ExceptionUtils.getStackTrace(
          e);
      return new InterpreterResult(Code.ERROR, errorMsg);
    } catch (Exception e) {
      LOGGER.error("Unexpected error", e);
      return new InterpreterResult(Code.ERROR, ExceptionUtils.getStackTrace(e));
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
    try {
      for (String s : sqlList) {
        if (s.trim().isEmpty())
          continue;
        Statement enableStmt = null;
        Statement statement = null;
        ResultSet resultSet = null;
        try {
          // Enable DBMS_OUTPUT
          enableStmt = conn.createStatement();
          enableStmt.execute("BEGIN dbms_output.enable(null); END;");
          // Execute the sql
          statement = conn.createStatement();
          paragraphStatements.put(context.getParagraphId(), statement);
          statement.setMaxRows(maxResult);
          boolean isResultSet = statement.execute(s);
          String regularResult;
          if (isResultSet) {
            resultSet = statement.getResultSet();
            regularResult = formatAsTable(resultSet);
          } else {
            int updateCount = statement.getUpdateCount();
            if (updateCount >= 0) {
              regularResult = "%text Affected rows: " + updateCount + "\n";
            } else {
              regularResult = "%text Executed successfully.\n";
            }
          }
          // Fetch DBMS_OUTPUT
          String output = getDbmsOutput(conn);
          resultMsg.append(regularResult)
                   .append("%text ")
                   .append(output)
                   .append("\n");
        } finally {
          if (resultSet != null) {
            try {
              resultSet.close();
            } catch (SQLException e) { /* ignore */ }
          }
          if (statement != null) {
            try {
              statement.close();
            } catch (SQLException e) { /* ignore */ }
          }
          if (enableStmt != null) {
            try {
              enableStmt.close();
            } catch (SQLException e) { /* ignore */ }
          }
        }
        paragraphStatements.remove(context.getParagraphId());
        // Log pool statistics after each execution for monitoring
        try {
          logPoolStatistics();
        } catch (SQLException e) {
          LOGGER.warn("Failed to log pool statistics", e);
        }
      }
    } finally {
      // Close the connection to return it to the pool
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e) { /* ignore */ }
      }
    }
    return new InterpreterResult(Code.SUCCESS, resultMsg.toString());
  }

  private void parseSqlBlocks(String bufferedSql, List<String> sqlList) {
    String trimmed = bufferedSql.trim().toUpperCase();
    if (trimmed.isEmpty()) return;
    boolean isPlSql = trimmed.startsWith("BEGIN") ||
                      trimmed.startsWith("DECLARE") ||
                      trimmed.matches("^(CREATE(\\s+OR\\s+REPLACE)?)\\s+(PROCEDURE|FUNCTION|PACKAGE|TRIGGER|TYPE)\\b.*");
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
    while (resultSet.next() && rowCount < maxResult) {
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

    if (rowCount == maxResult) {
      msg.append("\n... (Results limited to ")
         .append(maxResult)
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
        UniversalConnectionPoolManager mgr = UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager();
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
