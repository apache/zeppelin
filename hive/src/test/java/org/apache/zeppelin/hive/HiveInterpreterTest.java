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

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Hive interpreter unit tests
 */
public class HiveInterpreterTest {
  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void test() {
    HiveInterpreter t = new MockHiveInterpreter(new Properties());
    t.open();

    //simple select test
    InterpreterResult result = t.interpret("select * from t", null);
    assertEquals(result.type(), InterpreterResult.Type.TABLE);

    //explain test
    result = t.interpret("explain select * from t", null);
    assertEquals(result.type(), InterpreterResult.Type.TEXT);
    t.close();
  }
}

class MockHiveInterpreter extends HiveInterpreter {

  public MockHiveInterpreter(Properties property) {
    super(property);
  }

  @Override
  public Connection getJdbcConnection()
    throws SQLException {
    return new MockConnection();
  }
}

class MockResultSetMetadata implements ResultSetMetaData {

  @Override
  public int getColumnCount() throws SQLException {
    return 0;
  }

  @Override
  public boolean isAutoIncrement(int column) throws SQLException {
    return false;
  }

  @Override
  public boolean isCaseSensitive(int column) throws SQLException {
    return false;
  }

  @Override
  public boolean isSearchable(int column) throws SQLException {
    return false;
  }

  @Override
  public boolean isCurrency(int column) throws SQLException {
    return false;
  }

  @Override
  public int isNullable(int column) throws SQLException {
    return 0;
  }

  @Override
  public boolean isSigned(int column) throws SQLException {
    return false;
  }

  @Override
  public int getColumnDisplaySize(int column) throws SQLException {
    return 0;
  }

  @Override
  public String getColumnLabel(int column) throws SQLException {
    return null;
  }

  @Override
  public String getColumnName(int column) throws SQLException {
    return null;
  }

  @Override
  public String getSchemaName(int column) throws SQLException {
    return null;
  }

  @Override
  public int getPrecision(int column) throws SQLException {
    return 0;
  }

  @Override
  public int getScale(int column) throws SQLException {
    return 0;
  }

  @Override
  public String getTableName(int column) throws SQLException {
    return null;
  }

  @Override
  public String getCatalogName(int column) throws SQLException {
    return null;
  }

  @Override
  public int getColumnType(int column) throws SQLException {
    return 0;
  }

  @Override
  public String getColumnTypeName(int column) throws SQLException {
    return null;
  }

  @Override
  public boolean isReadOnly(int column) throws SQLException {
    return false;
  }

  @Override
  public boolean isWritable(int column) throws SQLException {
    return false;
  }

  @Override
  public boolean isDefinitelyWritable(int column) throws SQLException {
    return false;
  }

  @Override
  public String getColumnClassName(int column) throws SQLException {
    return null;
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    return null;
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return false;
  }
}
class MockResultSet implements ResultSet {

  @Override
  public boolean next() throws SQLException {
    return false;
  }

  @Override
  public void close() throws SQLException {

  }

  @Override
  public boolean wasNull() throws SQLException {
    return false;
  }

  @Override
  public String getString(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public boolean getBoolean(int columnIndex) throws SQLException {
    return false;
  }

  @Override
  public byte getByte(int columnIndex) throws SQLException {
    return 0;
  }

  @Override
  public short getShort(int columnIndex) throws SQLException {
    return 0;
  }

  @Override
  public int getInt(int columnIndex) throws SQLException {
    return 0;
  }

  @Override
  public long getLong(int columnIndex) throws SQLException {
    return 0;
  }

  @Override
  public float getFloat(int columnIndex) throws SQLException {
    return 0;
  }

  @Override
  public double getDouble(int columnIndex) throws SQLException {
    return 0;
  }

  @Override
  public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
    return null;
  }

  @Override
  public byte[] getBytes(int columnIndex) throws SQLException {
    return new byte[0];
  }

  @Override
  public Date getDate(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public Time getTime(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public Timestamp getTimestamp(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public InputStream getAsciiStream(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public InputStream getUnicodeStream(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public InputStream getBinaryStream(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public String getString(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public boolean getBoolean(String columnLabel) throws SQLException {
    return false;
  }

  @Override
  public byte getByte(String columnLabel) throws SQLException {
    return 0;
  }

  @Override
  public short getShort(String columnLabel) throws SQLException {
    return 0;
  }

  @Override
  public int getInt(String columnLabel) throws SQLException {
    return 0;
  }

  @Override
  public long getLong(String columnLabel) throws SQLException {
    return 0;
  }

  @Override
  public float getFloat(String columnLabel) throws SQLException {
    return 0;
  }

  @Override
  public double getDouble(String columnLabel) throws SQLException {
    return 0;
  }

  @Override
  public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
    return null;
  }

  @Override
  public byte[] getBytes(String columnLabel) throws SQLException {
    return new byte[0];
  }

  @Override
  public Date getDate(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public Time getTime(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public Timestamp getTimestamp(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public InputStream getAsciiStream(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public InputStream getUnicodeStream(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public InputStream getBinaryStream(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public SQLWarning getWarnings() throws SQLException {
    return null;
  }

  @Override
  public void clearWarnings() throws SQLException {

  }

  @Override
  public String getCursorName() throws SQLException {
    return null;
  }

  @Override
  public ResultSetMetaData getMetaData() throws SQLException {
    return new MockResultSetMetadata();
  }

  @Override
  public Object getObject(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public Object getObject(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public int findColumn(String columnLabel) throws SQLException {
    return 0;
  }

  @Override
  public Reader getCharacterStream(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public Reader getCharacterStream(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public boolean isBeforeFirst() throws SQLException {
    return false;
  }

  @Override
  public boolean isAfterLast() throws SQLException {
    return false;
  }

  @Override
  public boolean isFirst() throws SQLException {
    return false;
  }

  @Override
  public boolean isLast() throws SQLException {
    return false;
  }

  @Override
  public void beforeFirst() throws SQLException {

  }

  @Override
  public void afterLast() throws SQLException {

  }

  @Override
  public boolean first() throws SQLException {
    return false;
  }

  @Override
  public boolean last() throws SQLException {
    return false;
  }

  @Override
  public int getRow() throws SQLException {
    return 0;
  }

  @Override
  public boolean absolute(int row) throws SQLException {
    return false;
  }

  @Override
  public boolean relative(int rows) throws SQLException {
    return false;
  }

  @Override
  public boolean previous() throws SQLException {
    return false;
  }

  @Override
  public void setFetchDirection(int direction) throws SQLException {

  }

  @Override
  public int getFetchDirection() throws SQLException {
    return 0;
  }

  @Override
  public void setFetchSize(int rows) throws SQLException {

  }

  @Override
  public int getFetchSize() throws SQLException {
    return 0;
  }

  @Override
  public int getType() throws SQLException {
    return 0;
  }

  @Override
  public int getConcurrency() throws SQLException {
    return 0;
  }

  @Override
  public boolean rowUpdated() throws SQLException {
    return false;
  }

  @Override
  public boolean rowInserted() throws SQLException {
    return false;
  }

  @Override
  public boolean rowDeleted() throws SQLException {
    return false;
  }

  @Override
  public void updateNull(int columnIndex) throws SQLException {

  }

  @Override
  public void updateBoolean(int columnIndex, boolean x) throws SQLException {

  }

  @Override
  public void updateByte(int columnIndex, byte x) throws SQLException {

  }

  @Override
  public void updateShort(int columnIndex, short x) throws SQLException {

  }

  @Override
  public void updateInt(int columnIndex, int x) throws SQLException {

  }

  @Override
  public void updateLong(int columnIndex, long x) throws SQLException {

  }

  @Override
  public void updateFloat(int columnIndex, float x) throws SQLException {

  }

  @Override
  public void updateDouble(int columnIndex, double x) throws SQLException {

  }

  @Override
  public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {

  }

  @Override
  public void updateString(int columnIndex, String x) throws SQLException {

  }

  @Override
  public void updateBytes(int columnIndex, byte[] x) throws SQLException {

  }

  @Override
  public void updateDate(int columnIndex, Date x) throws SQLException {

  }

  @Override
  public void updateTime(int columnIndex, Time x) throws SQLException {

  }

  @Override
  public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {

  }

  @Override
  public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {

  }

  @Override
  public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {

  }

  @Override
  public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {

  }

  @Override
  public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {

  }

  @Override
  public void updateObject(int columnIndex, Object x) throws SQLException {

  }

  @Override
  public void updateNull(String columnLabel) throws SQLException {

  }

  @Override
  public void updateBoolean(String columnLabel, boolean x) throws SQLException {

  }

  @Override
  public void updateByte(String columnLabel, byte x) throws SQLException {

  }

  @Override
  public void updateShort(String columnLabel, short x) throws SQLException {

  }

  @Override
  public void updateInt(String columnLabel, int x) throws SQLException {

  }

  @Override
  public void updateLong(String columnLabel, long x) throws SQLException {

  }

  @Override
  public void updateFloat(String columnLabel, float x) throws SQLException {

  }

  @Override
  public void updateDouble(String columnLabel, double x) throws SQLException {

  }

  @Override
  public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {

  }

  @Override
  public void updateString(String columnLabel, String x) throws SQLException {

  }

  @Override
  public void updateBytes(String columnLabel, byte[] x) throws SQLException {

  }

  @Override
  public void updateDate(String columnLabel, Date x) throws SQLException {

  }

  @Override
  public void updateTime(String columnLabel, Time x) throws SQLException {

  }

  @Override
  public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {

  }

  @Override
  public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {

  }

  @Override
  public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException {

  }

  @Override
  public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException {

  }

  @Override
  public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {

  }

  @Override
  public void updateObject(String columnLabel, Object x) throws SQLException {

  }

  @Override
  public void insertRow() throws SQLException {

  }

  @Override
  public void updateRow() throws SQLException {

  }

  @Override
  public void deleteRow() throws SQLException {

  }

  @Override
  public void refreshRow() throws SQLException {

  }

  @Override
  public void cancelRowUpdates() throws SQLException {

  }

  @Override
  public void moveToInsertRow() throws SQLException {

  }

  @Override
  public void moveToCurrentRow() throws SQLException {

  }

  @Override
  public Statement getStatement() throws SQLException {
    return new MockStatement();
  }

  @Override
  public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
    return null;
  }

  @Override
  public Ref getRef(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public Blob getBlob(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public Clob getClob(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public Array getArray(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
    return null;
  }

  @Override
  public Ref getRef(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public Blob getBlob(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public Clob getClob(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public Array getArray(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public Date getDate(int columnIndex, Calendar cal) throws SQLException {
    return null;
  }

  @Override
  public Date getDate(String columnLabel, Calendar cal) throws SQLException {
    return null;
  }

  @Override
  public Time getTime(int columnIndex, Calendar cal) throws SQLException {
    return null;
  }

  @Override
  public Time getTime(String columnLabel, Calendar cal) throws SQLException {
    return null;
  }

  @Override
  public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
    return null;
  }

  @Override
  public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
    return null;
  }

  @Override
  public URL getURL(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public URL getURL(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public void updateRef(int columnIndex, Ref x) throws SQLException {

  }

  @Override
  public void updateRef(String columnLabel, Ref x) throws SQLException {

  }

  @Override
  public void updateBlob(int columnIndex, Blob x) throws SQLException {

  }

  @Override
  public void updateBlob(String columnLabel, Blob x) throws SQLException {

  }

  @Override
  public void updateClob(int columnIndex, Clob x) throws SQLException {

  }

  @Override
  public void updateClob(String columnLabel, Clob x) throws SQLException {

  }

  @Override
  public void updateArray(int columnIndex, Array x) throws SQLException {

  }

  @Override
  public void updateArray(String columnLabel, Array x) throws SQLException {

  }

  @Override
  public RowId getRowId(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public RowId getRowId(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public void updateRowId(int columnIndex, RowId x) throws SQLException {

  }

  @Override
  public void updateRowId(String columnLabel, RowId x) throws SQLException {

  }

  @Override
  public int getHoldability() throws SQLException {
    return 0;
  }

  @Override
  public boolean isClosed() throws SQLException {
    return false;
  }

  @Override
  public void updateNString(int columnIndex, String nString) throws SQLException {

  }

  @Override
  public void updateNString(String columnLabel, String nString) throws SQLException {

  }

  @Override
  public void updateNClob(int columnIndex, NClob nClob) throws SQLException {

  }

  @Override
  public void updateNClob(String columnLabel, NClob nClob) throws SQLException {

  }

  @Override
  public NClob getNClob(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public NClob getNClob(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public SQLXML getSQLXML(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public SQLXML getSQLXML(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {

  }

  @Override
  public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {

  }

  @Override
  public String getNString(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public String getNString(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public Reader getNCharacterStream(int columnIndex) throws SQLException {
    return null;
  }

  @Override
  public Reader getNCharacterStream(String columnLabel) throws SQLException {
    return null;
  }

  @Override
  public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {

  }

  @Override
  public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {

  }

  @Override
  public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {

  }

  @Override
  public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {

  }

  @Override
  public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {

  }

  @Override
  public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException {

  }

  @Override
  public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException {

  }

  @Override
  public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {

  }

  @Override
  public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {

  }

  @Override
  public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {

  }

  @Override
  public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {

  }

  @Override
  public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {

  }

  @Override
  public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {

  }

  @Override
  public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {

  }

  @Override
  public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {

  }

  @Override
  public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {

  }

  @Override
  public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {

  }

  @Override
  public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {

  }

  @Override
  public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {

  }

  @Override
  public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {

  }

  @Override
  public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {

  }

  @Override
  public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {

  }

  @Override
  public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {

  }

  @Override
  public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {

  }

  @Override
  public void updateClob(int columnIndex, Reader reader) throws SQLException {

  }

  @Override
  public void updateClob(String columnLabel, Reader reader) throws SQLException {

  }

  @Override
  public void updateNClob(int columnIndex, Reader reader) throws SQLException {

  }

  @Override
  public void updateNClob(String columnLabel, Reader reader) throws SQLException {

  }

  @Override
  public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
    return null;
  }

  @Override
  public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
    return null;
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    return null;
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return false;
  }
}
class MockStatement implements Statement {

  @Override
  public ResultSet executeQuery(String sql) throws SQLException {
    return new MockResultSet();
  }

  @Override
  public int executeUpdate(String sql) throws SQLException {
    return 0;
  }

  @Override
  public void close() throws SQLException {

  }

  @Override
  public int getMaxFieldSize() throws SQLException {
    return 0;
  }

  @Override
  public void setMaxFieldSize(int max) throws SQLException {

  }

  @Override
  public int getMaxRows() throws SQLException {
    return 0;
  }

  @Override
  public void setMaxRows(int max) throws SQLException {

  }

  @Override
  public void setEscapeProcessing(boolean enable) throws SQLException {

  }

  @Override
  public int getQueryTimeout() throws SQLException {
    return 0;
  }

  @Override
  public void setQueryTimeout(int seconds) throws SQLException {

  }

  @Override
  public void cancel() throws SQLException {

  }

  @Override
  public SQLWarning getWarnings() throws SQLException {
    return null;
  }

  @Override
  public void clearWarnings() throws SQLException {

  }

  @Override
  public void setCursorName(String name) throws SQLException {

  }

  @Override
  public boolean execute(String sql) throws SQLException {
    return false;
  }

  @Override
  public ResultSet getResultSet() throws SQLException {
    return new MockResultSet();
  }

  @Override
  public int getUpdateCount() throws SQLException {
    return 0;
  }

  @Override
  public boolean getMoreResults() throws SQLException {
    return false;
  }

  @Override
  public void setFetchDirection(int direction) throws SQLException {

  }

  @Override
  public int getFetchDirection() throws SQLException {
    return 0;
  }

  @Override
  public void setFetchSize(int rows) throws SQLException {

  }

  @Override
  public int getFetchSize() throws SQLException {
    return 0;
  }

  @Override
  public int getResultSetConcurrency() throws SQLException {
    return 0;
  }

  @Override
  public int getResultSetType() throws SQLException {
    return 0;
  }

  @Override
  public void addBatch(String sql) throws SQLException {

  }

  @Override
  public void clearBatch() throws SQLException {

  }

  @Override
  public int[] executeBatch() throws SQLException {
    return new int[0];
  }

  @Override
  public Connection getConnection() throws SQLException {
    return null;
  }

  @Override
  public boolean getMoreResults(int current) throws SQLException {
    return false;
  }

  @Override
  public ResultSet getGeneratedKeys() throws SQLException {
    return null;
  }

  @Override
  public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
    return 0;
  }

  @Override
  public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
    return 0;
  }

  @Override
  public int executeUpdate(String sql, String[] columnNames) throws SQLException {
    return 0;
  }

  @Override
  public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
    return false;
  }

  @Override
  public boolean execute(String sql, int[] columnIndexes) throws SQLException {
    return false;
  }

  @Override
  public boolean execute(String sql, String[] columnNames) throws SQLException {
    return false;
  }

  @Override
  public int getResultSetHoldability() throws SQLException {
    return 0;
  }

  @Override
  public boolean isClosed() throws SQLException {
    return false;
  }

  @Override
  public void setPoolable(boolean poolable) throws SQLException {

  }

  @Override
  public boolean isPoolable() throws SQLException {
    return false;
  }

  @Override
  public void closeOnCompletion() throws SQLException {

  }

  @Override
  public boolean isCloseOnCompletion() throws SQLException {
    return false;
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    return null;
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return false;
  }
}
class MockConnection implements Connection {

  @Override
  public Statement createStatement() throws SQLException {
    return new MockStatement();
  }

  @Override
  public PreparedStatement prepareStatement(String sql) throws SQLException {
    return null;
  }

  @Override
  public CallableStatement prepareCall(String sql) throws SQLException {
    return null;
  }

  @Override
  public String nativeSQL(String sql) throws SQLException {
    return null;
  }

  @Override
  public void setAutoCommit(boolean autoCommit) throws SQLException {

  }

  @Override
  public boolean getAutoCommit() throws SQLException {
    return false;
  }

  @Override
  public void commit() throws SQLException {

  }

  @Override
  public void rollback() throws SQLException {

  }

  @Override
  public void close() throws SQLException {

  }

  @Override
  public boolean isClosed() throws SQLException {
    return false;
  }

  @Override
  public DatabaseMetaData getMetaData() throws SQLException {
    return null;
  }

  @Override
  public void setReadOnly(boolean readOnly) throws SQLException {

  }

  @Override
  public boolean isReadOnly() throws SQLException {
    return false;
  }

  @Override
  public void setCatalog(String catalog) throws SQLException {

  }

  @Override
  public String getCatalog() throws SQLException {
    return null;
  }

  @Override
  public void setTransactionIsolation(int level) throws SQLException {

  }

  @Override
  public int getTransactionIsolation() throws SQLException {
    return 0;
  }

  @Override
  public SQLWarning getWarnings() throws SQLException {
    return null;
  }

  @Override
  public void clearWarnings() throws SQLException {

  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
    return null;
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
    return null;
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
    return null;
  }

  @Override
  public Map<String, Class<?>> getTypeMap() throws SQLException {
    return null;
  }

  @Override
  public void setTypeMap(Map<String, Class<?>> map) throws SQLException {

  }

  @Override
  public void setHoldability(int holdability) throws SQLException {

  }

  @Override
  public int getHoldability() throws SQLException {
    return 0;
  }

  @Override
  public Savepoint setSavepoint() throws SQLException {
    return null;
  }

  @Override
  public Savepoint setSavepoint(String name) throws SQLException {
    return null;
  }

  @Override
  public void rollback(Savepoint savepoint) throws SQLException {

  }

  @Override
  public void releaseSavepoint(Savepoint savepoint) throws SQLException {

  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
    return null;
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
    return null;
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
    return null;
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
    return null;
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
    return null;
  }

  @Override
  public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
    return null;
  }

  @Override
  public Clob createClob() throws SQLException {
    return null;
  }

  @Override
  public Blob createBlob() throws SQLException {
    return null;
  }

  @Override
  public NClob createNClob() throws SQLException {
    return null;
  }

  @Override
  public SQLXML createSQLXML() throws SQLException {
    return null;
  }

  @Override
  public boolean isValid(int timeout) throws SQLException {
    return false;
  }

  @Override
  public void setClientInfo(String name, String value) throws SQLClientInfoException {

  }

  @Override
  public void setClientInfo(Properties properties) throws SQLClientInfoException {

  }

  @Override
  public String getClientInfo(String name) throws SQLException {
    return null;
  }

  @Override
  public Properties getClientInfo() throws SQLException {
    return null;
  }

  @Override
  public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
    return null;
  }

  @Override
  public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
    return null;
  }

  @Override
  public void setSchema(String schema) throws SQLException {

  }

  @Override
  public String getSchema() throws SQLException {
    return null;
  }

  @Override
  public void abort(Executor executor) throws SQLException {

  }

  @Override
  public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {

  }

  @Override
  public int getNetworkTimeout() throws SQLException {
    return 0;
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    return null;
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return false;
  }
}
