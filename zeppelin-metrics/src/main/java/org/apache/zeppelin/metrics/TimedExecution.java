package org.apache.zeppelin.metrics;

/**
 * Created by hfreire on 6/30/17.
 */
public class TimedExecution {
  private final MetricType type;
  private long start;
  private long finish;

  public TimedExecution(MetricType type) {
    start = System.currentTimeMillis();
    this.type = type;
  }

  public void finish() {
    this.finish = System.currentTimeMillis();
  }

  public MetricType getMetricType() {
    return type;
  }

  public long getDuration() {
    return finish - start;
  }
}
