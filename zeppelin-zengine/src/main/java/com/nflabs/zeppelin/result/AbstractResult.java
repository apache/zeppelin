package com.nflabs.zeppelin.result;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

public abstract class AbstractResult {
    //TODO: static configuration vs dependency igection
    // = Zengine.getConf().getLong(ConfVars.ZEPPELIN_MAX_RESULT)
	private static final long DEFAULT_RESULT_NUM_LIMIT = 100;
    private ColumnDef [] columnDef;
	private long max;
	
	transient private ResultSet res;
	transient Exception e;
	
	boolean loaded = false;
	@SuppressWarnings("unused")
    private int code;
	
	public AbstractResult(ResultSet res) throws ResultDataException {
		this(res, -1);
	}
	public AbstractResult(ResultSet res, long max) throws ResultDataException {
		try {
			init(res, max);
		} catch (SQLException e) {
			throw new ResultDataException(e);
		}
	}
	
	public boolean isLoaded(){
		return loaded;
	}

	public AbstractResult(Exception e) throws ResultDataException {
		this.e = e;
		columnDef = new ColumnDef[4];
		columnDef[0] = new ColumnDef("class", Types.VARCHAR, "varchar");
		columnDef[1] = new ColumnDef("message", Types.VARCHAR, "varchar");
		columnDef[2] = new ColumnDef("stacktrace", Types.VARCHAR, "varchar");
		columnDef[3] = new ColumnDef("cause", Types.VARCHAR, "varchar");
		
		max = -1;
		Object [] row = new Object[4];
		row[0] = e.getClass().getName();
		row[1] = e.getMessage();
		row[2] = e.getStackTrace();
		row[3] = (e.getCause()==null) ? null : e.getCause().getMessage();
		try {
			process(columnDef, row, 0);
		} catch (Exception e1) {
			throw new ResultDataException(e1);
		}
	}
	
	public AbstractResult(int code, String [] message) throws ResultDataException {
		this.code = code;
		
		if(message!=null){
			columnDef = new ColumnDef[1];
			columnDef[0] = new ColumnDef("message", Types.VARCHAR, "varchar");
	
			try {
				for(int i=0; i<message.length; i++){
					process(columnDef, new String[]{message[i]}, i);			
				}
			} catch (Exception e1) {
				throw new ResultDataException(e1);
			}
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
		if (max < 0){
		    //FIXME(alex): is static configuration evil?
			this.max = DEFAULT_RESULT_NUM_LIMIT;
		} else {
			this.max = max;
		}
	}
		
	public void load() throws SQLException, ResultDataException{
		if(loaded==true) return;
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
		loaded = true;

	}

	protected abstract void process(ColumnDef [] columnDef, Object [] row, long n) throws Exception;

	public ColumnDef [] getColumnDef(){
		return columnDef;
	}
}
