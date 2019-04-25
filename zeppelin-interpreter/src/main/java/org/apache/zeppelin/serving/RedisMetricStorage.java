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
package org.apache.zeppelin.serving;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import redis.clients.jedis.Jedis;

public class RedisMetricStorage implements MetricStorage {
  public static final int DEFAULT_METRIC_EXPIRE_SEC = 60 * 30;

  private final Jedis redis;
  private final String noteId;
  private final String revId;
  private final SimpleDateFormat dateFormat;
  private final int metricExpireSec;

  public RedisMetricStorage() {
    // serviceName is "<serving|test>-<noteId>-<revId>"
    String serviceName = System.getenv("SERVICE_NAME");
    String[] names = serviceName.split("-");
    noteId = names[1];
    revId = names[2];

    metricExpireSec = DEFAULT_METRIC_EXPIRE_SEC;

    String[] hostPort = getRedisAddr().split(":");
    redis = new Jedis(hostPort[0], Integer.parseInt(hostPort[1]));
    dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm");
  }

  @VisibleForTesting
  public RedisMetricStorage(
          String redisAddr,
          String noteId,
          String revId,
          int metricExpireSec
  ) {
    this.noteId = noteId;
    this.revId = revId;
    this.metricExpireSec = metricExpireSec;

    String[] hostPort = redisAddr.split(":");
    redis = new Jedis(hostPort[0], Integer.parseInt(hostPort[1]));
    dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm");
  }


  String getRedisAddr() {
    String addr = System.getenv(
            ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_METRIC_REDIS_ADDR.name());
    return addr;
  }

  String redisKey(Date date, String endpoint) {
    return redisKey(date, noteId, revId, endpoint);
  }

  String redisKey(Date date, String noteId, String revId, String endpoint) {
    long ts = date.getTime()/1000;
    ts -= ts % 60;
    String dateString = Long.toString(ts);
    return String.format("%s.%s.%s.%s", noteId, revId, endpoint, dateString).toLowerCase();
  }

  private void setExpire(Date updateDate, String key) {
    redis.expireAt(key, (updateDate.getTime()/1000) + metricExpireSec);
  }

  @Override
  public double incr(Date date, String endpoint, String field, double n) {
    String key = redisKey(date, endpoint);
    Double r = redis.hincrByFloat(key, field, n);
    setExpire(date, key);
    return r;
  }

  @Override
  public void set(Date date, String endpoint, String field, String value) {
    String key = redisKey(date, endpoint);
    redis.hset(key, field, value);
    setExpire(date, key);
  }

  @Override
  public Object get(Date date, String endpoint, String field) {
    String key = redisKey(date, endpoint);
    return redis.hget(key, field);
  }

  @Override
  public Map<String, String> get(Date date, String endpoint) {
    String key = redisKey(date, endpoint);
    return redis.hgetAll(key);
  }

  @Override
  public Map<String, String> get(Date date, String noteId, String revId, String endpoint) {
    String key = redisKey(date, noteId, revId, endpoint);
    return redis.hgetAll(key);
  }

  @Override
  public synchronized List<Map<String, Object>> get(Date from, Date to, String noteId, String revId, String endpoint) {
    List<Map<String, Object>> series = new LinkedList<>();

    Date p = from;
    while (p.before(to)) {
      series.add(ImmutableMap.of(
              "timestamp", p.getTime()/1000,
              "metric", get(p, noteId, revId, endpoint)
      ));
      p = new Date(p.getTime() + (1000 * 60));
    }

    return series;
  }
}
