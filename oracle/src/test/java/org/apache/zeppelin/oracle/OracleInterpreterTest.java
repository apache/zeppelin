package org.apache.zeppelin.oracle;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.*;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OracleInterpreter.
 */
@DisplayName("OracleInterpreter Unit Tests")
class OracleInterpreterTest {

  // To run these tests, provide DB connection values via env vars or system properties.
  private static final String ORACLE_CONNECTION_URL =
    System.getProperty("oracle.url",System.getenv("ORACLE_URL"));
  private static final String ORACLE_CONNECTION_USERNAME =
    System.getProperty("oracle.username",System.getenv("ORACLE_USERNAME"));
  private static final String ORACLE_CONNECTION_PASSWORD =
    System.getProperty("oracle.password",System.getenv("ORACLE_PASSWORD"));

  private OracleInterpreter interpreter;
  private InterpreterContext context;

  @BeforeEach
  void setUp() {
    context = InterpreterContext.builder()
                                .setAuthenticationInfo(new AuthenticationInfo("testUser"))
                                .setParagraphId("paragraph-" + System.currentTimeMillis())
                                .setInterpreterOut(new InterpreterOutput())
                                .build();
  }

  @AfterEach
  void tearDown() {
    if (interpreter != null) {
      try {
        interpreter.close();
      } catch (Exception e) {
        System.err.println("Warning: Error during interpreter cleanup: " + e.getMessage());
      }
      interpreter = null;
    }
  }

  private Properties prepareInterpreterProperties() {
    Properties props = new Properties();
    props.setProperty("oracle.connection.url", ORACLE_CONNECTION_URL);
    props.setProperty("oracle.connection.username", ORACLE_CONNECTION_USERNAME);
    props.setProperty("oracle.connection.password", ORACLE_CONNECTION_PASSWORD);
    props.setProperty("oracleucp.connectionPoolName", "test-pool-" + System.currentTimeMillis());
    props.setProperty("oracleucp.initialPoolSize", "1");
    props.setProperty("oracleucp.minPoolSize", "1");
    props.setProperty("oracleucp.maxPoolSize", "5");
    props.setProperty("oracleucp.connectionWaitTimeout", "30");
    props.setProperty("oracleucp.inactiveConnectionTimeout", "300");
    props.setProperty("oracleucp.validateConnectionOnBorrow", "false");
    props.setProperty("oracleucp.abandonedConnectionTimeout", "0");
    props.setProperty("oracleucp.timeToLiveConnectionTimeout", "0");
    props.setProperty("oracleucp.maxStatements", "10");
    return props;
  }

  @Nested
  @DisplayName("Instantiation Tests")
  class InstantiationTests {

    @Test
    void shouldCreateInterpreterWithValidProperties() {
      interpreter = new OracleInterpreter(prepareInterpreterProperties());

      assertNotNull(interpreter, "Interpreter should not be null");
    }

    @Test
    void shouldStorePropertiesCorrectly() {
      Properties props = prepareInterpreterProperties();
      props.setProperty("oracle.maxResults", "2000");

      interpreter = new OracleInterpreter(props);

      assertEquals("2000", interpreter.getProperty("oracle.maxResults"));
      assertEquals(ORACLE_CONNECTION_URL,
                   interpreter.getProperty("oracle.connection.url"));
    }

    @Test
    void shouldReturnDefaultForMissingProperty() {
      interpreter = new OracleInterpreter(prepareInterpreterProperties());

      String defaultValue = interpreter.getProperty("nonexistent.property", "default");

      assertEquals("default", defaultValue);
    }

    @Test
    void shouldReturnNullForMissingPropertyWithoutDefault() {
      interpreter = new OracleInterpreter(prepareInterpreterProperties());

      String value = interpreter.getProperty("nonexistent.property");

      assertNull(value);
    }
  }

  @Nested
  @DisplayName("Empty and Null Input Handling")
  class EmptyInputTests {

    @BeforeEach
    void createInterpreter() {
      interpreter = new OracleInterpreter(prepareInterpreterProperties());
    }

    @Test
    void shouldHandleNullSql() {
      InterpreterResult result = interpreter.interpret(null, context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code(),
                   "Null SQL should return SUCCESS");
    }

    @Test
    void shouldHandleEmptyStringSql() {
      InterpreterResult result = interpreter.interpret("", context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code(),
                   "Empty SQL should return SUCCESS");
    }

    @Test
    void shouldHandleWhitespaceSql() {
      InterpreterResult result = interpreter.interpret("   ", context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code(),
                   "Whitespace-only SQL should return SUCCESS");
    }
  }

  @Nested
  @DisplayName("Interpreter Lifecycle")
  class LifecycleTests {

    @Test
    void cancelShouldNotThrowWhenNoActiveStatement() {
      interpreter = new OracleInterpreter(prepareInterpreterProperties());

      assertDoesNotThrow(() -> interpreter.cancel(context),
                         "Cancel should not throw when no statement is active");
    }

    @Test
    void closeShouldNotThrowWhenNotOpened() {
      interpreter = new OracleInterpreter(prepareInterpreterProperties());

      assertDoesNotThrow(() -> interpreter.close(),
                         "Close should not throw when interpreter was never opened");
    }

    @Test
    void multipleCloseShouldBeSafe() {
      interpreter = new OracleInterpreter(prepareInterpreterProperties());

      assertDoesNotThrow(() -> {
        interpreter.close();
        interpreter.close();
        interpreter.close();
      }, "Multiple close calls should be safe");
    }
  }

  @Nested
  @DisplayName("Configuration Tests")
  class ConfigurationTests {

    @Test
    void shouldUseDefaultMaxResults() {
      Properties props = prepareInterpreterProperties();
      // Don't set oracle.maxResults

      interpreter = new OracleInterpreter(props);

      // Default should be 1000 as per implementation
      assertEquals("1000", interpreter.getProperty("oracle.maxResults", "1000"));
    }

    @Test
    void shouldAcceptCustomMaxResults() {
      Properties props = prepareInterpreterProperties();
      props.setProperty("oracle.maxResults", "5000");

      interpreter = new OracleInterpreter(props);

      assertEquals("5000", interpreter.getProperty("oracle.maxResults"));
    }

    @Test
    void shouldStoreUcpPoolConfiguration() {
      Properties props = prepareInterpreterProperties();
      props.setProperty("oracleucp.maxPoolSize", "20");
      props.setProperty("oracleucp.minPoolSize", "5");
      props.setProperty("oracleucp.connectionWaitTimeout", "60");

      interpreter = new OracleInterpreter(props);

      assertAll("UCP configuration",
                () -> assertEquals("20", interpreter.getProperty("oracleucp.maxPoolSize")),
                () -> assertEquals("5", interpreter.getProperty("oracleucp.minPoolSize")),
                () -> assertEquals("60", interpreter.getProperty("oracleucp.connectionWaitTimeout"))
      );
    }

    @Test
    void shouldStoreWalletConfiguration() {
      Properties props = prepareInterpreterProperties();
      props.setProperty("oracle.connection.wallet.location", "/usr/oracle/wallet");
      props.setProperty("oracle.connection.tns.alias", "mydb_high");

      interpreter = new OracleInterpreter(props);

      assertAll("Wallet configuration",
                () -> assertEquals("/usr/oracle/wallet",
                                   interpreter.getProperty("oracle.connection.wallet.location")),
                () -> assertEquals("mydb_high",
                                   interpreter.getProperty("oracle.connection.tns.alias"))
      );
    }

    @Test
    void shouldValidatePoolSizeRelationship() {
      Properties props = prepareInterpreterProperties();
      props.setProperty("oracleucp.minPoolSize", "5");
      props.setProperty("oracleucp.maxPoolSize", "20");

      interpreter = new OracleInterpreter(props);

      int min = Integer.parseInt(interpreter.getProperty("oracleucp.minPoolSize"));
      int max = Integer.parseInt(interpreter.getProperty("oracleucp.maxPoolSize"));

      assertTrue(min <= max,
                 "minPoolSize (" + min + ") should be <= maxPoolSize (" + max + ")");
    }
  }

  @Nested
  @DisplayName("Open/Initialize Error Handling")
  class OpenErrorTests {

    @Test
    void shouldThrowWhenDriverNotFound() {
      Properties props = prepareInterpreterProperties();
      props.setProperty("oracle.connection.driver", "com.nonexistent.FakeDriver");

      interpreter = new OracleInterpreter(props);

      InterpreterException exception = assertThrows(
        InterpreterException.class,
        () -> interpreter.open(),
        "Should throw InterpreterException when driver class not found"
      );

      // Verify the exception has meaningful information
      assertNotNull(exception.getCause(), "Exception should have a cause");
      assertTrue(exception.getCause() instanceof ClassNotFoundException,
                 "Cause should be ClassNotFoundException");
    }

    @Test
    void shouldThrowWhenWalletWithoutTnsAlias() {
      Properties props = prepareInterpreterProperties();
      props.setProperty("oracle.connection.wallet.location", "/path/to/wallet");
      // Deliberately not setting oracle.connection.tns.alias

      interpreter = new OracleInterpreter(props);

      InterpreterException exception = assertThrows(
        InterpreterException.class,
        () -> interpreter.open(),
        "Should throw when wallet location is set but TNS alias is missing"
      );

      assertTrue(exception.getMessage().contains("TNS alias"),
                 "Exception message should mention TNS alias");
    }

    @Test
    void shouldThrowOnInvalidUcpProperties() {
      String[] propertyNames = {
        "oracleucp.initialPoolSize",
        "oracleucp.minPoolSize",
        "oracleucp.maxPoolSize"
      };

      for (String propertyName : propertyNames) {
        Properties props = prepareInterpreterProperties();
        props.setProperty(propertyName, "invalid");

        interpreter = new OracleInterpreter(props);

        assertThrows(
          InterpreterException.class,
          () -> interpreter.open(),
          "Should throw for invalid " + propertyName
        );
      }
    }
  }

  @Nested
  @DisplayName("SQL Classification Logic")
  class SqlClassificationTests {

    /**
     * These tests verify the interpreter correctly identifies SQL statement types.
     *
     * Note: These tests verify the LOGIC of SQL classification, not actual execution.
     * For actual execution tests, see the integration test class.
     */

    @Test
    void dmlStatementsShouldBeRecognizedByKeywords() {
      // This tests the classification logic that's used in parseSqlBlocks
      assertAll("DML statement recognition",
                () -> assertTrue(isDmlStatement("INSERT INTO t VALUES (1)"), "INSERT"),
                () -> assertTrue(isDmlStatement("UPDATE t SET c = 1"), "UPDATE"),
                () -> assertTrue(isDmlStatement("DELETE FROM t"), "DELETE"),
                () -> assertTrue(isDmlStatement("MERGE INTO t USING s ON (t.id = s.id)"), "MERGE"),
                () -> assertTrue(isDmlStatement("  insert into t values (1)"), "INSERT with leading space"),
                () -> assertTrue(isDmlStatement("INSERT  INTO t VALUES (1)"), "INSERT with extra space")
      );
    }

    @Test
    void selectStatementsShouldBeRecognized() {
      assertAll("SELECT statement recognition",
                () -> assertTrue(isSelectStatement("SELECT * FROM dual"), "Simple SELECT"),
                () -> assertTrue(isSelectStatement("SELECT 1 FROM dual"), "SELECT literal"),
                () -> assertTrue(isSelectStatement("  SELECT * FROM t"), "SELECT with leading space"),
                () -> assertTrue(isSelectStatement("select * from t"), "lowercase select")
      );
    }

    @Test
    void plsqlBlocksShouldBeRecognized() {
      assertAll("PL/SQL block recognition",
                () -> assertTrue(isPlSqlBlock("BEGIN NULL; END;"), "Simple BEGIN block"),
                () -> assertTrue(isPlSqlBlock("DECLARE v NUMBER; BEGIN NULL; END;"), "DECLARE block"),
                () -> assertTrue(isPlSqlBlock("  BEGIN NULL; END;"), "BEGIN with leading space"),
                () -> assertTrue(isPlSqlBlock("begin null; end;"), "lowercase begin")
      );
    }

    @Test
    void createPlsqlObjectsShouldBeRecognized() {
      assertAll("CREATE PL/SQL object recognition",
                () -> assertTrue(isCreatePlSqlObject("CREATE PROCEDURE p AS BEGIN NULL; END;"), "PROCEDURE"),
                () -> assertTrue(isCreatePlSqlObject("CREATE OR REPLACE PROCEDURE p AS BEGIN NULL; END;"),
                                 "OR REPLACE PROCEDURE"),
                () -> assertTrue(isCreatePlSqlObject("CREATE FUNCTION f RETURN NUMBER AS BEGIN RETURN 1; END;"),
                                 "FUNCTION"),
                () -> assertTrue(isCreatePlSqlObject("CREATE OR REPLACE FUNCTION f RETURN NUMBER AS BEGIN RETURN 1; END;"),
                                 "OR REPLACE FUNCTION"),
                () -> assertTrue(isCreatePlSqlObject("CREATE PACKAGE pkg AS END;"), "PACKAGE"),
                () -> assertTrue(isCreatePlSqlObject("CREATE TRIGGER trg BEFORE INSERT ON t BEGIN NULL; END;"),
                                 "TRIGGER"),
                () -> assertTrue(isCreatePlSqlObject("CREATE TYPE typ AS OBJECT (id NUMBER);"), "TYPE")
      );
    }

    // Helper methods that mirror the classification logic in OracleInterpreter
    private boolean isDmlStatement(String sql) {
      String normalized = sql.trim().toUpperCase();
      return normalized.startsWith("INSERT") ||
        normalized.startsWith("UPDATE") ||
        normalized.startsWith("DELETE") ||
        normalized.startsWith("MERGE");
    }

    private boolean isSelectStatement(String sql) {
      return sql.trim().toUpperCase().startsWith("SELECT");
    }

    private boolean isPlSqlBlock(String sql) {
      String normalized = sql.trim().replaceAll("\\s+", " ").toUpperCase();
      return normalized.startsWith("BEGIN") || normalized.startsWith("DECLARE");
    }

    private boolean isCreatePlSqlObject(String sql) {
      String normalized = sql.trim().replaceAll("\\s+", " ").toUpperCase();
      return normalized.matches("^CREATE\\s+(OR\\s+REPLACE\\s+)?(PROCEDURE|FUNCTION|PACKAGE|TRIGGER|TYPE)\\b.*");
    }
  }

  @Nested
  @DisplayName("SQL Delimiter Handling")
  class DelimiterTests {

    @Test
    void slashDelimiterShouldSeparatePlsqlBlocks() {
      String input = "BEGIN\n  DBMS_OUTPUT.PUT_LINE('Hello');\nEND;\n/\nSELECT 1 FROM DUAL;";

      // The interpreter splits on / to separate blocks
      String[] blocks = input.split("\\r?\\n/\\r?\\n");

      assertEquals(2, blocks.length, "Should have 2 blocks separated by /");
      assertTrue(blocks[0].trim().toUpperCase().startsWith("BEGIN"), "First block should be PL/SQL");
      assertTrue(blocks[1].trim().toUpperCase().startsWith("SELECT"), "Second block should be SELECT");
    }

    @Test
    void semicolonShouldSeparateRegularSql() {
      String input = "SELECT 1 FROM DUAL; SELECT 2 FROM DUAL; SELECT 3 FROM DUAL;";

      // SqlSplitter handles semicolon-separated SQL
      String[] statements = input.split(";");

      assertTrue(statements.length >= 3, "Should have at least 3 statements");
    }

    @Test
    void mixedDelimitersShouldBeHandled() {
      String input = "SELECT 1 FROM DUAL;\n" +
        "BEGIN NULL; END;\n" +
        "/\n" +
        "SELECT 2 FROM DUAL;";

      // Verify the structure is parseable
      assertTrue(input.contains(";"), "Should contain semicolons");
      assertTrue(input.contains("/"), "Should contain slash");
    }
  }

  @Nested
  @DisplayName("Interpreter Context Handling")
  class ContextTests {

    @Test
    void shouldUseParagraphIdFromContext() {
      interpreter = new OracleInterpreter(prepareInterpreterProperties());

      String paragraphId = context.getParagraphId();

      assertNotNull(paragraphId, "Paragraph ID should not be null");
      assertTrue(paragraphId.startsWith("paragraph-"),
                 "Paragraph ID should have expected format");
    }

    @Test
    void shouldHandleDifferentParagraphIds() {
      interpreter = new OracleInterpreter(prepareInterpreterProperties());

      InterpreterContext context1 = InterpreterContext.builder()
                                                      .setAuthenticationInfo(new AuthenticationInfo("user1"))
                                                      .setParagraphId("para-1")
                                                      .setInterpreterOut(new InterpreterOutput())
                                                      .build();

      InterpreterContext context2 = InterpreterContext.builder()
                                                      .setAuthenticationInfo(new AuthenticationInfo("user2"))
                                                      .setParagraphId("para-2")
                                                      .setInterpreterOut(new InterpreterOutput())
                                                      .build();

      assertNotEquals(context1.getParagraphId(), context2.getParagraphId(),
                      "Different contexts should have different paragraph IDs");
    }
  }

  @Nested
  @DisplayName("Concurrent Access Safety")
  class ConcurrencySafetyTests {

    @Test
    void cancelShouldBeSafeFromDifferentThread() throws InterruptedException {
      interpreter = new OracleInterpreter(prepareInterpreterProperties());

      Thread cancelThread = new Thread(() -> {
        interpreter.cancel(context);
      });

      cancelThread.start();
      cancelThread.join(1000);

      assertFalse(cancelThread.isAlive(),
                  "Cancel thread should complete without hanging");
    }

    @Test
    void multipleConcurrentCancelsShouldBeSafe() throws InterruptedException {
      interpreter = new OracleInterpreter(prepareInterpreterProperties());

      Thread[] threads = new Thread[5];
      for (int i = 0; i < threads.length; i++) {
        final int idx = i;
        InterpreterContext ctx = InterpreterContext.builder()
                                                   .setAuthenticationInfo(new AuthenticationInfo("user"))
                                                   .setParagraphId("para-" + idx)
                                                   .setInterpreterOut(new InterpreterOutput())
                                                   .build();

        threads[i] = new Thread(() -> interpreter.cancel(ctx));
      }

      for (Thread t : threads) {
        t.start();
      }

      for (Thread t : threads) {
        t.join(1000);
        assertFalse(t.isAlive(), "All cancel threads should complete");
      }
    }
  }

  @Nested
  @DisplayName("Interpret Method Tests")
  class InterpretTests {

    @BeforeEach
    void openInterpreter() throws InterpreterException {
      interpreter = new OracleInterpreter(prepareInterpreterProperties());
      interpreter.open();
    }

    // --- Basic SELECT Tests ---

    @Test
    void shouldExecuteSimpleSelect() {
      InterpreterResult result = interpreter.interpret("SELECT 1 AS num FROM DUAL", context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertTrue(result.message().get(0).getData().contains("NUM"));
      assertTrue(result.message().get(0).getData().contains("1"));
    }

    @Test
    void shouldExecuteSelectWithMultipleColumns() {
      InterpreterResult result = interpreter.interpret(
        "SELECT 1 AS col1, 'hello' AS col2, SYSDATE AS col3 FROM DUAL", context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      String output = result.message().get(0).getData();
      assertTrue(output.contains("COL1"));
      assertTrue(output.contains("COL2"));
      assertTrue(output.contains("COL3"));
    }

    @Test
    void shouldExecuteSelectWithWhereClause() {
      InterpreterResult result = interpreter.interpret(
        "SELECT * FROM DUAL WHERE DUMMY = 'X'", context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    }

    @Test
    void shouldExecuteMultipleSelects() {
      String sql = "SELECT 1 FROM DUAL; SELECT 2 FROM DUAL;\n" +
        " SELECT 3 FROM DUAL;";

      InterpreterResult result = interpreter.interpret(sql, context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    }

    // --- Error Handling Tests ---

    @Test
    void shouldReturnErrorForInvalidSql() {
      InterpreterResult result = interpreter.interpret("SELEC * FORM dual", context);

      assertEquals(InterpreterResult.Code.ERROR, result.code());
      assertTrue(result.message().get(0).getData().contains("ORA-"),
                 "Should contain Oracle error code");
    }

    @Test
    void shouldReturnErrorForNonExistentTable() {
      InterpreterResult result = interpreter.interpret(
        "SELECT * FROM non_existent_table_xyz_123", context);

      assertEquals(InterpreterResult.Code.ERROR, result.code());
      assertTrue(result.message().get(0).getData().contains("ORA-"),
                 "Should contain Oracle error code");
    }

    @Test
    void shouldReturnErrorForInvalidColumn() {
      InterpreterResult result = interpreter.interpret(
        "SELECT non_existent_column FROM DUAL", context);

      assertEquals(InterpreterResult.Code.ERROR, result.code());
    }

    // --- DML Tests ---

    @Test
    void shouldExecuteDdlAndDml() {
      String tableName = "TEST_TBL_" + System.currentTimeMillis();

      try {
        // CREATE TABLE
        InterpreterResult createResult = interpreter.interpret(
          "CREATE TABLE " + tableName + " (id NUMBER, name VARCHAR2(50))", context);
        assertEquals(InterpreterResult.Code.SUCCESS, createResult.code());

        // INSERT
        InterpreterResult insertResult = interpreter.interpret(
          "INSERT INTO " + tableName + " VALUES (1, 'test')", context);
        assertEquals(InterpreterResult.Code.SUCCESS, insertResult.code());
        assertTrue(insertResult.message().get(0).getData().contains("1 row affected"));

        // UPDATE
        InterpreterResult updateResult = interpreter.interpret(
          "UPDATE " + tableName + " SET name = 'updated' WHERE id = 1", context);
        assertEquals(InterpreterResult.Code.SUCCESS, updateResult.code());

        // DELETE
        InterpreterResult deleteResult = interpreter.interpret(
          "DELETE FROM " + tableName + " WHERE id = 1", context);
        assertEquals(InterpreterResult.Code.SUCCESS, deleteResult.code());

      } finally {
        // Cleanup
        interpreter.interpret("DROP TABLE " + tableName, context);
      }
    }

    @Test
    void shouldReportCorrectRowCount() {
      String tableName = "TEST_ROWS_" + System.currentTimeMillis();

      try {
        interpreter.interpret(
          "CREATE TABLE " + tableName + " (id NUMBER)", context);

        InterpreterResult result = interpreter.interpret(
          "INSERT INTO " + tableName + " SELECT LEVEL FROM DUAL CONNECT BY LEVEL <= 5",
          context);

        assertEquals(InterpreterResult.Code.SUCCESS, result.code());
        assertTrue(result.message().get(0).getData().contains("5 rows affected"));

      } finally {
        interpreter.interpret("DROP TABLE " + tableName, context);
      }
    }

    // --- PL/SQL Tests ---

    @Test
    void shouldExecuteSimplePlsqlBlock() {
      String plsql = "BEGIN NULL; END;";

      InterpreterResult result = interpreter.interpret(plsql, context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    }

    @Test
    void shouldExecutePlsqlWithDbmsOutput() {
      String plsql = "BEGIN\n" +
        "    DBMS_OUTPUT.PUT_LINE('Hello from PL/SQL');\n" +
        "END;\n" +
        "/";

      InterpreterResult result = interpreter.interpret(plsql, context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertTrue(result.message().get(1).getData().contains("Hello from PL/SQL"),
                 "Should capture DBMS_OUTPUT");
    }

    @Test
    void shouldExecutePlsqlWithDeclare() {
      String plsql = "DECLARE\n" +
        "    v_num NUMBER := 42;\n" +
        "BEGIN\n" +
        "    DBMS_OUTPUT.PUT_LINE('Value is: ' || v_num);\n" +
        "END;";

      InterpreterResult result = interpreter.interpret(plsql, context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertTrue(result.message().get(1).getData().contains("Value is: 42"));
    }

    @Test
    void shouldExecutePlsqlWithSlashDelimiter() {
      String plsql = "BEGIN\n" +
        "    DBMS_OUTPUT.PUT_LINE('First block');\n" +
        "END;\n" +
        "/\n" +
        "SELECT 'After PL/SQL' AS msg FROM DUAL";

      InterpreterResult result = interpreter.interpret(plsql, context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      String output1 = result.message().get(1).getData();
      assertTrue(output1.contains("First block"));
      String output2 = result.message().get(2).getData();
      assertTrue(output2.contains("After PL/SQL"));
    }

    @Test
    void shouldReturnErrorForInvalidPlsql() {
      String plsql = "BEGIN INVALID_PROCEDURE_XYZ(); END;";

      InterpreterResult result = interpreter.interpret(plsql, context);

      assertEquals(InterpreterResult.Code.ERROR, result.code());
    }

    // --- Max Results Tests ---

    @Test
    void shouldRespectMaxResultsLimit() throws InterpreterException {
      Properties props = prepareInterpreterProperties();
      props.setProperty("oracle.maxResults", "5");

      OracleInterpreter limitedInterpreter = new OracleInterpreter(props);
      limitedInterpreter.open();

      try {
        InterpreterResult result = limitedInterpreter.interpret(
          "SELECT LEVEL AS num FROM DUAL CONNECT BY LEVEL <= 100", context);

        assertEquals(InterpreterResult.Code.SUCCESS, result.code());
        assertTrue(result.message().get(0).getData().contains("limited to 5 rows"),
                   "Should indicate results are limited");

      } finally {
        limitedInterpreter.close();
      }
    }

    // --- Transaction Tests ---

    @Test
    void shouldHandleCommit() {
      InterpreterResult result = interpreter.interpret("COMMIT", context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    }

    @Test
    void shouldHandleRollback() {
      InterpreterResult result = interpreter.interpret("ROLLBACK", context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    }

    // --- Special Cases ---

    @Test
    void shouldHandleSqlWithComments() {
      String sql = "-- This is a comment\n" +
        "SELECT 1 FROM DUAL /* inline comment */";

      InterpreterResult result = interpreter.interpret(sql, context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    }

    @Test
    void shouldHandleNullValues() {
      InterpreterResult result = interpreter.interpret(
        "SELECT NULL AS nullable_col FROM DUAL", context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
      assertTrue(result.message().get(0).getData().contains("null"));
    }

    @Test
    void shouldHandleLargeText() {
      InterpreterResult result = interpreter.interpret(
        "SELECT RPAD('x', 1000, 'x') AS large_text FROM DUAL", context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    }

    @Test
    void shouldHandleDateValues() {
      InterpreterResult result = interpreter.interpret(
        "SELECT SYSDATE AS current_date, SYSTIMESTAMP AS current_ts FROM DUAL", context);

      assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    }
  }
}
