package com.nflabs.zeppelin.server;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.zengine.Z;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.ZQL;

public class ZQLSession extends Job{
	transient Logger logger = Logger.getLogger(ZQLSession.class);

	private String zql;
	Result error;
	
	public ZQLSession(String jobName, JobListener listener) {
		super(jobName, listener);
	}
	
	public void setZQL(String zql){
		this.zql = zql;
	}
	
	public String getZQL(){
		return zql;
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
		LinkedList<Result> results = new LinkedList<Result>();
		ZQL zqlEvaluator = new ZQL(zql);
		List<Z> zqlResult;
		zqlResult = zqlEvaluator.compile();

		
		for(Z zz : zqlResult){
			try {
				results.add(zz.execute().result());
			} catch (ZException e) {
				error = new Result(e);
				throw e;
			} 
		}

		return results;
	}

	@Override
	protected boolean jobAbort() {
		// TODO implement
		return false;
		
	}
	
}
