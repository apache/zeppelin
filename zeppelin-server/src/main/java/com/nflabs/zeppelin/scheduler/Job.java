package com.nflabs.zeppelin.scheduler;

import java.util.Date;
import java.util.Map;

import com.nflabs.zeppelin.scheduler.JobListener;

public abstract class Job {
	public static enum Status{
		READY,
		RUNNING,
		FINISHED,
		ERROR,
		ABORT,
	}
	
	private String jobName;
	Status status;
	transient private Exception exception;
	Object ret;
	boolean aborted = false;
	transient private JobListener listener;
	String id;
	
	Date dateCreated;
	Date dateStarted;
	Date dateFinished;
	
	public Job(String jobName, JobListener listener) {
		this.jobName = jobName;
		this.listener = listener;
		status = Status.READY;
		id = System.currentTimeMillis()+"_"+hashCode();
		dateCreated = new Date();
	}
	
	public String getId(){
		return id;
	}
	
	public Status getStatus(){
		return status;
	}

	private void setStatus(Status status){
		if(this.status==status) return;
		this.status = status;
		if(listener!=null) listener.statusChange(this);
	}
	
	public boolean isTerminated(){
		if(status==Status.READY || status==Status.RUNNING) return false;
		else return true;
	}
	
	public boolean isRunning(){
		if(status==Status.RUNNING) return true;
		else return false;
	}
	
	public void run(){
		if(aborted==true){
			setStatus(Status.ABORT);
			return;
		}
		try{
			setStatus(Status.RUNNING);
			dateStarted = new Date();
			ret = jobRun();			
			if(aborted==true){
				setStatus(Status.ABORT);
			} else {
				setStatus(Status.FINISHED);
			}
			dateFinished = new Date();
		}catch(Exception e){
			this.exception = e;
			setStatus(Status.ERROR);
			dateFinished = new Date();
		}
	}
	
	public void abort(){
		aborted = true;
		jobAbort();
	}
	
	public Exception getException(){
		return exception;
	}
	
	public Object getReturn(){
		return ret;
	}
	
	public String getJobName() {
		return jobName;
	}

	public void setJobName(String jobName) {
		this.jobName = jobName;
	}
	
	public abstract int progress();

	public abstract Map<String, Object> info();
	
	protected abstract Object jobRun() throws Exception;	

	protected abstract void jobAbort();
	
}