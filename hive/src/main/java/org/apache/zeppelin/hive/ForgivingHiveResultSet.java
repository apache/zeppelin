package org.apache.zeppelin.hive;

import org.apache.hive.jdbc.HiveQueryResultSet;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;
/**
 * Overwrites the not supported Hive methods with ones that say 
 * that a column doesn't have a given feature.
 * This allows hive to work properly with cached result sets.
 * 
 * @author Rusty Phillips {@literal: <rusty@cloudspace.com>}
 *
 */
public class ForgivingHiveResultSet implements ResultSet {
  ResultSet inner_results;
  
  public int hashCode() {
    return inner_results.hashCode();
  }

  public boolean absolute(int row) throws SQLException {
    return inner_results.absolute(row);
  }

  public void afterLast() throws SQLException {
    inner_results.afterLast();
  }

  public void cancelRowUpdates() throws SQLException {
    inner_results.cancelRowUpdates();
  }

  public void deleteRow() throws SQLException {
    inner_results.deleteRow();
  }

  public int findColumn(String columnName) throws SQLException {
    return inner_results.findColumn(columnName);
  }

  public boolean first() throws SQLException {
    return inner_results.first();
  }

  public Array getArray(int i) throws SQLException {
    return inner_results.getArray(i);
  }

  public Array getArray(String colName) throws SQLException {
    return inner_results.getArray(colName);
  }

  public InputStream getAsciiStream(int columnIndex) throws SQLException {
    return inner_results.getAsciiStream(columnIndex);
  }

  public InputStream getAsciiStream(String columnName) throws SQLException {
    return inner_results.getAsciiStream(columnName);
  }

  public boolean equals(Object obj) {
    return inner_results.equals(obj);
  }

  public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
    return inner_results.getBigDecimal(columnIndex);
  }

  public BigDecimal getBigDecimal(String columnName) throws SQLException {
    return inner_results.getBigDecimal(columnName);
  }

  public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
    return inner_results.getBigDecimal(columnIndex, scale);
  }

  public BigDecimal getBigDecimal(String columnName, int scale) throws SQLException {
    return inner_results.getBigDecimal(columnName, scale);
  }

  public InputStream getBinaryStream(int columnIndex) throws SQLException {
    return inner_results.getBinaryStream(columnIndex);
  }

  public InputStream getBinaryStream(String columnName) throws SQLException {
    return inner_results.getBinaryStream(columnName);
  }

  public Blob getBlob(int i) throws SQLException {
    return inner_results.getBlob(i);
  }

  public Blob getBlob(String colName) throws SQLException {
    return inner_results.getBlob(colName);
  }

  public boolean getBoolean(int columnIndex) throws SQLException {
    return inner_results.getBoolean(columnIndex);
  }

  public boolean getBoolean(String columnName) throws SQLException {
    return inner_results.getBoolean(columnName);
  }

  public byte getByte(int columnIndex) throws SQLException {
    return inner_results.getByte(columnIndex);
  }

  public byte getByte(String columnName) throws SQLException {
    return inner_results.getByte(columnName);
  }

  public byte[] getBytes(int columnIndex) throws SQLException {
    return inner_results.getBytes(columnIndex);
  }

  public byte[] getBytes(String columnName) throws SQLException {
    return inner_results.getBytes(columnName);
  }

  public Reader getCharacterStream(int columnIndex) throws SQLException {
    return inner_results.getCharacterStream(columnIndex);
  }

  public Reader getCharacterStream(String columnName) throws SQLException {
    return inner_results.getCharacterStream(columnName);
  }

  public Clob getClob(int i) throws SQLException {
    return inner_results.getClob(i);
  }

  public Clob getClob(String colName) throws SQLException {
    return inner_results.getClob(colName);
  }

  public int getConcurrency() throws SQLException {
    return inner_results.getConcurrency();
  }

  public String getCursorName() throws SQLException {
    return inner_results.getCursorName();
  }

  public Date getDate(int columnIndex) throws SQLException {
    return inner_results.getDate(columnIndex);
  }

  public Date getDate(String columnName) throws SQLException {
    return inner_results.getDate(columnName);
  }

  public Date getDate(int columnIndex, Calendar cal) throws SQLException {
    return inner_results.getDate(columnIndex, cal);
  }

  public Date getDate(String columnName, Calendar cal) throws SQLException {
    return inner_results.getDate(columnName, cal);
  }

  public double getDouble(int columnIndex) throws SQLException {
    return inner_results.getDouble(columnIndex);
  }

  public double getDouble(String columnName) throws SQLException {
    return inner_results.getDouble(columnName);
  }

  public int getFetchDirection() throws SQLException {
    return inner_results.getFetchDirection();
  }

  public String toString() {
    return inner_results.toString();
  }

  public float getFloat(int columnIndex) throws SQLException {
    return inner_results.getFloat(columnIndex);
  }

  public float getFloat(String columnName) throws SQLException {
    return inner_results.getFloat(columnName);
  }

  public int getHoldability() throws SQLException {
    return inner_results.getHoldability();
  }

  public int getInt(int columnIndex) throws SQLException {
    return inner_results.getInt(columnIndex);
  }

  public int getInt(String columnName) throws SQLException {
    return inner_results.getInt(columnName);
  }

  public long getLong(int columnIndex) throws SQLException {
    return inner_results.getLong(columnIndex);
  }

  public void close() throws SQLException {
    inner_results.close();
  }

  public boolean next() throws SQLException {
    return inner_results.next();
  }

  public long getLong(String columnName) throws SQLException {
    return inner_results.getLong(columnName);
  }

  public Reader getNCharacterStream(int arg0) throws SQLException {
    return inner_results.getNCharacterStream(arg0);
  }

  public Reader getNCharacterStream(String arg0) throws SQLException {
    return inner_results.getNCharacterStream(arg0);
  }

  public NClob getNClob(int arg0) throws SQLException {
    return inner_results.getNClob(arg0);
  }

  public NClob getNClob(String columnLabel) throws SQLException {
    return inner_results.getNClob(columnLabel);
  }

  public String getNString(int columnIndex) throws SQLException {
    return inner_results.getNString(columnIndex);
  }

  public String getNString(String columnLabel) throws SQLException {
    return inner_results.getNString(columnLabel);
  }

  public ResultSetMetaData getMetaData() throws SQLException {
    return new ForgivingHiveResultSetMetadata(inner_results.getMetaData());
  }

  public void setFetchSize(int rows) throws SQLException {
    inner_results.setFetchSize(rows);
  }

  public int getType() throws SQLException {
    return inner_results.getType();
  }

  public Object getObject(int columnIndex) throws SQLException {
    return inner_results.getObject(columnIndex);
  }

  public int getFetchSize() throws SQLException {
    return inner_results.getFetchSize();
  }

  public Object getObject(String columnName) throws SQLException {
    return inner_results.getObject(columnName);
  }

  public Object getObject(int i, Map<String, Class<?>> map) throws SQLException {
    return inner_results.getObject(i, map);
  }

  public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
    return inner_results.getObject(columnLabel, type);
  }

  public Object getObject(String colName, Map<String, Class<?>> map) throws SQLException {
    return inner_results.getObject(colName, map);
  }

  public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
    return inner_results.getObject(columnIndex, type);
  }

  public Ref getRef(int i) throws SQLException {
    return inner_results.getRef(i);
  }

  public void beforeFirst() throws SQLException {
    inner_results.beforeFirst();
  }

  public Ref getRef(String colName) throws SQLException {
    return inner_results.getRef(colName);
  }

  public RowId getRowId(int columnIndex) throws SQLException {
    return inner_results.getRowId(columnIndex);
  }

  public RowId getRowId(String columnLabel) throws SQLException {
    return inner_results.getRowId(columnLabel);
  }

  public boolean isBeforeFirst() throws SQLException {
    return inner_results.isBeforeFirst();
  }

  public SQLXML getSQLXML(int columnIndex) throws SQLException {
    return inner_results.getSQLXML(columnIndex);
  }

  public SQLXML getSQLXML(String columnLabel) throws SQLException {
    return inner_results.getSQLXML(columnLabel);
  }

  public int getRow() throws SQLException {
    return inner_results.getRow();
  }

  public short getShort(int columnIndex) throws SQLException {
    return inner_results.getShort(columnIndex);
  }

  public short getShort(String columnName) throws SQLException {
    return inner_results.getShort(columnName);
  }

  public Statement getStatement() throws SQLException {
    return inner_results.getStatement();
  }

  public String getString(int columnIndex) throws SQLException {
    return inner_results.getString(columnIndex);
  }

  public String getString(String columnName) throws SQLException {
    return inner_results.getString(columnName);
  }

  public Time getTime(int columnIndex) throws SQLException {
    return inner_results.getTime(columnIndex);
  }

  public Time getTime(String columnName) throws SQLException {
    return inner_results.getTime(columnName);
  }

  public Time getTime(int columnIndex, Calendar cal) throws SQLException {
    return inner_results.getTime(columnIndex, cal);
  }

  public Time getTime(String columnName, Calendar cal) throws SQLException {
    return inner_results.getTime(columnName, cal);
  }

  public Timestamp getTimestamp(int columnIndex) throws SQLException {
    return inner_results.getTimestamp(columnIndex);
  }

  public Timestamp getTimestamp(String columnName) throws SQLException {
    return inner_results.getTimestamp(columnName);
  }

  public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
    return inner_results.getTimestamp(columnIndex, cal);
  }

  public Timestamp getTimestamp(String columnName, Calendar cal) throws SQLException {
    return inner_results.getTimestamp(columnName, cal);
  }

  public URL getURL(int columnIndex) throws SQLException {
    return inner_results.getURL(columnIndex);
  }

  public URL getURL(String columnName) throws SQLException {
    return inner_results.getURL(columnName);
  }

  public InputStream getUnicodeStream(int columnIndex) throws SQLException {
    return inner_results.getUnicodeStream(columnIndex);
  }

  public InputStream getUnicodeStream(String columnName) throws SQLException {
    return inner_results.getUnicodeStream(columnName);
  }

  public void insertRow() throws SQLException {
    inner_results.insertRow();
  }

  public boolean isAfterLast() throws SQLException {
    return inner_results.isAfterLast();
  }

  public boolean isClosed() throws SQLException {
    return inner_results.isClosed();
  }

  public boolean isFirst() throws SQLException {
    return inner_results.isFirst();
  }

  public boolean isLast() throws SQLException {
    return inner_results.isLast();
  }

  public boolean last() throws SQLException {
    return inner_results.last();
  }

  public void moveToCurrentRow() throws SQLException {
    inner_results.moveToCurrentRow();
  }

  public void moveToInsertRow() throws SQLException {
    inner_results.moveToInsertRow();
  }

  public boolean previous() throws SQLException {
    return inner_results.previous();
  }

  public void refreshRow() throws SQLException {
    inner_results.refreshRow();
  }

  public boolean relative(int rows) throws SQLException {
    return inner_results.relative(rows);
  }

  public boolean rowDeleted() throws SQLException {
    return inner_results.rowDeleted();
  }

  public boolean rowInserted() throws SQLException {
    return inner_results.rowInserted();
  }

  public boolean rowUpdated() throws SQLException {
    return inner_results.rowUpdated();
  }

  public void setFetchDirection(int direction) throws SQLException {
    inner_results.setFetchDirection(direction);
  }

  public void updateArray(int columnIndex, Array x) throws SQLException {
    inner_results.updateArray(columnIndex, x);
  }

  public void updateArray(String columnName, Array x) throws SQLException {
    inner_results.updateArray(columnName, x);
  }

  public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
    inner_results.updateAsciiStream(columnIndex, x);
  }

  public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
    inner_results.updateAsciiStream(columnLabel, x);
  }

  public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
    inner_results.updateAsciiStream(columnIndex, x, length);
  }

  public void updateAsciiStream(String columnName, InputStream x, int length) throws SQLException {
    inner_results.updateAsciiStream(columnName, x, length);
  }

  public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
    inner_results.updateAsciiStream(columnIndex, x, length);
  }

  public void updateAsciiStream(String columnLabel,
    InputStream x,
    long length) throws SQLException {
    inner_results.updateAsciiStream(columnLabel, x, length);
  }

  public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
    inner_results.updateBigDecimal(columnIndex, x);
  }

  public void updateBigDecimal(String columnName, BigDecimal x) throws SQLException {
    inner_results.updateBigDecimal(columnName, x);
  }

  public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
    inner_results.updateBinaryStream(columnIndex, x);
  }

  public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
    inner_results.updateBinaryStream(columnLabel, x);
  }

  public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
    inner_results.updateBinaryStream(columnIndex, x, length);
  }

  public void updateBinaryStream(String columnName, InputStream x, int length) throws SQLException {
    inner_results.updateBinaryStream(columnName, x, length);
  }

  public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
    inner_results.updateBinaryStream(columnIndex, x, length);
  }

  public void updateBinaryStream(String columnLabel,
    InputStream x, long length) throws SQLException {
    inner_results.updateBinaryStream(columnLabel, x, length);
  }

  public void updateBlob(int columnIndex, Blob x) throws SQLException {
    inner_results.updateBlob(columnIndex, x);
  }

  public void updateBlob(String columnName, Blob x) throws SQLException {
    inner_results.updateBlob(columnName, x);
  }

  public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
    inner_results.updateBlob(columnIndex, inputStream);
  }

  public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
    inner_results.updateBlob(columnLabel, inputStream);
  }

  public void updateBlob(int columnIndex, InputStream inputStream, long length)
      throws SQLException {
    inner_results.updateBlob(columnIndex, inputStream, length);
  }

  public void updateBlob(String columnLabel, InputStream inputStream, long length)
      throws SQLException {
    inner_results.updateBlob(columnLabel, inputStream, length);
  }

  public void updateBoolean(int columnIndex, boolean x) throws SQLException {
    inner_results.updateBoolean(columnIndex, x);
  }

  public void updateBoolean(String columnName, boolean x) throws SQLException {
    inner_results.updateBoolean(columnName, x);
  }

  public void updateByte(int columnIndex, byte x) throws SQLException {
    inner_results.updateByte(columnIndex, x);
  }

  public void updateByte(String columnName, byte x) throws SQLException {
    inner_results.updateByte(columnName, x);
  }

  public void updateBytes(int columnIndex, byte[] x) throws SQLException {
    inner_results.updateBytes(columnIndex, x);
  }

  public void updateBytes(String columnName, byte[] x) throws SQLException {
    inner_results.updateBytes(columnName, x);
  }

  public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
    inner_results.updateCharacterStream(columnIndex, x);
  }

  public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
    inner_results.updateCharacterStream(columnLabel, reader);
  }

  public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
    inner_results.updateCharacterStream(columnIndex, x, length);
  }

  public void updateCharacterStream(String columnName, Reader reader, int length)
      throws SQLException {
    inner_results.updateCharacterStream(columnName, reader, length);
  }

  public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
    inner_results.updateCharacterStream(columnIndex, x, length);
  }

  public void updateCharacterStream(String columnLabel, Reader reader, long length)
      throws SQLException {
    inner_results.updateCharacterStream(columnLabel, reader, length);
  }

  public void updateClob(int columnIndex, Clob x) throws SQLException {
    inner_results.updateClob(columnIndex, x);
  }

  public void updateClob(String columnName, Clob x) throws SQLException {
    inner_results.updateClob(columnName, x);
  }

  public void updateClob(int columnIndex, Reader reader) throws SQLException {
    inner_results.updateClob(columnIndex, reader);
  }

  public void updateClob(String columnLabel, Reader reader) throws SQLException {
    inner_results.updateClob(columnLabel, reader);
  }

  public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
    inner_results.updateClob(columnIndex, reader, length);
  }

  public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
    inner_results.updateClob(columnLabel, reader, length);
  }

  public void updateDate(int columnIndex, Date x) throws SQLException {
    inner_results.updateDate(columnIndex, x);
  }

  public void updateDate(String columnName, Date x) throws SQLException {
    inner_results.updateDate(columnName, x);
  }

  public void updateDouble(int columnIndex, double x) throws SQLException {
    inner_results.updateDouble(columnIndex, x);
  }

  public void updateDouble(String columnName, double x) throws SQLException {
    inner_results.updateDouble(columnName, x);
  }

  public void updateFloat(int columnIndex, float x) throws SQLException {
    inner_results.updateFloat(columnIndex, x);
  }

  public void updateFloat(String columnName, float x) throws SQLException {
    inner_results.updateFloat(columnName, x);
  }

  public void updateInt(int columnIndex, int x) throws SQLException {
    inner_results.updateInt(columnIndex, x);
  }

  public void updateInt(String columnName, int x) throws SQLException {
    inner_results.updateInt(columnName, x);
  }

  public void updateLong(int columnIndex, long x) throws SQLException {
    inner_results.updateLong(columnIndex, x);
  }

  public void updateLong(String columnName, long x) throws SQLException {
    inner_results.updateLong(columnName, x);
  }

  public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
    inner_results.updateNCharacterStream(columnIndex, x);
  }

  public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
    inner_results.updateNCharacterStream(columnLabel, reader);
  }

  public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
    inner_results.updateNCharacterStream(columnIndex, x, length);
  }

  public void updateNCharacterStream(String columnLabel, Reader reader, long length)
      throws SQLException {
    inner_results.updateNCharacterStream(columnLabel, reader, length);
  }

  public void updateNClob(int columnIndex, NClob clob) throws SQLException {
    inner_results.updateNClob(columnIndex, clob);
  }

  public void updateNClob(String columnLabel, NClob clob) throws SQLException {
    inner_results.updateNClob(columnLabel, clob);
  }

  public void updateNClob(int columnIndex, Reader reader) throws SQLException {
    inner_results.updateNClob(columnIndex, reader);
  }

  public void updateNClob(String columnLabel, Reader reader) throws SQLException {
    inner_results.updateNClob(columnLabel, reader);
  }

  public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
    inner_results.updateNClob(columnIndex, reader, length);
  }

  public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
    inner_results.updateNClob(columnLabel, reader, length);
  }

  public void updateNString(int columnIndex, String string) throws SQLException {
    inner_results.updateNString(columnIndex, string);
  }

  public void updateNString(String columnLabel, String string) throws SQLException {
    inner_results.updateNString(columnLabel, string);
  }

  public void updateNull(int columnIndex) throws SQLException {
    inner_results.updateNull(columnIndex);
  }

  public void updateNull(String columnName) throws SQLException {
    inner_results.updateNull(columnName);
  }

  public void updateObject(int columnIndex, Object x) throws SQLException {
    inner_results.updateObject(columnIndex, x);
  }

  public void updateObject(String columnName, Object x) throws SQLException {
    inner_results.updateObject(columnName, x);
  }

  public void updateObject(int columnIndex, Object x, int scale) throws SQLException {
    inner_results.updateObject(columnIndex, x, scale);
  }

  public void updateObject(String columnName, Object x, int scale) throws SQLException {
    inner_results.updateObject(columnName, x, scale);
  }

  public void updateRef(int columnIndex, Ref x) throws SQLException {
    inner_results.updateRef(columnIndex, x);
  }

  public void updateRef(String columnName, Ref x) throws SQLException {
    inner_results.updateRef(columnName, x);
  }

  public void updateRow() throws SQLException {
    inner_results.updateRow();
  }

  public void updateRowId(int columnIndex, RowId x) throws SQLException {
    inner_results.updateRowId(columnIndex, x);
  }

  public void updateRowId(String columnLabel, RowId x) throws SQLException {
    inner_results.updateRowId(columnLabel, x);
  }

  public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
    inner_results.updateSQLXML(columnIndex, xmlObject);
  }

  public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
    inner_results.updateSQLXML(columnLabel, xmlObject);
  }

  public void updateShort(int columnIndex, short x) throws SQLException {
    inner_results.updateShort(columnIndex, x);
  }

  public void updateShort(String columnName, short x) throws SQLException {
    inner_results.updateShort(columnName, x);
  }

  public void updateString(int columnIndex, String x) throws SQLException {
    inner_results.updateString(columnIndex, x);
  }

  public void updateString(String columnName, String x) throws SQLException {
    inner_results.updateString(columnName, x);
  }

  public void updateTime(int columnIndex, Time x) throws SQLException {
    inner_results.updateTime(columnIndex, x);
  }

  public void updateTime(String columnName, Time x) throws SQLException {
    inner_results.updateTime(columnName, x);
  }

  public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
    inner_results.updateTimestamp(columnIndex, x);
  }

  public void updateTimestamp(String columnName, Timestamp x) throws SQLException {
    inner_results.updateTimestamp(columnName, x);
  }

  public SQLWarning getWarnings() throws SQLException {
    return inner_results.getWarnings();
  }

  public void clearWarnings() throws SQLException {
    inner_results.clearWarnings();
  }

  public boolean wasNull() throws SQLException {
    return inner_results.wasNull();
  }

  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return inner_results.isWrapperFor(iface);
  }

  public <T> T unwrap(Class<T> iface) throws SQLException {
    return inner_results.unwrap(iface);
  }

  public ForgivingHiveResultSet(ResultSet source)
  {
    this.inner_results = source;
  }
  
  
}
