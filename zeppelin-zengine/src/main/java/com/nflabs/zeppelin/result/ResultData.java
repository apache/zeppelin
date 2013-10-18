package com.nflabs.zeppelin.result;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedList;
import java.util.List;

public abstract class ResultData {
	ColumnDef [] columnDef;
	private ResultSet res;
	private long max;
	
	public ResultData(ResultSet res) throws ResultDataException {
		this(res, -1);
	}
	public ResultData(ResultSet res, long max) throws ResultDataException {
		try {
			beforeProcess();
			init(res, max);
			afterProcess();
		} catch (SQLException e) {
			throw new ResultDataException(e);
		}
	}
	
	public void init(ResultSet res, long max) throws SQLException, ResultDataException{
		ResultSetMetaData metaData = res.getMetaData();
		int numColumns = metaData.getColumnCount();
		
		// meta data extraction
		columnDef = new ColumnDef[numColumns];
		
		for(int i=0; i<numColumns; i++){
			columnDef[i] = new ColumnDef(metaData.getColumnLabel(i+1), metaData.getColumnType(i+1), metaData.getColumnTypeName(i+1));
		}
		
		this.res = res;
		this.max = max;
	}
		
	public void load() throws SQLException, ResultDataException{
		// row data extraction
		int numColumns = columnDef.length;
		
		long count = 0;
		while(res.next()){
			count++;
			if(max >=0 && count>max) break;
			Object [] row = new Object[numColumns];
			for(int i=0; i<numColumns; i++){
				int type = columnDef[i].getType();
				int c = i+1;
				if(type == Types.ARRAY){
					row[i] = res.getArray(c);
				} else if(type==Types.BIGINT){
					row[i] = res.getLong(c);
				} else if(type==Types.BINARY){
					row[i] = res.getString(c);
				} else if(type==Types.BIT){
					row[i] = res.getString(c);
				} else if(type==Types.BLOB){
					row[i] = res.getBlob(c);
				} else if(type==Types.BOOLEAN){
					row[i] = res.getBoolean(c);
				} else if(type==Types.CHAR){
					row[i] = res.getString(c);
				} else if(type==Types.CLOB){
					row[i] = res.getClob(c);
				} else if(type==Types.DATALINK){
					row[i] = res.getString(c);
				} else if(type==Types.DATE){
					row[i] = res.getDate(c);
				} else if(type==Types.DECIMAL){
					row[i] = res.getInt(c);
				} else if(type==Types.DISTINCT){
					row[i] = res.getString(c);
				} else if(type==Types.DOUBLE){
					row[i] = res.getDouble(c);
				} else if(type==Types.FLOAT){
					row[i] = res.getFloat(c);
				} else if(type==Types.INTEGER){
					row[i] = res.getInt(c);
				} else if(type==Types.JAVA_OBJECT){
					row[i] = res.getObject(c);
				} else if(type==Types.LONGNVARCHAR){
					row[i] = res.getString(c);
				} else if(type==Types.LONGVARBINARY){
					row[i] = res.getString(c);
				} else if(type==Types.LONGVARCHAR){
					row[i] = res.getString(c);
				} else if(type==Types.NCHAR){
					row[i] = res.getNCharacterStream(c);
				} else if(type==Types.NCLOB){
					row[i] = res.getNClob(c);
				} else if(type==Types.NULL){
					row[i] = res.getString(c);
				} else if(type==Types.NUMERIC){
					row[i] = res.getLong(c);
				} else if(type==Types.NVARCHAR){
					row[i] = res.getString(c);
				} else if(type==Types.OTHER){
					row[i] = res.getString(c);
				} else if(type==Types.REAL){
					row[i] = res.getDouble(c);
				} else if(type==Types.REF){
					row[i] = res.getString(c);
				} else if(type==Types.ROWID){
					row[i] = res.getString(c);
				} else if(type==Types.SMALLINT){
					row[i] = res.getShort(c);
				} else if(type==Types.SQLXML){
					row[i] = res.getSQLXML(c);
				} else if(type==Types.STRUCT){
					row[i] = res.getString(c);
				} else if(type==Types.TIME){
					row[i] = res.getTime(c);
				} else if(type==Types.TIMESTAMP){
					row[i] = res.getTimestamp(c);
				} else if(type==Types.TINYINT){
					row[i] = res.getShort(c);
				} else if(type==Types.VARBINARY){
					row[i] = res.getString(c);
				} else if(type==Types.VARCHAR){
					row[i] = res.getString(c);
				} else {
					row[i] = null;
				}
			}
			try {
				process(columnDef, row, count-1);
			} catch (Exception e) {
				throw new ResultDataException(e);
			}
		}

	}
	protected abstract void beforeProcess();
	protected abstract void process(ColumnDef [] columnDef, Object [] row, long n) throws Exception;
	protected abstract void afterProcess();
}
