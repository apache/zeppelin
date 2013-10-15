package com.nflabs.zeppelin.zai;

import java.util.List;

import com.nflabs.zeppelin.zai.ParamSpec.Option;
import com.nflabs.zeppelin.zai.ParamSpec.Range;
import com.nflabs.zeppelin.zdd.DataType;

public class ColumnSpec {
	private String columnName;
	private int columnIndex=-1;
	private DataType dataType;
	
	private boolean repeat;

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
	
	public ColumnSpec withRepeat(boolean repeat){
		this.repeat = repeat;
		return this;
	}
	
	public boolean getRepeat(){
		return repeat;
	}
	
	
}
