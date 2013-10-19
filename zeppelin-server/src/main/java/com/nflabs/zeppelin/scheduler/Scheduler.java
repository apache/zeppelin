package com.nflabs.zeppelin.scheduler;

import java.util.Collection;

public interface Scheduler  extends Runnable{
	public String getName();
	public Collection<Job> getJobsWaiting();
	public Collection<Job> getJobsRunning();
	public void submit(Job job);
	public void stop();
}
