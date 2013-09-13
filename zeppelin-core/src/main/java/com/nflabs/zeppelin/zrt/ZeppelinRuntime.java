package com.nflabs.zeppelin.zrt;

import java.util.Date;

import org.apache.spark.SparkContext;

import shark.SharkContext;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;

public class ZeppelinRuntime {	
	private ZeppelinConfiguration conf;
	private SharkContext sharkContext;
	private SparkContext sparkContext;
	
	public ZeppelinRuntime(ZeppelinConfiguration conf){
		this.conf = conf;
		
		String sparkMaster = getEnv("MASTER", "local");
		String sparkHome = getEnv("SPARK_HOME", "./");
		String jobName = "ZeppelinRuntime-"+new Date();

		sparkContext = sharkContext = new SharkContext(sparkMaster, jobName, sparkHome, null, scala.collection.JavaConversions.mapAsScalaMap(System.getenv()));
	}
	
	private String getEnv(String key, String def){
		String val = System.getenv(key);
		if(val==null) return def;
		else return val;
	}
	
	public SharkContext sharkContext(){
		return sharkContext;
	}
	
	public SparkContext sparkContext(){
		return sparkContext;
	}
	
	public ExecContext execContext(){
		return new ExecContext();
	}
	

	public void destroy(){
		
	}

	
}
