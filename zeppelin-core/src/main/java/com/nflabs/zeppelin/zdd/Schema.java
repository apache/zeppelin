package com.nflabs.zeppelin.zdd;

public class Schema {
	ColumnDesc[] columns;
	
	public Schema(ColumnDesc [] columns){
		this.columns = columns;
	}

	public ColumnDesc[] getColumns() {
		return columns;
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
