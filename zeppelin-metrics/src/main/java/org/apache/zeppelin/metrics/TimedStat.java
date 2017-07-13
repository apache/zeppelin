/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.metrics;

import java.util.Calendar;
import java.util.Date;

import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;
import org.apache.commons.lang3.time.DateUtils;
import org.weakref.jmx.Managed;

/**
 * A stat that contains a histogram distribution of values
 */
public class TimedStat implements Stat {
  public static final long MAX_MILLIS = 300000;

  private final Histogram histogram;

  private final Histogram oneMinuteHistogram;
  private long nextMinute;

  public TimedStat() {
    // metrics w/ upper bound of 5 minutes
    this.histogram = new ConcurrentHistogram(TimedStat.MAX_MILLIS, 5);
    this.oneMinuteHistogram = new ConcurrentHistogram(TimedStat.MAX_MILLIS, 5);
  }

  @Managed
  @Override
  public long getCount() {
    return histogram.getTotalCount();
  }

  @Managed
  public double getMaxMillis() {
    return histogram.getMaxValue();
  }

  @Managed
  public double getMeanMillis() {
    return histogram.getMean();
  }

  @Managed
  public double getP50Millis() {
    return histogram.getValueAtPercentile(50);
  }

  @Managed
  public double getP99Millis() {
    return histogram.getValueAtPercentile(99);
  }

  @Managed
  public double getMeanMillisOneMinute() {
    return oneMinuteHistogram.getMean();
  }

  @Managed
  public double getMaxMillisOneMinute() {
    return oneMinuteHistogram.getMaxValue();
  }

  @Managed
  public double getP50MillisOneMinute() {
    return oneMinuteHistogram.getValueAtPercentile(50);
  }

  @Managed
  public double getP99MillisOneMinute() {
    return oneMinuteHistogram.getValueAtPercentile(99);
  }

  @Managed
  @Override
  public void record(long duration) {
    histogram.recordValue(duration);

    if (System.currentTimeMillis() > nextMinute) {
      oneMinuteHistogram.reset();
      nextMinute = nearestMinute();
    }
    oneMinuteHistogram.recordValue(duration);
  }

  private long nearestMinute() {
    return DateUtils.round(new Date(), Calendar.MINUTE).getTime();
  }
}
