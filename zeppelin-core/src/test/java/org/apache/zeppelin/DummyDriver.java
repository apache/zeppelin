package org.apache.zeppelin;

import org.apache.zeppelin.driver.AbortException;
import org.apache.zeppelin.driver.Driver;
import org.apache.zeppelin.driver.Progress;
import org.apache.zeppelin.job.Job;
import org.apache.zeppelin.job.JobResult;

public class DummyDriver implements Driver{

	boolean abort = false;
	private int to;
	int progress = 0;
	
	@Override
	public String name() {
		return "test";
	}

	@Override
	public JobResult execute(Job job) throws AbortException {
		long start = System.currentTimeMillis();
		while(System.currentTimeMillis() - start < to){
			progress = (int) ((System.currentTimeMillis() - start)*100 / to);
			if(abort == true) throw new AbortException();
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return new JobResult("hello", false);
	}

	@Override
	public void init() {
		this.to = 1000;
	}

	@Override
	public void terminate() {
	}

	@Override
	public void abort(Job job) {
		abort = true;
	}

	@Override
	public Progress progress(Job jobe) {
		return new Progress(progress, "progress");
	}

}
