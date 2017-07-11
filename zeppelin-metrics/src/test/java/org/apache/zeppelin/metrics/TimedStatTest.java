package org.apache.zeppelin.metrics;

import org.junit.Test;

import static org.junit.Assert.*;

public class TimedStatTest {

  @Test
  public void recording() throws InterruptedException {
    TimedStat metric = new TimedStat();
    for (int i = 0; i < 2000; i++) {
      metric.record(i);
    }

    assertEquals(999.5, metric.getMeanMillis(), 0.01);
    assertEquals(999.0, metric.getP50Millis(), 0.01);
    assertEquals(1979.0, metric.getP99Millis(), 0.01);
    assertEquals(1999.0, metric.getMaxMillis(), 0.01);
    assertEquals(2000, metric.getCount());
  }

}
