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

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Collection<Job> getJobsWaiting() {
    List<Job> ret = new LinkedList<Job>();
    synchronized (queue) {
      for (Job job : queue) {
        ret.add(job);
      }
    }
    return ret;
  }

  @Override
  public Collection<Job> getJobsRunning() {
    List<Job> ret = new LinkedList<Job>();
    synchronized (queue) {
      for (Job job : running) {
        ret.add(job);
      }
    }
    return ret;
  }



  @Override
  public void submit(Job job) {
    job.setStatus(Status.PENDING);
    synchronized (queue) {
      queue.add(job);
      queue.notify();
    }
  }

  @Override
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

    @Override
    public void run() {
      if (job.isAborted()) {
        job.setStatus(Status.ABORT);
        job.aborted = false;

        synchronized (queue) {
          running.remove(job);
          queue.notify();
        }

        return;
      }

      job.setStatus(Status.RUNNING);
      if (listener != null) {
        listener.jobStarted(scheduler, job);
      }
      job.run();
      if (job.isAborted()) {
        job.setStatus(Status.ABORT);
      } else {
        if (job.getException() != null) {
          job.setStatus(Status.ERROR);
        } else {
          job.setStatus(Status.FINISHED);
        }
      }

      if (listener != null) {
        listener.jobFinished(scheduler, job);
      }

      // reset aborted flag to allow retry
      job.aborted = false;
      synchronized (queue) {
        running.remove(job);
        queue.notify();
      }
    }
  }


  @Override
  public void stop() {
    terminate = true;
    synchronized (queue) {
      queue.notify();
    }
  }

}
