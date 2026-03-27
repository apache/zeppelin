/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.flink;

import java.sql.Time;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.TimeZone;

/**
 * Copied from flink-project with minor modification.
 * */
public class TimestampStringUtils {

  private static final TimeZone LOCAL_TZ = TimeZone.getDefault();

  public TimestampStringUtils() {
  }

  public static String timestampToString(LocalDateTime ldt, int precision) {
    String fraction;
    for(fraction = pad(9, (long)ldt.getNano()); fraction.length() > precision && fraction.endsWith("0"); fraction = fraction.substring(0, fraction.length() - 1)) {
    }

    StringBuilder ymdhms = ymdhms(new StringBuilder(), ldt.getYear(), ldt.getMonthValue(), ldt.getDayOfMonth(), ldt.getHour(), ldt.getMinute(), ldt.getSecond());
    if (fraction.length() > 0) {
      ymdhms.append(".").append(fraction);
    }

    return ymdhms.toString();
  }

  private static String pad(int length, long v) {
    StringBuilder s = new StringBuilder(Long.toString(v));

    while(s.length() < length) {
      s.insert(0, "0");
    }

    return s.toString();
  }

  private static StringBuilder hms(StringBuilder b, int h, int m, int s) {
    int2(b, h);
    b.append(':');
    int2(b, m);
    b.append(':');
    int2(b, s);
    return b;
  }

  private static StringBuilder ymdhms(StringBuilder b, int year, int month, int day, int h, int m, int s) {
    ymd(b, year, month, day);
    b.append(' ');
    hms(b, h, m, s);
    return b;
  }

  private static StringBuilder ymd(StringBuilder b, int year, int month, int day) {
    int4(b, year);
    b.append('-');
    int2(b, month);
    b.append('-');
    int2(b, day);
    return b;
  }

  private static void int4(StringBuilder buf, int i) {
    buf.append((char)(48 + i / 1000 % 10));
    buf.append((char)(48 + i / 100 % 10));
    buf.append((char)(48 + i / 10 % 10));
    buf.append((char)(48 + i % 10));
  }

  private static void int2(StringBuilder buf, int i) {
    buf.append((char)(48 + i / 10 % 10));
    buf.append((char)(48 + i % 10));
  }

  public static String unixTimeToString(int time) {
    StringBuilder buf = new StringBuilder(8);
    unixTimeToString(buf, time, 0);
    return buf.toString();
  }

  private static void unixTimeToString(StringBuilder buf, int time, int precision) {
    while(time < 0) {
      time = (int)((long)time + 86400000L);
    }

    int h = time / 3600000;
    int time2 = time % 3600000;
    int m = time2 / '\uea60';
    int time3 = time2 % '\uea60';
    int s = time3 / 1000;
    int ms = time3 % 1000;
    int2(buf, h);
    buf.append(':');
    int2(buf, m);
    buf.append(':');
    int2(buf, s);
    if (precision > 0) {
      buf.append('.');

      while(precision > 0) {
        buf.append((char)(48 + ms / 100));
        ms %= 100;
        ms *= 10;
        if (ms == 0) {
          break;
        }

        --precision;
      }
    }

  }

  public static int timeToInternal(Time time) {
    long ts = time.getTime() + (long)LOCAL_TZ.getOffset(time.getTime());
    return (int)(ts % 86400000L);
  }

  public static int localTimeToUnixDate(LocalTime time) {
    return time.getHour() * 3600000 + time.getMinute() * '\uea60' + time.getSecond() * 1000 + time.getNano() / 1000000;
  }
}
