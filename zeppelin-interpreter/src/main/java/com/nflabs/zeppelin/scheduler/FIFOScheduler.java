package com.nflabs.zeppelin.scheduler;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.nflabs.zeppelin.scheduler.Job.Status;

/**
 * TODO(moon) : add description.
 * 
 * @author Leemoonsoo
 *
 */
public class FIFOScheduler implements Scheduler {
  List<Job> queue = new LinkedList<Job>();
  private ExecutorService executor;
  private SchedulerListener listener;
  boolean terminate = false;
  Job runningJob = null;
  private String name;

  public FIFOScheduler(String name, ExecutorService executor, SchedulerListener listener) {
    this.name = name;
    this.executor = executor;
    this.listener = listener;
  }

  public String getName() {
    return name;
  }

  public Collection<Job> getJobsWaiting() {
    List<Job> ret = new LinkedList<Job>();
    synchronized (queue) {
      for (Job job : queue) {
        ret.add(job);
      }
    }
    return ret;
  }

  public Collection<Job> getJobsRunning() {
    List<Job> ret = new LinkedList<Job>();
    Job job = runningJob;

    if (job != null) {
      ret.add(job);
    }

    return ret;
  }



  public void submit(Job job) {
    job.setStatus(Status.PENDING);
    synchronized (queue) {
      queue.add(job);
      queue.notify();
    }
  }

  public void run() {

    synchronized (queue) {
      while (terminate == false) {
        if (runningJob != null || queue.isEmpty() == true) {
          try {
            queue.wait(500);
          } catch (InterruptedException e) {
          }
          continue;
        }

        runningJob = queue.remove(0);

        final Scheduler scheduler = this;
        this.executor.execute(new Runnable() {
          public void run() {
            if (listener != null) {
              listener.jobStarted(scheduler, runningJob);
            }
            runningJob.run();
            if (listener != null) {
              listener.jobFinished(scheduler, runningJob);
            }
            runningJob = null;
            synchronized (queue) {
              queue.notify();
            }
          }
        });
      }
    }
  }

  public void stop() {
    terminate = true;
    synchronized (queue) {
      queue.notify();
    }
  }

}
