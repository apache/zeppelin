package org.apache.zeppelin.hive;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
/**
 * Hive does not implement all of the methods needed by CachedResultSet,
 * but it could have a sensible default implementation that will work.
 * @author Rusty Phillips {@literal: <rusty@cloudspace.com>}
 *
 */
public class ForgivingHiveResultSetMetadata implements ResultSetMetaData {
  
  ResultSetMetaData innerMetaData;
  
  public ForgivingHiveResultSetMetadata(ResultSetMetaData metaData) {
    this.innerMetaData = metaData;
  }

  public <T> T unwrap(Class<T> iface) throws SQLException {
    return innerMetaData.unwrap(iface);
  }

  public int getColumnCount() throws SQLException {
    return innerMetaData.getColumnCount();
  }

  public boolean isAutoIncrement(int column) throws SQLException {
    return innerMetaData.isAutoIncrement(column);
  }

  public boolean isCaseSensitive(int column) throws SQLException {
    return innerMetaData.isCaseSensitive(column);
  }

  public boolean isSearchable(int column) throws SQLException {
    return false;
  }

  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return innerMetaData.isWrapperFor(iface);
  }

  public boolean isCurrency(int column) throws SQLException {
    return false;
  }

  public int isNullable(int column) throws SQLException {
    return innerMetaData.isNullable(column);
  }

  public boolean isSigned(int column) throws SQLException {
    return false;
  }

  public int getColumnDisplaySize(int column) throws SQLException {
    return innerMetaData.getColumnDisplaySize(column);
  }

  public String getColumnLabel(int column) throws SQLException {
    return innerMetaData.getColumnLabel(column);
  }

  public String getColumnName(int column) throws SQLException {
    return innerMetaData.getColumnName(column);
  }

  public String getSchemaName(int column) throws SQLException {
    return "";
  }

  public int getPrecision(int column) throws SQLException {
    return innerMetaData.getPrecision(column);
  }

  public int getScale(int column) throws SQLException {
    return innerMetaData.getScale(column);
  }

  public String getTableName(int column) throws SQLException {
    return "";
  }

  public String getCatalogName(int column) throws SQLException {
    return "";
  }

  public int getColumnType(int column) throws SQLException {
    return innerMetaData.getColumnType(column);
  }

  public String getColumnTypeName(int column) throws SQLException {
    return innerMetaData.getColumnTypeName(column);
  }

  public boolean isReadOnly(int column) throws SQLException {
    return innerMetaData.isReadOnly(column);
  }

  public boolean isWritable(int column) throws SQLException {
    return innerMetaData.isWritable(column);
  }

  public boolean isDefinitelyWritable(int column) throws SQLException {
    return innerMetaData.isDefinitelyWritable(column);
  }

  public String getColumnClassName(int column) throws SQLException {
    return innerMetaData.getColumnClassName(column);
  }

}
