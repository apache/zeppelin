package org.apache.zeppelin.driver;

import org.apache.zeppelin.job.Job;
import org.apache.zeppelin.job.JobResult;

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
