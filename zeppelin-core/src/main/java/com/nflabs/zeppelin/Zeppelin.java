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
		//this.runtime = new ZeppelinRuntime(conf, new User(""));
	}
	

	
	
}
