package com.nflabs.zeppelin.visualizer;

import com.nflabs.zeppelin.job.JobResult;

public interface Visualizer {
	public String render(JobResult jobResult);
}
