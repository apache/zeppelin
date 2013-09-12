package com.nflabs.zeppelin.zrt;

import org.apache.spark.SparkContext;

import shark.SharkContext;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;

public class ZeppelinRuntime {	
	private ZeppelinConfiguration conf;
	
	public ZeppelinRuntime(ZeppelinConfiguration conf){
		this.conf = conf;
	}

	public SharkContext sharkContext(){
		return null;
	}
	
	public SparkContext sparkContext(){
		return null;
	}
	
	public ExecContext execContext(){
		return new ExecContext();
	}
	

	public void destroy(){
		
	}

	
}
