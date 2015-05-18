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
package org.apache.zeppelin.tajo;


import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * This is borrowed from Apache Commons DBCP2.
 *
 * A dummy {@link java.sql.Connection}, for testing purposes.
 */
public class TesterConnection implements Connection {
  protected boolean _open = true;
  protected boolean _autoCommit = true;
  protected int _transactionIsolation = 1;
  protected DatabaseMetaData _metaData = new TesterDatabaseMetaData();
  protected String _catalog = null;
  protected Map<String,Class<?>> _typeMap = null;
  protected boolean _readOnly = false;
  protected SQLWarning warnings = null;
  protected String username = null;
  protected Exception failure;

  public String getUsername() {
    return this.username;
  }

  public void setWarnings(SQLWarning warning) {
    this.warnings = warning;
  }

  @Override
  public void clearWarnings() throws SQLException {
    checkOpen();
    warnings = null;
  }

  @Override
  public void close() throws SQLException {
    checkFailure();
    _open = false;
  }

  @Override
  public void commit() throws SQLException {
    checkOpen();
    if (isReadOnly()) {
      throw new SQLException("Cannot commit a readonly connection");
    }
  }

  @Override
  public Statement createStatement() throws SQLException {
    checkOpen();
    return new TesterStatement(this);
  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
    checkOpen();
    return new TesterStatement(this);
  }

  @Override
  public boolean getAutoCommit() throws SQLException {
    checkOpen();
    return _autoCommit;
  }

  @Override
  public String getCatalog() throws SQLException {
    checkOpen();
    return _catalog;
  }

  @Override
  public DatabaseMetaData getMetaData() throws SQLException {
    checkOpen();
    return _metaData;
  }

  @Override
  public int getTransactionIsolation() throws SQLException {
    checkOpen();
    return _transactionIsolation;
  }

  @Override
  public Map<String,Class<?>> getTypeMap() throws SQLException {
    checkOpen();
    return _typeMap;
  }

  @Override
  public SQLWarning getWarnings() throws SQLException {
    checkOpen();
    return warnings;
  }

  @Override
  public boolean isClosed() throws SQLException {
    checkFailure();
    return !_open;
  }

  @Override
  public boolean isReadOnly() throws SQLException {
    checkOpen();
    return _readOnly;
  }

  @Override
  public String nativeSQL(String sql) throws SQLException {
    checkOpen();
    return sql;
  }

  @Override
  public CallableStatement prepareCall(String sql) throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public PreparedStatement prepareStatement(String sql) throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public void rollback() throws SQLException {
    checkOpen();
    if (isReadOnly()) {
      throw new SQLException("Cannot rollback a readonly connection");
    }
  }

  @Override
  public void setAutoCommit(boolean autoCommit) throws SQLException {
    checkOpen();
    _autoCommit = autoCommit;
  }

  @Override
  public void setCatalog(String catalog) throws SQLException {
    checkOpen();
    _catalog = catalog;
  }

  @Override
  public void setReadOnly(boolean readOnly) throws SQLException {
    checkOpen();
    _readOnly = readOnly;
  }

  @Override
  public void setTransactionIsolation(int level) throws SQLException {
    checkOpen();
    _transactionIsolation = level;
  }

  @Override
  public void setTypeMap(Map<String,Class<?>> map) throws SQLException {
    checkOpen();
    _typeMap = map;
  }

  protected void checkOpen() throws SQLException {
    if(!_open) {
      throw new SQLException("Connection is closed.");
    }
    checkFailure();
  }

  protected void checkFailure() throws SQLException {
    if (failure != null) {
      if(failure instanceof SQLException) {
        throw (SQLException)failure;
      } else {
        throw new SQLException("TesterConnection failure", failure);
      }
    }
  }

  public void setFailure(Exception failure) {
    this.failure = failure;
  }

  @Override
  public int getHoldability() throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public void setHoldability(int holdability) throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public java.sql.Savepoint setSavepoint() throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public java.sql.Savepoint setSavepoint(String name) throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public void rollback(java.sql.Savepoint savepoint) throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public void releaseSavepoint(java.sql.Savepoint savepoint) throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public Statement createStatement(int resultSetType,
                                   int resultSetConcurrency,
                                   int resultSetHoldability)
    throws SQLException {
    return createStatement();
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType,
                                            int resultSetConcurrency,
                                            int resultSetHoldability)
    throws SQLException {
    return prepareStatement(sql);
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType,
                                       int resultSetConcurrency,
                                       int resultSetHoldability)
    throws SQLException {
    return prepareCall(sql);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
    throws SQLException {
    return prepareStatement(sql);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int columnIndexes[])
    throws SQLException {
    return prepareStatement(sql);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, String columnNames[])
    throws SQLException {
    return prepareStatement(sql);
  }


  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public Blob createBlob() throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public Clob createClob() throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public NClob createNClob() throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public SQLXML createSQLXML() throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public boolean isValid(int timeout) throws SQLException {
    return _open;
  }

  @Override
  public void setClientInfo(String name, String value) throws SQLClientInfoException {
    throw new SQLClientInfoException();
  }

  @Override
  public void setClientInfo(Properties properties) throws SQLClientInfoException {
    throw new SQLClientInfoException();
  }

  @Override
  public Properties getClientInfo() throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public String getClientInfo(String name) throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public void setSchema(String schema) throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public String getSchema() throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public void abort(Executor executor) throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public void setNetworkTimeout(Executor executor, int milliseconds)
    throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }

  @Override
  public int getNetworkTimeout() throws SQLException {
    throw new SQLFeatureNotSupportedException("Not supported.");
  }
}
