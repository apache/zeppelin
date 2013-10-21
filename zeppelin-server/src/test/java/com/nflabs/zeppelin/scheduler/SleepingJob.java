package com.nflabs.zeppelin.scheduler;

import java.util.HashMap;
import java.util.Map;

public class SleepingJob extends Job{
	
	private int time;
	boolean abort = false;
	private long start;
	private int count;
	
	
	public SleepingJob(String jobName, JobListener listener, int time){
		super(jobName, listener);
		this.time = time;
		count = 0;
	}
	public Object jobRun() {
		start = System.currentTimeMillis();
		while(abort==false){
			count++;
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
			if(System.currentTimeMillis() - start>time) break;
		}
		return System.currentTimeMillis()-start;
	}

	public boolean jobAbort() {
		abort = true;
		return true;
	}

	public int progress() {
		long p = (System.currentTimeMillis() - start)*100 / time;
		if(p<0) p = 0;
		if(p>100) p = 100;
		return (int) p;
	}

	public Map<String, Object> info() {
		Map<String, Object> i = new HashMap<String, Object>();
		i.put("LoopCount", Integer.toString(count));
		return i;
	}


}
