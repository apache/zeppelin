package org.apache.zeppelin.metrics;

import org.weakref.jmx.Managed;

/**
 * A stat that contains a histogram distribution of values
 */
public class FailureStat implements Stat {
  private final Exception exception;
  private long count;

  public FailureStat(MetricType type, Exception ex) {
    this.exception = ex;
  }

  @Managed
  @Override
  public long getCount() {
    return count;
  }

  @Managed
  @Override
  public void record(long q) {
    setCount(count + q);
  }

  @Managed
  public Exception getException() {
    return exception;
  }

  @Managed
  public void setCount(long count) {
    this.count = count;
  }
}
