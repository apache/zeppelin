package com.nflabs.zeppelin.scheduler;

import java.text.SimpleDateFormat;
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
		
		dateCreated = new Date();		
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
		id = dateFormat.format(dateCreated)+"_"+hashCode();
		
		setStatus(Status.READY);
	}
	
	public String getId(){
		return id;
	}
	
	public Status getStatus(){
		return status;
	}
	
	public void setStatus(Status status){
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
			dateFinished = new Date();
			if(aborted==true){				
				setStatus(Status.ABORT);
			} else {
				setStatus(Status.FINISHED);
			}			
		}catch(Exception e){
			this.exception = e;
			dateFinished = new Date();
			setStatus(Status.ERROR);
		}
	}
	
	public void abort(){
		aborted = jobAbort();
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

	protected abstract boolean jobAbort();

	public boolean isAborted() {
		return aborted;
	}

	public Date getDateCreated() {
		return dateCreated;
	}

	public Date getDateStarted() {
		return dateStarted;
	}

	public Date getDateFinished() {
		return dateFinished;
	}
	
	
}