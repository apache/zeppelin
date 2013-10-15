package com.nflabs.zeppelin.zai;

import java.util.LinkedList;
import java.util.List;

public class DataSpec {

	List<ColumnSpec> columnSpecs = new LinkedList<ColumnSpec>();
	
	public DataSpec(){
		
	}
	
	public DataSpec addColumnSpec(ColumnSpec spec){
		columnSpecs.add(spec);
		return this;
	}
	
	public DataSpec withColumnSpecs(ColumnSpec [] specs){
		columnSpecs.clear();
		if(specs!=null){
			for(ColumnSpec s : specs){
				columnSpecs.add(s);
			}
		}
		return this;			
	}
	
	public ColumnSpec [] getColumnSpecs(){
		return columnSpecs.toArray(new ColumnSpec[]{});
	}
}
