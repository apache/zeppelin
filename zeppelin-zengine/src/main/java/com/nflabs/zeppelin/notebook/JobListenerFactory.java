package com.nflabs.zeppelin.notebook;

import com.nflabs.zeppelin.scheduler.JobListener;

/**
 * TODO(moon): provide description.
 * 
 * @author Leemoonsoo
 *
 */
public interface JobListenerFactory {
  public JobListener getParagraphJobListener(Note note);
}
