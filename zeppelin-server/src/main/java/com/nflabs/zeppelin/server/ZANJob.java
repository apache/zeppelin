package com.nflabs.zeppelin.server;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.zan.ZAN;

public class ZANJob extends Job{
	public static enum Operation {
		INSTALL,    // install
		UNINSTALL,  // uninstall
		UPGRADE,    // upgrade lib
		UPDATE		// update catalog		
	}

	private Operation op;
	private String libName;
	transient private ZAN zan;
	
	public ZANJob(String jobName, com.nflabs.zeppelin.zan.ZAN zan, Operation op, String libName, JobListener listener){
		super(jobName, listener);
		this.zan = zan;
		this.op = op;
		this.libName = libName;
	}
	
	private Logger logger(){
		return LoggerFactory.getLogger(ZQLJob.class);
	}
	
	@Override
	public int progress() {
		return 0;
	}

	@Override
	public Map<String, Object> info() {
		return new HashMap<String, Object>();
	}

	@Override
	protected Object jobRun() throws Throwable {
		if (op==Operation.UPDATE) {
			zan.update();
		} else if (op==Operation.INSTALL) {
			zan.install(libName, null);
		} else if (op==Operation.UNINSTALL) {
			zan.uninstall(libName);
		} else if (op==Operation.UPGRADE) {
			zan.upgrade(libName, null);
		} else {
			logger().warn("Unsupported operation "+op.toString());
		}
		return null;
	}

	@Override
	protected boolean jobAbort() {
		return false;
	}

}
