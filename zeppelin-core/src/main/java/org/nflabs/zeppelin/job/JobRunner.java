package org.nflabs.zeppelin.job;

import java.util.logging.Logger;

import org.nflabs.zeppelin.driver.AbortException;
import org.nflabs.zeppelin.driver.Driver;
import org.nflabs.zeppelin.driver.DriverManager;

public class JobRunner implements Runnable{
	Logger logger = Logger.getLogger(JobRunner.class.getName());
	private String driverClass;
	private Job job;
	private JobResult result;
	private Exception e;
	
	boolean abort = false;
	
	public static enum Status{
		CREATED,
		RUNNING,
		FINISHED,
		ABORTED,
		ERROR
	}
	
	Status status;
	private DriverManager driverManager;
	private Driver driver;
	private JobRunnerListener listener;
	

	
	public JobRunner(Job job, String driverClass, DriverManager driverManager, JobRunnerListener listener) {
		this.job = job;
		this.driverClass = driverClass;
		this.driverManager = driverManager;
		this.listener = listener;
		status = Status.CREATED;
		if(listener!=null) listener.onStatusChange(job, this, Status.CREATED);
	}

	@Override
	public void run() {
		status = Status.RUNNING;		
		try {
			driver = driverManager.get(driverClass);
			if(listener!=null) listener.onStatusChange(job, this, Status.RUNNING);
			
			result = driver.execute(job);
			status = Status.FINISHED;
			if(listener!=null) listener.onStatusChange(job, this, Status.FINISHED);
		} catch(AbortException e){
			status = Status.ABORTED;
			if(listener!=null) listener.onStatusChange(job, this, Status.ABORTED);
		} catch(Exception e){
			this.e = e;
			status = Status.ERROR;
			if(listener!=null) listener.onStatusChange(job, this, Status.ERROR);
		} finally {
			synchronized(job){
				job.notifyAll();
			}
		}
	}
	
	public boolean isTerminated(){
		if(status==Status.FINISHED || status==Status.ABORTED || status==Status.ERROR){
			return true;
		} else {
			return false;
		}
	}
	
	public void abort(){
		if(status == Status.FINISHED || status == Status.ERROR){
			// job is already finished. can not abort.
			return;
		}
		if(driver!=null)
			driver.abort(job);
	}
	
	public JobResult getResult(){
		return result;
	}
	
	public Exception getException(){
		return e;
	}

	public String getDriverClass() {
		return driverClass;
	}

	public void setDriverClass(String driverClass) {
		this.driverClass = driverClass;
	}

	public Job getJob() {
		return job;
	}

	public void setJob(Job job) {
		this.job = job;
	}

	public DriverManager getDriverManager() {
		return driverManager;
	}

	public void setDriverManager(DriverManager driverManager) {
		this.driverManager = driverManager;
	}

	public Driver getDriver() {
		return driver;
	}

	public void setDriver(Driver driver) {
		this.driver = driver;
	}

	public void setResult(JobResult result) {
		this.result = result;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}
	
	
}
