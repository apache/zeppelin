package com.nflabs.zeppelin.server;

import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.scheduler.Job.Status;

public class AnalyzeSessionManager implements JobListener {
	public TreeMap<String, AnalyzeSession> sessions = new TreeMap<String, AnalyzeSession>();
	AtomicLong counter = new AtomicLong(0);
	private Scheduler scheduler;
	
	public AnalyzeSessionManager(Scheduler scheduler){
		this.scheduler = scheduler;
	}
	
	public AnalyzeSession create(){
		AnalyzeSession as = new AnalyzeSession("", this);
		
		synchronized(sessions){
			sessions.put(as.getId(), as);
		}
		
		return as;
	}
	
	public AnalyzeSession get(String sessionId){
		synchronized(sessions){
			if(sessions.containsKey(sessionId)){
				return sessions.get(sessionId);
			} else {
				return null;
			}
		}
	}
	
	public AnalyzeSession run(String sessionId){		
		AnalyzeSession s = get(sessionId);
		if(s==null) return null;
		scheduler.submit(s);
		return s;
	}
	
	
	public AnalyzeSession setZql(String sessionId, String zql){
		AnalyzeSession s = get(sessionId);
		if(s==null){
			return null;
		} else {
			s.setZQL(zql);
			return s;
		}
	}

	public boolean discard(String sessionId){
		AnalyzeSession s= get(sessionId);
		if(s==null) return false;
		
		abort(sessionId);
		synchronized(sessions){
			sessions.remove(sessionId);
		}
		return true;
	}
	
	public AnalyzeSession abort(String sessionId){
		AnalyzeSession s= get(sessionId);
		if(s==null) return null;
		
		s.abort();
		return s;
	}

	@Override
	public void statusChange(Job job) {
		if(job.getStatus()==Status.FINISHED ||
		   job.getStatus()==Status.ERROR ||
		   job.getStatus()==Status.ABORT){
			synchronized(sessions){
				sessions.remove(job.getId());
			}
			
			// add to history;
		}
		
	}
	
	
}
