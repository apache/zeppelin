package com.nflabs.zeppelin.zdd;


import shark.api.TableRDD;

public class ZDD {
	private TableRDD rdd;
	private Schema schema;
	

	public ZDD(TableRDD rdd) {
		this.rdd = rdd;
		this.schema = new Schema(ColumnDesc.createSchema(rdd.schema()));
	}
	
	public Schema schema(){
		return schema;
	}
	
	public TableRDD rdd(){
		return rdd;
	}
	
	public String tableName(){
		return rdd.name();
	}
	
	public boolean equals(Object o){
		if(o instanceof ZDD){
			return tableName().equals(((ZDD)o).tableName());
		} else {
			return false;
		}
	}
}
