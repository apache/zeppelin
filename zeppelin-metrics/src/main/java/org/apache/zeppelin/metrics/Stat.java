package org.apache.zeppelin.metrics;

/**
 * Base attributes any JMX stat will expose: for now, only count is required. Some stats
 * may include execution times (eg. p99 latency) - see TimedStat for those
 */
public interface Stat {
  long getCount();
  void record(long amount);
}
