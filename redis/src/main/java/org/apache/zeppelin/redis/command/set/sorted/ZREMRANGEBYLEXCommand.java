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

package org.apache.zeppelin.redis.command.set.sorted;

import org.apache.zeppelin.redis.command.RedisCommand;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Remove all members in a sorted set between the given lexicographical range
 */
public class ZREMRANGEBYLEXCommand extends RedisCommand {

  public ZREMRANGEBYLEXCommand(JedisPool clientPool) {
    super(clientPool);
  }

  @Override
  protected String execute(Jedis client, String[] tokens) {
    String result = client.zremrangeByLex(tokens[1], tokens[2], tokens[3]).toString();
    return result;
  }
}
