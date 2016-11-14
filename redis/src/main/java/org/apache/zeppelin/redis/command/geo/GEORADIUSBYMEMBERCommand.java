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

package org.apache.zeppelin.redis.command.geo;

import org.apache.zeppelin.redis.command.RedisCommand;
import redis.clients.jedis.GeoUnit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Query a sorted set representing a geospatial index to fetch members matching
 * a given maximum distance from a member
 */
public class GEORADIUSBYMEMBERCommand extends RedisCommand {

  public GEORADIUSBYMEMBERCommand(JedisPool clientPool) {
    super(clientPool);
  }

  @Override
  protected String execute(Jedis client, String[] tokens) {
    String result = client.georadiusByMember(tokens[1], tokens[2],
            Double.valueOf(tokens[3]),
            GeoUnit.valueOf(tokens[4])).toString();
    return result;
  }
}
