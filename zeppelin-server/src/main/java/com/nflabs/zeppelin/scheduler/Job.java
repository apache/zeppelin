package com.nflabs.zeppelin.scheduler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import com.nflabs.zeppelin.scheduler.JobListener;

/**
 * Skeletal implementation of the Job concept:
 *  - designed for inheritance
 *  - should be run on a separate thread
 *  - maintains internal state: it's status
 *  - supports listeners who are updated on status change
 *  
 */
public abstract class Job {
    //TODO(alex): make Job interface and AbstractJob - skeletal impl
	public static enum Status {
		READY,
		RUNNING,
		FINISHED,
		ERROR,
		ABORT,;
        boolean isReady() { return this==RUNNING; }
        boolean isRunning() { return this==RUNNING; }
	}

    private String jobName;
    String id;
    Object result;
    Date dateCreated;
    Date dateStarted;
    Date dateFinished;
    Status status;
    //TODO(alex): why do we keep this state if we already have Status?
    boolean jobWasAborted = false;
	
	transient private Throwable lastException;
	transient private JobListener listener;
	
	public Job(String jobName, JobListener listener) {
		this.jobName = jobName;
		this.listener = listener;
		
		dateCreated = new Date();		
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
		id = dateFormat.format(dateCreated)+"_"+super.hashCode();
		
		setStatus(Status.READY);
	}
	
	public String getId(){
		return id;
	}
	
	public int hashCode(){
		return id.hashCode();
	}
	
	public boolean equals(Object o){
		return ((Job)o).hashCode()==hashCode();
	}
	
	public Status getStatus(){
		return status;
	}
	
	public void setStatus(Status status){
	    if(this.status==status) return;
	    Status before = this.status;
	    Status after = status;
	    if (listener!=null) listener.beforeStatusChange(this, before, after);
	    this.status = status;
	    if (listener!=null) listener.afterStatusChange(this, before, after);
	}
	
	public void setListener(JobListener listener){
		this.listener = listener;
	}
	
	public JobListener getListener(){
		return listener;
	}
		
	public boolean isTerminated(){
		return !this.status.isReady() && !this.status.isRunning(); 
	}
	
	public boolean isRunning(){
		return this.status.isRunning();
	}
	
	public void run(){
		if(jobWasAborted){
			setStatus(Status.ABORT);
			return;
		}
		try{
			setStatus(Status.RUNNING);
			dateStarted = new Date();
			result = jobRun();
			dateFinished = new Date();
			if(jobWasAborted){				
				setStatus(Status.ABORT);
			} else {
				setStatus(Status.FINISHED);
			}			
		}catch(Throwable e){
			this.lastException = e;
			dateFinished = new Date();			
			setStatus(Status.ERROR);
		}
	}
		
	public Throwable getException(){
		return lastException;
	}
	
	protected void setException(Throwable t){
		lastException = t;
	}
	
	public Object getReturn(){
		return result;
	}
	
	public String getJobName() {
		return jobName;
	}

	public void setJobName(String jobName) {
		this.jobName = jobName;
	}
	
	public abstract int progress();

	public abstract Map<String, Object> info();
	
	protected abstract Object jobRun() throws Throwable;	

	protected abstract boolean jobAbort();

	public void abort() {
	    jobWasAborted = jobAbort();
	}

	public boolean isAborted() {
		return jobWasAborted;
		//TODO(alex) why not this.status.isAborted()?
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
