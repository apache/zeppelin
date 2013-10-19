package com.nflabs.zeppelin.scheduler;

public interface JobListener {
	public void statusChange(Job job);
}
