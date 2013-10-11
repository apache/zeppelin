package com.nflabs.zeppelin;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;


import com.nflabs.zeppelin.conf.ZeppelinConfiguration;

import com.nflabs.zeppelin.zrt.User;
import com.nflabs.zeppelin.zrt.ZeppelinRuntime;



public class Zeppelin {
	Logger logger = Logger.getLogger(Zeppelin.class.getName());
	private ThreadPoolExecutor executorPool;
	private ZeppelinConfiguration conf;
	private ZeppelinRuntime runtime;
	private User user;
	

	public Zeppelin(ZeppelinConfiguration conf, User user) throws Exception{
		this.conf = conf;
		this.user = user;
		
		if(System.getProperty("spark.serializer")==null){
			System.setProperty("spark.serializer", "org.apache.spark.serializer.KryoSerializer");
		}
		
		this.runtime = new ZeppelinRuntime(conf, user);
		
	}
	

	public void destroy(){
		runtime.destroy();
	}
	
	
}
