package com.nflabs.zeppelin.zai;

import java.util.LinkedList;
import java.util.List;

public class SchemaSpec {
	List<ColumnSpec> columnSpecs;
	public SchemaSpec(){
		columnSpecs = new LinkedList<ColumnSpec>();
	}
	
	public SchemaSpec add(ColumnSpec col){
		columnSpecs.add(col);
		return this;
	}
	
	public List<ColumnSpec> getColumnSpecs(){
		return columnSpecs;
	}
}
