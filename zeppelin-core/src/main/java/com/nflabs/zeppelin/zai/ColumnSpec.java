package com.nflabs.zeppelin.zai;

import com.nflabs.zeppelin.zdd.DataType;

public class ColumnSpec {
	private String columnName;
	private int columnIndex;
	private DataType dataType;

	public ColumnSpec(){
		
	}
	
	public ColumnSpec columnName(String name){
		this.columnName = name;
		return this;
	}
	
	public ColumnSpec columnIndex(int i){
		this.columnIndex = i;
		return this;
	}
	
	public ColumnSpec withType(DataType type){
		this.dataType = type;
		return this;
	}

	public String getColumnName() {
		return columnName;
	}

	public int getColumnIndex() {
		return columnIndex;
	}

	public DataType getDataType() {
		return dataType;
	}
	
	
}
