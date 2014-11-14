package com.nflabs.zeppelin.notebook;

import com.nflabs.zeppelin.scheduler.JobListener;

public interface JobListenerFactory {
	public JobListener getParagraphJobListener(Note note); 
}
