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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.embedded.RedisServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RedisMetricStorageTest {
  static int redisPort = 16380;
  private RedisServer redisServer;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    redisServer = new RedisServer(redisPort);
    redisServer.start();
  }

  @After
  public void tearDown() throws InterruptedException {
    redisServer.stop();
  }

  @Test
  public void testAdd() throws InterruptedException {
    RedisMetricStorage m = new RedisMetricStorage("localhost:" + redisPort, "note1", "rev1", 1);
    Date now = new Date();

    Metric m1 = m.add(now, "ep1", "count", 1);
    assertEquals(1, m1.getCount(), 0);
    assertEquals(1.0, m1.getSum(), 0);

    Metric m2 = m.add(now, "ep1", "count", 2);
    assertEquals(2, m2.getCount(), 0);
    assertEquals(3.0, m2.getSum(), 0);
  }

  @Test
  public void testExpire() throws InterruptedException {
    // given
    RedisMetricStorage m = new RedisMetricStorage("localhost:" + redisPort, "note1", "rev1", 1);
    Date now = new Date();

    // when
    assertEquals(null, m.get(now, "ep2", "count"));
    m.add(now, "ep2", "count", 1);
    assertEquals(1, m.get(now, "ep2", "count").getCount());
    Thread.sleep(1000);

    // then
    assertEquals(null, m.get(now, "ep2", "count"));
  }

  @Test
  public void testGetAll() {
    // given
    RedisMetricStorage m = new RedisMetricStorage("localhost:" + redisPort, "note1", "rev1", 1);
    Date now = new Date();

    // when
    m.add(now, "ep3", "count", 1);

    // then
    Map<String, Metric> map = m.get(now, "ep3");
    assertEquals(1, map.get("count").getCount());
    assertEquals(1.0, map.get("count").getSum(), 1.0);
  }

  @Test
  public void testReconnect() throws InterruptedException, IOException {
    // given
    RedisMetricStorage m = new RedisMetricStorage("localhost:" + redisPort, "note1", "rev1", 1);

    Date now = new Date();

    // when
    m.add(now, "ep4", "count", 1);
    redisServer.stop();
    redisServer.start();

    // then
    try {
      m.add(now, "ep4", "count", 1);
      assertFalse(true);
    } catch (JedisConnectionException e) {
      // exception expected
    }

    // then
    Metric metric = m.add(now, "ep4", "count", 1);
    assertEquals(1, m.get(now, "ep4", "count").getCount());
  }
}
