package org.apache.zeppelin.job;

import org.apache.zeppelin.driver.Progress;
import org.apache.zeppelin.job.JobRunner.Status;

public class JobInfo {
	Job job;
	JobRunner.Status status;
	Progress progress;
	String driverClass;
	JobResult result;
	
	
	public JobInfo(Job job, Status status, Progress progress, String driverClass, JobResult result) {
		super();
		this.job = job;
		this.status = status;
		this.progress = progress;
		this.driverClass = driverClass;
		this.result = result;
	}
	public Job getJob() {
		return job;
	}
	public void setJob(Job job) {
		this.job = job;
	}
	public JobRunner.Status getStatus() {
		return status;
	}
	public void setStatus(JobRunner.Status status) {
		this.status = status;
	}
	public Progress getProgress() {
		return progress;
	}
	public void setProgress(Progress progress) {
		this.progress = progress;
	}
	public String getDriverClass() {
		return driverClass;
	}
	public void setDriverClass(String driverClass) {
		this.driverClass = driverClass;
	}
	public JobResult getResult() {
		return result;
	}
	public void setResult(JobResult result) {
		this.result = result;
	}
	
	
}
