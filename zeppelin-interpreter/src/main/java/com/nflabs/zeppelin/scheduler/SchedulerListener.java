package com.nflabs.zeppelin.scheduler;

/**
 * TODO(moon) : add description.
 * 
 * @author Leemoonsoo
 *
 */
public interface SchedulerListener {
  public void jobStarted(Scheduler scheduler, Job job);

  public void jobFinished(Scheduler scheduler, Job job);
}
