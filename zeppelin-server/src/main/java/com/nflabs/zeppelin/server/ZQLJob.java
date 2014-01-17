package com.nflabs.zeppelin.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.result.ResultDataException;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.zengine.ZException;
import com.nflabs.zeppelin.zengine.ZQLException;
import com.nflabs.zeppelin.zengine.Zengine;
import com.nflabs.zeppelin.zengine.api.Z;
import com.nflabs.zeppelin.zengine.api.ZQL;

public class ZQLJob extends Job {
	private String zql;
	private List<Map<String, Object>> params;
	Result error;
	String cron;

	private List<Z> zqlPlans = Collections.emptyList();
    private Zengine z;

	//FIXME transient private ZeppelinConnection conn;
	
	public ZQLJob(String jobName, JobListener listener) {
		super(jobName, listener);
	}
	
	private Logger logger(){
		return LoggerFactory.getLogger(ZQLJob.class);
	}
	
	public ZQLJob clone(){
		// clone object using gson
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		gsonBuilder.registerTypeAdapter(Z.class, new ZAdapter());

		Gson gson = gsonBuilder.create();
		String jsonstr = gson.toJson(this);
		ZQLJob job = gson.fromJson(jsonstr, ZQLJob.class);
		
		// set transient values
		//job.conn = conn;
		job.setListener(getListener());
		job.setException(getException());
		return job;
	}
	
	public void setZQL(String zql){
		this.zql = zql;
		//TODO(moon): possible optimization - update current plan
		zqlPlans = Collections.emptyList();
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
		return Collections.emptyMap();
	}
	
	public void dryRun() throws ZException, ZQLException{
		if(getStatus()!=Status.READY) return;
		
		if(zqlPlans.isEmpty()){
			ZQL zqlEvaluator = new ZQL(zql, z);
			zqlPlans = zqlEvaluator.compile();
		} else {
			reconstructNextReference();
		}
		
		for(Z zz : zqlPlans){
			zz.dryRun();
		}
	}

	@Override
	protected Object jobRun() throws ZQLException, ZException, ResultDataException {
		LinkedList<Result> results = new LinkedList<Result>();
		ZQL zqlEvaluator = new ZQL(zql, z);
		zqlPlans = zqlEvaluator.compile();
		
		synchronized(this){
			//FIXME conn = Z.getConnection();
		}
		
		for(int i=0; i<zqlPlans.size(); i++){
			Z zz = zqlPlans.get(i);
			Map<String, Object> p = new HashMap<String, Object>();
			if(params!=null && params.size()>=i+1){
				p = params.get(i);
			}
			try {
				zz.withParams(p);
				
				zz.execute();//FIXME was .execute(conn)
				
				results.add(zz.result());
				zz.release();
			} catch (ZException e) {
				error = new Result(e);
				
				//FIXME conn.close();
				synchronized(this){					
					//FIXME conn = null;
				}
				throw e;
			}
		}
		
		//FIXME conn.close();
		synchronized(this){					
			//FIXME conn = null;
		}
		
		return results;
	}

	@Override
	protected boolean jobAbort() {
		boolean result = true;
		synchronized(this) {
		    for (Z zz : zqlPlans) {
		        result = zz.abort();
		        if (!result) break;
		    }
		}
		return result;
	}

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

}
