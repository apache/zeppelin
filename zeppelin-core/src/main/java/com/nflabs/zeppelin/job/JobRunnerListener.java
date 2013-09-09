package com.nflabs.zeppelin.job;



public interface JobRunnerListener {
	public void onStatusChange(Job job, JobRunner jobRunner, JobRunner.Status status);
}
