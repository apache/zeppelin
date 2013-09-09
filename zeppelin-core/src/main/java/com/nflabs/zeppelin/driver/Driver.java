package com.nflabs.zeppelin.driver;

import com.nflabs.zeppelin.job.Job;
import com.nflabs.zeppelin.job.JobResult;

public interface Driver {
	/**
	 * Friendly name
	 * @return
	 */
	public String name();
	public JobResult execute(Job job) throws AbortException;
	public void init();
	public void terminate();
	public void abort(Job job);
	public Progress progress(Job jobe);
}
