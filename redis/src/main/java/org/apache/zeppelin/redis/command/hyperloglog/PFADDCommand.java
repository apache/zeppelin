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

package org.apache.zeppelin.redis.command.hyperloglog;

import org.apache.zeppelin.redis.command.RedisCommand;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Adds the specified elements to the specified HyperLogLog.
 */
public class PFADDCommand extends RedisCommand {

  public PFADDCommand(JedisPool clientPool) {
    super(clientPool);
  }

  @Override
  protected String execute(Jedis client, String[] tokens) {
    String[] elements = new String[tokens.length - 2];
    System.arraycopy(tokens, 2, elements, 0, tokens.length - 2);
    String result = client.pfadd(tokens[1], elements).toString();
    return result;
  }
}
