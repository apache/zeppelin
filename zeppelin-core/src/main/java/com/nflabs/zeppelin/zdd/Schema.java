package com.nflabs.zeppelin.zdd;

public class Schema {
	ColumnDesc[] columns;
	String name;
	
	public Schema(String name, ColumnDesc [] columns){
		this.name = name;
		this.columns = columns;
	}

	public ColumnDesc[] getColumns() {
		return columns;
	}

	public String getName() {
		return name;
	}

	public String toHiveTableCreationQueryColumnPart(){
		String col = null;
		
		if(columns!=null){
			for(ColumnDesc c : columns){
				if(col!=null) col += ", ";
				col += c.name() + " " + c.type().hiveName;
			}
		} else {
			return "";
		}
		
		return col;
	}
	
}
