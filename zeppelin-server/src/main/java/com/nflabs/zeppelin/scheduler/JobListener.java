package com.nflabs.zeppelin.scheduler;

public interface JobListener {
	public void beforeStatusChange(Job job, Job.Status before, Job.Status after);
	public void afterStatusChange(Job job, Job.Status before, Job.Status after);
}
