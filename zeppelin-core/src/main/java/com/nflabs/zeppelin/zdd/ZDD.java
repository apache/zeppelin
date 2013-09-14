package com.nflabs.zeppelin.zdd;



import org.apache.spark.rdd.RDD;

import shark.api.TableRDD;

public class ZDD {
	private TableRDD rdd;
	private Schema schema;

	public ZDD(TableRDD rdd) {
		this.rdd = rdd;
		this.schema = new Schema(rdd.name(), ColumnDesc.createSchema(rdd.schema()));
	}
	
	public Schema schema(){
		return schema;
	}
	
	public RDD rdd(){
		return rdd;
	}
}
