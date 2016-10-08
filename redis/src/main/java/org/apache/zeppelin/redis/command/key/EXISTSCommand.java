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

package org.apache.zeppelin.redis.command.key;

import org.apache.zeppelin.redis.command.RedisCommand;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Determine if a key exists
 */
public class EXISTSCommand extends RedisCommand {

  public EXISTSCommand(JedisPool clientPool) {
    super(clientPool);
  }

  @Override
  protected String execute(Jedis client, String[] tokens) {
    String[] keys = new String[tokens.length - 1];
    System.arraycopy(tokens, 1, keys, 0, tokens.length - 1);
    String result = client.exists(keys).toString();
    return result;
  }
}
