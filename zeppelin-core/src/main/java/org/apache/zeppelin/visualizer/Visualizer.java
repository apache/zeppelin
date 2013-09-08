package org.apache.zeppelin.visualizer;

import org.apache.zeppelin.job.JobResult;

public interface Visualizer {
	public String render(JobResult jobResult);
}
