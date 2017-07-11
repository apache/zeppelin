package org.apache.zeppelin.metrics;

import org.weakref.jmx.Managed;

/**
 * A stat keeping simple counters only
 */
public class CounterStat implements Stat {
  private long count;

  public CounterStat() {
  }

  @Override
  @Managed
  public long getCount() {
    return count;
  }

  @Override
  @Managed
  public void record(long amount) {
    setCount(count + amount);
  }

  @Managed
  private void setCount(long count) {
    this.count = count;
  }
}
