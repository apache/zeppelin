package com.nflabs.zeppelin.server;

import java.sql.ResultSet;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.nflabs.zeppelin.result.ResultDataObject;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.zengine.Z;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.ZQL;

public class ZQLSession extends Job{
	transient Logger logger = Logger.getLogger(ZQLSession.class);

	private String zql;
	List<ResultDataObject> results;

	public ZQLSession(String jobName, JobListener listener) {
		super(jobName, listener);
	}
	
	public void setZQL(String zql){
		this.zql = zql;
	}
	
	public String getZQL(){
		return zql;
	}
	
	public List<ResultDataObject> getResults(){
		return results;
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
	protected Object jobRun() throws Exception {
		
		ZQL zqlEvaluator = new ZQL(zql);
		List<Z> zqlResult = null;
		zqlResult = zqlEvaluator.compile();

		
		for(Z zz : zqlResult){
			List<ResultSet> res;
			try {
				res = zz.execute();
				for(ResultSet r : res){
					results.add(new ResultDataObject(r));
				}
			} catch (ZException e) {
				results.add(new ResultDataObject(e));
			} 
		}

		return results;
	}

	@Override
	protected void jobAbort() {
		// TODO implement
		
	}
	
}
