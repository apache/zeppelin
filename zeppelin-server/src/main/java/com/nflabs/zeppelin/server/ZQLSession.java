package com.nflabs.zeppelin.server;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriverException;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.zengine.Z;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.ZQL;
import com.nflabs.zeppelin.zengine.ZQLException;

public class ZQLSession extends Job{
	private String zql;
	private List<Map<String, Object>> params;
	Result error;
	String cron;

	private List<Z> zqlPlans;

	transient private ZeppelinConnection conn;
	
	public ZQLSession(String jobName, JobListener listener) {
		super(jobName, listener);
	}
	
	private Logger logger(){
		return LoggerFactory.getLogger(ZQLSession.class);
	}
	
	public void setZQL(String zql){
		this.zql = zql;
		// later we can improve this part. to make it modify current plan.
		zqlPlans = null;
		setStatus(Status.READY);
	}
	

	public void setParams(List<Map<String, Object>> params) {
		this.params = params;
	}
	
	public List<Z> getPlan(){
		reconstructNextReference();
		return zqlPlans;
	}
	
	public String getZQL(){
		return zql;
	}

	private void reconstructNextReference(){
		if(zqlPlans==null) return;
		// reconstruct plan link. in case of restored by gson
		for(Z z : zqlPlans){
			Z next = null;
			for(Z c=z; c!=null; c=c.prev()){
				c.setNext(next);
				next = c;
			}
		}
	}
	
	@Override
	public int progress() {
		return 0;
	}

	@Override
	public Map<String, Object> info() {
		return new HashMap<String, Object>();
	}
	
	public void dryRun() throws ZException, ZQLException{
		if(getStatus()!=Status.READY) return;
		
		if(zqlPlans==null){
			ZQL zqlEvaluator = new ZQL(zql);
			zqlPlans = zqlEvaluator.compile();
		} else {
			reconstructNextReference();
		}
		
		for(Z zz : zqlPlans){
			zz.dryRun();
		}
	}

	@Override
	protected Object jobRun() throws Throwable {
		LinkedList<Result> results = new LinkedList<Result>();
		ZQL zqlEvaluator = new ZQL(zql);
		zqlPlans = zqlEvaluator.compile();

		/*
		if(zqlPlans==null){
			ZQL zqlEvaluator = new ZQL(zql);
			zqlPlans = zqlEvaluator.compile();
		} else {
			reconstructNextReference();
		}*/
		
		synchronized(this){
			conn = Z.getConnection();
		}
		
		for(int i=0; i<zqlPlans.size(); i++){
			Z zz = zqlPlans.get(i);
			Map<String, Object> p = new HashMap<String, Object>();
			if(params!=null && params.size()>=i+1){
				p = params.get(i);
			}
			try {
				zz.withParams(p);
				
				zz.execute(conn);
				
				results.add(zz.result());
				zz.release();
			} catch (ZException e) {
				error = new Result(e);
				
				conn.close();
				synchronized(this){					
					conn = null;
				}
				throw e;
			}
		}
		
		conn.close();
		synchronized(this){					
			conn = null;
		}
		
		return results;
	}

	@Override
	protected boolean jobAbort() {
		synchronized(this){
			if(conn!=null){
				try {
					conn.abort();
					return true;
				} catch (ZeppelinDriverException e) {
					logger().error("Abort failure", e);
					return false;
				}
			} else {
				return true;
			}
		}
	}

	public String getCron() {
		return cron;
	}

	public void setCron(String cron) {
		this.cron = cron;
	}

	
}
