package org.apache.zeppelin.redis.command.key;

import org.apache.zeppelin.redis.command.RedisCommand;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Wait for the synchronous replication of all the write commands sent
 * in the context of the current connection
 */
public class WAITCommand extends RedisCommand {

  public WAITCommand(JedisPool clientPool) {
    super(clientPool);
  }

  @Override
  protected String execute(Jedis client, String[] tokens) {
    try {
      client.wait(Long.valueOf(tokens[1]));
      return "SUCC";
    } catch (InterruptedException e) {
      return "Interrupted";
    }
  }
}
