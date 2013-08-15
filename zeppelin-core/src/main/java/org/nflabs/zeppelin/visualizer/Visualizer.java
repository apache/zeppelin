package org.nflabs.zeppelin.visualizer;

import org.nflabs.zeppelin.job.JobResult;

public interface Visualizer {
	public String render(JobResult jobResult);
}
