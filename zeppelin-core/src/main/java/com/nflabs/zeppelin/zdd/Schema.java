package com.nflabs.zeppelin.zdd;

public class Schema {
	public final ColumnDesc[] columns;
	public final String name;
	
	public Schema(String name, ColumnDesc [] columns){
		this.name = name;
		this.columns = columns;
	}

	
}
