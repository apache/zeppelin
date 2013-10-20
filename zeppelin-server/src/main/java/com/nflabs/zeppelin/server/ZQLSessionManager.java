package com.nflabs.zeppelin.server;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.scheduler.Job.Status;

public class ZQLSessionManager implements JobListener {
	public TreeMap<String, ZQLSession> sessions = new TreeMap<String, ZQLSession>();
	
	AtomicLong counter = new AtomicLong(0);
	private Scheduler scheduler;
	
	public ZQLSessionManager(Scheduler scheduler){
		this.scheduler = scheduler;
	}
	
	public ZQLSession create(){
		ZQLSession as = new ZQLSession("", this);
		
		synchronized(sessions){
			sessions.put(as.getId(), as);
		}
		
		return as;
	}
	
	public ZQLSession get(String sessionId){
		synchronized(sessions){
			if(sessions.containsKey(sessionId)){
				return sessions.get(sessionId);
			} else {
				return null;
			}
		}
	}
	
	public ZQLSession run(String sessionId){		
		ZQLSession s = get(sessionId);
		if(s==null) return null;
		scheduler.submit(s);
		return s;
	}
	
	
	public ZQLSession setZql(String sessionId, String zql){
		ZQLSession s = get(sessionId);
		if(s==null){
			return null;
		} else {
			s.setZQL(zql);
			return s;
		}
	}

	public boolean discard(String sessionId){
		ZQLSession s= get(sessionId);
		if(s==null) return false;
		
		abort(sessionId);
		synchronized(sessions){
			sessions.remove(sessionId);
		}
		return true;
	}
	
	public ZQLSession abort(String sessionId){
		ZQLSession s= get(sessionId);
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
			
			// TODO persist
		}
		
	}

	public Map<String, ZQLSession> getRunning() {
		return (Map<String, ZQLSession>) sessions.clone();
	}
	
	private void persist(ZQLSession sess){
		
	}
}
