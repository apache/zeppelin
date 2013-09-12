package com.nflabs.zeppelin;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;

import shark.api.TableRDD;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.zai.Param;
import com.nflabs.zeppelin.zai.ZeppelinApplication;
import com.nflabs.zeppelin.zdd.ZDD;
import com.nflabs.zeppelin.zrt.ZeppelinRuntime;



public class Zeppelin {
	Logger logger = Logger.getLogger(Zeppelin.class.getName());
	private ThreadPoolExecutor executorPool;
	private ZeppelinConfiguration conf;
	private ZeppelinRuntime runtime;
	

	public Zeppelin(ZeppelinConfiguration conf){
		this.conf = conf;		
		this.runtime = new ZeppelinRuntime(conf);
	}
	
	public List<ZDD> run(ZeppelinApplication za, List<ZDD> inputs, List<Param> params){
		return za.execute(inputs, params);
	}
	
	public ZDD fromHadoop(URI location){
		return null;
	}
	
	public ZDD fromShark(String sql){
		TableRDD rdd = runtime.sharkContext().sql2rdd(sql);
		return new ZDD(rdd);
	}
	
	
}
