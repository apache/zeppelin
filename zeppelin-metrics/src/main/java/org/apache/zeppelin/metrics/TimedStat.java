package org.apache.zeppelin.metrics;

import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;
import org.weakref.jmx.Managed;

/**
 * A stat that contains a histogram distribution of values
 */
public class TimedStat implements Stat {
  public static final long MAX_MILLIS = 300000;

  private final Histogram histogram;
  private double maxValue;
  private double p99Millis;
  private double meanMillis;
  private double p50Millis;

  public TimedStat() {
    // metrics w/ upper bound of 5 minutes
    this.histogram = new ConcurrentHistogram(TimedStat.MAX_MILLIS, 5);
  }

  @Managed
  @Override
  public long getCount() {
    return histogram.getTotalCount();
  }

  @Managed
  public double getMaxMillis() {
    return maxValue;
  }

  @Managed
  public double getP99Millis() {
    return p99Millis;
  }

  @Managed
  @Override
  public void record(long duration) {
    histogram.recordValue(duration);

    setMaxValue(histogram.getMaxValueAsDouble());
    setP99Millis(histogram.getValueAtPercentile(99));
    setP50Millis(histogram.getValueAtPercentile(50));
    setMeanMillis(histogram.getMean());
  }

  @Managed
  private void setMaxValue(double maxValue) {
    this.maxValue = maxValue;
  }

  @Managed
  private void setP99Millis(long p99Millis) {
    this.p99Millis = p99Millis;
  }

  @Managed
  public double getMeanMillis() {
    return meanMillis;
  }

  @Managed
  private void setMeanMillis(double meanMillis) {
    this.meanMillis = meanMillis;
  }

  @Managed
  public double getP50Millis() {
    return p50Millis;
  }

  @Managed
  void setP50Millis(long p50Millis) {
    this.p50Millis = p50Millis;
  }
}
