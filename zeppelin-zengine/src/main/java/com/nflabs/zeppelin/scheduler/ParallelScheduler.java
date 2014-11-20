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
public class ParallelScheduler implements Scheduler {
  List<Job> queue = new LinkedList<Job>();
  List<Job> running = new LinkedList<Job>();
  private ExecutorService executor;
  private SchedulerListener listener;
  boolean terminate = false;
  private String name;
  private int maxConcurrency;

  public ParallelScheduler(String name, ExecutorService executor, SchedulerListener listener,
      int maxConcurrency) {
    this.name = name;
    this.executor = executor;
    this.listener = listener;
    this.maxConcurrency = maxConcurrency;
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
    synchronized (queue) {
      for (Job job : running) {
        ret.add(job);
      }
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
        if (running.size() >= maxConcurrency || queue.isEmpty() == true) {
          try {
            queue.wait(500);
          } catch (InterruptedException e) {
          }
          continue;
        }

        Job job = queue.remove(0);
        running.add(job);
        Scheduler scheduler = this;

        executor.execute(new JobRunner(scheduler, job));
      }


    }
  }

  public void setMaxConcurrency(int maxConcurrency) {
    this.maxConcurrency = maxConcurrency;
    synchronized (queue) {
      queue.notify();
    }
  }

  private class JobRunner implements Runnable {
    private Scheduler scheduler;
    private Job job;

    public JobRunner(Scheduler scheduler, Job job) {
      this.scheduler = scheduler;
      this.job = job;
    }

    public void run() {
      if (listener != null) {
        listener.jobStarted(scheduler, job);
      }
      job.run();
      if (listener != null) {
        listener.jobFinished(scheduler, job);
      }
      synchronized (queue) {
        running.remove(job);
        queue.notify();
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
