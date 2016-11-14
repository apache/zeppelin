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

package org.apache.zeppelin.redis.command;

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

import static org.apache.zeppelin.interpreter.InterpreterResult.Code.ERROR;
import static org.apache.zeppelin.interpreter.InterpreterResult.Code.SUCCESS;

/**
 * Redis Base Command
 */
public abstract class RedisCommand {

  private static final Logger LOG = LoggerFactory.getLogger(RedisCommand.class);
  private JedisPool clientPool;

  public RedisCommand(JedisPool clientPool) {
    this.clientPool = clientPool;
  }

  public InterpreterResult execute(String[] tokens) {
    try (Jedis client = clientPool.getResource()) {
      try {
        return new InterpreterResult(SUCCESS, execute(client, tokens));
      } catch (JedisException exception) {
        LOG.error("Couldn't execute redis command {}", tokens[0], exception);
        return new InterpreterResult(ERROR, exception.getMessage());
      }
    }
  }

  protected String[] copy(String[] tokens, int start) {
    String[] elements = new String[tokens.length - start];
    System.arraycopy(tokens, start, elements, 0, tokens.length - start);
    return elements;
  }

  /**
   * @param tokens
   * @return
   * @throws Exception
   */
  protected abstract String execute(Jedis client, String[] tokens);

}
