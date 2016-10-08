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

package org.apache.zeppelin.redis;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.redis.command.RedisCommand;
import org.apache.zeppelin.redis.command.geo.GEOADDCommand;
import org.apache.zeppelin.redis.command.geo.GEODISTCommand;
import org.apache.zeppelin.redis.command.geo.GEOHASHCommand;
import org.apache.zeppelin.redis.command.geo.GEOPOSCommand;
import org.apache.zeppelin.redis.command.geo.GEORADIUSBYMEMBERCommand;
import org.apache.zeppelin.redis.command.geo.GEORADIUSCommand;
import org.apache.zeppelin.redis.command.hash.HDELCommand;
import org.apache.zeppelin.redis.command.hash.HEXISTSCommand;
import org.apache.zeppelin.redis.command.hash.HGETALLCommand;
import org.apache.zeppelin.redis.command.hash.HGETCommand;
import org.apache.zeppelin.redis.command.hash.HINCRBYCommand;
import org.apache.zeppelin.redis.command.hash.HINCRBYFLOATCommand;
import org.apache.zeppelin.redis.command.hash.HKEYSCommand;
import org.apache.zeppelin.redis.command.hash.HLENCommand;
import org.apache.zeppelin.redis.command.hash.HMGETCommand;
import org.apache.zeppelin.redis.command.hash.HMSETCommand;
import org.apache.zeppelin.redis.command.hash.HSCANCommand;
import org.apache.zeppelin.redis.command.hash.HSETCommand;
import org.apache.zeppelin.redis.command.hash.HSETNXCommand;
import org.apache.zeppelin.redis.command.hash.HVALSCommand;
import org.apache.zeppelin.redis.command.hyperloglog.PFADDCommand;
import org.apache.zeppelin.redis.command.hyperloglog.PFCOUNTCommand;
import org.apache.zeppelin.redis.command.hyperloglog.PFMERGECommand;
import org.apache.zeppelin.redis.command.key.*;
import org.apache.zeppelin.redis.command.list.*;
import org.apache.zeppelin.redis.command.set.SADDCommand;
import org.apache.zeppelin.redis.command.set.SCARDCommand;
import org.apache.zeppelin.redis.command.set.SDIFFCommand;
import org.apache.zeppelin.redis.command.set.SDIFFSTORECommand;
import org.apache.zeppelin.redis.command.set.SINTERCommand;
import org.apache.zeppelin.redis.command.set.SINTERSTORECommand;
import org.apache.zeppelin.redis.command.set.SISMEMBERCommand;
import org.apache.zeppelin.redis.command.set.SMEMBERSCommand;
import org.apache.zeppelin.redis.command.set.SMOVECommand;
import org.apache.zeppelin.redis.command.set.SPOPCommand;
import org.apache.zeppelin.redis.command.set.SRANDMEMBERCommand;
import org.apache.zeppelin.redis.command.set.SREMCommand;
import org.apache.zeppelin.redis.command.set.SSCANCommand;
import org.apache.zeppelin.redis.command.set.SUNIONCommand;
import org.apache.zeppelin.redis.command.set.SUNIONSTORECommand;
import org.apache.zeppelin.redis.command.set.sorted.*;
import org.apache.zeppelin.redis.command.string.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.apache.zeppelin.interpreter.InterpreterResult.Code.ERROR;

/**
 * Redis interpreter for Zeppelin.
 */
public class RedisInterpreter extends Interpreter {

  private static final Logger LOG = LoggerFactory.getLogger(RedisInterpreter.class);

  private static final char SPACE = ' ';
  private static final char DOUBLE_QUOTATION = '\"';

  private static final String REDIS_HOST = "redis.host";
  private static final String REDIS_PORT = "redis.port";

  private String host = Protocol.DEFAULT_HOST;
  private int port = Protocol.DEFAULT_PORT;

  private JedisPoolConfig config;
  private JedisPool clientPool;

  private static final Map<Protocol.Command, RedisCommand> COMMANDS = Maps.newHashMap();

  public RedisInterpreter(Properties property) {

    super(property);

    if (property.containsKey(REDIS_HOST) &&
          !Strings.isNullOrEmpty(property.getProperty(REDIS_HOST))) {
      host = property.getProperty(REDIS_HOST);
    }

    if (property.containsKey(REDIS_PORT) &&
            !Strings.isNullOrEmpty(property.getProperty(REDIS_PORT))) {
      port = Integer.valueOf(property.getProperty(REDIS_PORT));
    }

    LOG.info("Connection to %s:%d", host, port);

    config = new JedisPoolConfig();
    config.setMaxTotal(JedisPoolConfig.DEFAULT_MAX_TOTAL);
    config.setMaxIdle(JedisPoolConfig.DEFAULT_MAX_IDLE);
    config.setMinIdle(JedisPoolConfig.DEFAULT_MIN_IDLE);
    config.setMaxWaitMillis(JedisPoolConfig.DEFAULT_MAX_WAIT_MILLIS);
  }

  @Override
  public void open() {
    clientPool = new JedisPool(config, host, port);

    // GEO Commands
    COMMANDS.put(Protocol.Command.GEOADD, new GEOADDCommand(clientPool));
    COMMANDS.put(Protocol.Command.GEODIST, new GEODISTCommand(clientPool));
    COMMANDS.put(Protocol.Command.GEOHASH, new GEOHASHCommand(clientPool));
    COMMANDS.put(Protocol.Command.GEOPOS, new GEOPOSCommand(clientPool));
    COMMANDS.put(Protocol.Command.GEORADIUSBYMEMBER, new GEORADIUSBYMEMBERCommand(clientPool));
    COMMANDS.put(Protocol.Command.GEORADIUS, new GEORADIUSCommand(clientPool));

    // Hash Commands
    COMMANDS.put(Protocol.Command.HDEL, new HDELCommand(clientPool));
    COMMANDS.put(Protocol.Command.HEXISTS, new HEXISTSCommand(clientPool));
    COMMANDS.put(Protocol.Command.HGET, new HGETCommand(clientPool));
    COMMANDS.put(Protocol.Command.HGETALL, new HGETALLCommand(clientPool));
    COMMANDS.put(Protocol.Command.HINCRBY, new HINCRBYCommand(clientPool));
    COMMANDS.put(Protocol.Command.HINCRBYFLOAT, new HINCRBYFLOATCommand(clientPool));
    COMMANDS.put(Protocol.Command.HKEYS, new HKEYSCommand(clientPool));
    COMMANDS.put(Protocol.Command.HLEN, new HLENCommand(clientPool));
    COMMANDS.put(Protocol.Command.HMGET, new HMGETCommand(clientPool));
    COMMANDS.put(Protocol.Command.HMSET, new HMSETCommand(clientPool));
    COMMANDS.put(Protocol.Command.HSET, new HSETCommand(clientPool));
    COMMANDS.put(Protocol.Command.HSETNX, new HSETNXCommand(clientPool));
    COMMANDS.put(Protocol.Command.HVALS, new HVALSCommand(clientPool));
    COMMANDS.put(Protocol.Command.HSCAN, new HSCANCommand(clientPool));

    // HyperLogLog Commands
    COMMANDS.put(Protocol.Command.PFADD, new PFADDCommand(clientPool));
    COMMANDS.put(Protocol.Command.PFCOUNT, new PFCOUNTCommand(clientPool));
    COMMANDS.put(Protocol.Command.PFMERGE, new PFMERGECommand(clientPool));

    // Key Commands
    COMMANDS.put(Protocol.Command.DEL, new DELCommand(clientPool));
    COMMANDS.put(Protocol.Command.DUMP, new DUMPCommand(clientPool));
    COMMANDS.put(Protocol.Command.EXISTS, new EXISTSCommand(clientPool));
    COMMANDS.put(Protocol.Command.EXPIRE, new EXPIRECommand(clientPool));
    COMMANDS.put(Protocol.Command.EXPIREAT, new EXPIREATCommand(clientPool));
    COMMANDS.put(Protocol.Command.KEYS, new KEYSCommand(clientPool));
    COMMANDS.put(Protocol.Command.MIGRATE, new MIGRATECommand(clientPool));
    COMMANDS.put(Protocol.Command.MOVE, new MOVECommand(clientPool));
    COMMANDS.put(Protocol.Command.PERSIST, new PERSISTCommand(clientPool));
    COMMANDS.put(Protocol.Command.PEXPIRE, new PEXPIRECommand(clientPool));
    COMMANDS.put(Protocol.Command.PEXPIREAT, new PEXPIREATCommand(clientPool));
    COMMANDS.put(Protocol.Command.PTTL, new PTTLCommand(clientPool));
    COMMANDS.put(Protocol.Command.RANDOMKEY, new RANDOMKEYCommand(clientPool));
    COMMANDS.put(Protocol.Command.RENAME, new RENAMECommand(clientPool));
    COMMANDS.put(Protocol.Command.RENAMENX, new RENAMENXCommand(clientPool));
    COMMANDS.put(Protocol.Command.RESTORE, new RESTORECommand(clientPool));
    COMMANDS.put(Protocol.Command.SORT, new SORTCommand(clientPool));
    COMMANDS.put(Protocol.Command.TTL, new TTLCommand(clientPool));
    COMMANDS.put(Protocol.Command.TYPE, new TYPECommand(clientPool));
    COMMANDS.put(Protocol.Command.WAIT, new WAITCommand(clientPool));
    COMMANDS.put(Protocol.Command.SCAN, new SCANCommand(clientPool));

    // List Commands
    COMMANDS.put(Protocol.Command.BLPOP, new BLPOPCommand(clientPool));
    COMMANDS.put(Protocol.Command.BRPOP, new BRPOPCommand(clientPool));
    COMMANDS.put(Protocol.Command.BRPOPLPUSH, new BRPOPLPUSHCommand(clientPool));
    COMMANDS.put(Protocol.Command.LINDEX, new LINDEXCommand(clientPool));
    COMMANDS.put(Protocol.Command.LINSERT, new LINSERTCommand(clientPool));
    COMMANDS.put(Protocol.Command.LLEN, new LLENCommand(clientPool));
    COMMANDS.put(Protocol.Command.LPOP, new LPOPCommand(clientPool));
    COMMANDS.put(Protocol.Command.LPUSH, new LPUSHCommand(clientPool));
    COMMANDS.put(Protocol.Command.LPUSHX, new LPUSHXCommand(clientPool));
    COMMANDS.put(Protocol.Command.LRANGE, new LRANGECommand(clientPool));
    COMMANDS.put(Protocol.Command.LREM, new LREMCommand(clientPool));
    COMMANDS.put(Protocol.Command.LSET, new LSETCommand(clientPool));
    COMMANDS.put(Protocol.Command.LTRIM, new LTRIMCommand(clientPool));
    COMMANDS.put(Protocol.Command.RPOP, new RPOPCommand(clientPool));
    COMMANDS.put(Protocol.Command.RPOPLPUSH, new RPOPLPUSHCommand(clientPool));
    COMMANDS.put(Protocol.Command.RPUSH, new RPUSHCommand(clientPool));
    COMMANDS.put(Protocol.Command.RPUSHX, new RPUSHXCommand(clientPool));

    // Set Commands
    COMMANDS.put(Protocol.Command.SADD, new SADDCommand(clientPool));
    COMMANDS.put(Protocol.Command.SCARD, new SCARDCommand(clientPool));
    COMMANDS.put(Protocol.Command.SDIFF, new SDIFFCommand(clientPool));
    COMMANDS.put(Protocol.Command.SDIFFSTORE, new SDIFFSTORECommand(clientPool));
    COMMANDS.put(Protocol.Command.SINTER, new SINTERCommand(clientPool));
    COMMANDS.put(Protocol.Command.SINTERSTORE, new SINTERSTORECommand(clientPool));
    COMMANDS.put(Protocol.Command.SISMEMBER, new SISMEMBERCommand(clientPool));
    COMMANDS.put(Protocol.Command.SMEMBERS, new SMEMBERSCommand(clientPool));
    COMMANDS.put(Protocol.Command.SMOVE, new SMOVECommand(clientPool));
    COMMANDS.put(Protocol.Command.SPOP, new SPOPCommand(clientPool));
    COMMANDS.put(Protocol.Command.SRANDMEMBER, new SRANDMEMBERCommand(clientPool));
    COMMANDS.put(Protocol.Command.SREM, new SREMCommand(clientPool));
    COMMANDS.put(Protocol.Command.SUNION, new SUNIONCommand(clientPool));
    COMMANDS.put(Protocol.Command.SUNIONSTORE, new SUNIONSTORECommand(clientPool));
    COMMANDS.put(Protocol.Command.SSCAN, new SSCANCommand(clientPool));

    // Sorted Set Commands
    COMMANDS.put(Protocol.Command.ZADD, new ZADDCommand(clientPool));
    COMMANDS.put(Protocol.Command.ZCARD, new ZCARDCommand(clientPool));
    COMMANDS.put(Protocol.Command.ZCOUNT, new ZCOUNTCommand(clientPool));
    COMMANDS.put(Protocol.Command.ZINCRBY, new ZINCRBYCommand(clientPool));
    COMMANDS.put(Protocol.Command.ZINTERSTORE, new ZINTERSTORECommand(clientPool));
    COMMANDS.put(Protocol.Command.ZLEXCOUNT, new ZLEXCOUNTCommand(clientPool));
    COMMANDS.put(Protocol.Command.ZRANGE, new ZRANGECommand(clientPool));
    COMMANDS.put(Protocol.Command.ZRANGEBYLEX, new ZRANGEBYLEXCommand(clientPool));
    COMMANDS.put(Protocol.Command.ZREVRANGEBYLEX, new ZREVRANGEBYLEXCommand(clientPool));
    COMMANDS.put(Protocol.Command.ZRANGEBYSCORE, new ZRANGEBYSCORECommand(clientPool));
    COMMANDS.put(Protocol.Command.ZRANK, new ZRANKCommand(clientPool));
    COMMANDS.put(Protocol.Command.ZREM, new ZREMCommand(clientPool));
    COMMANDS.put(Protocol.Command.ZREMRANGEBYLEX, new ZREMRANGEBYLEXCommand(clientPool));
    COMMANDS.put(Protocol.Command.ZREMRANGEBYRANK, new ZREMRANGEBYRANKCommand(clientPool));
    COMMANDS.put(Protocol.Command.ZREMRANGEBYSCORE, new ZREMRANGEBYSCORECommand(clientPool));
    COMMANDS.put(Protocol.Command.ZREVRANGE, new ZREVRANGECommand(clientPool));
    COMMANDS.put(Protocol.Command.ZREVRANGEBYSCORE, new ZREVRANGEBYSCORECommand(clientPool));
    COMMANDS.put(Protocol.Command.ZREVRANK, new ZREVRANKCommand(clientPool));
    COMMANDS.put(Protocol.Command.ZSCORE, new ZSCORECommand(clientPool));
    COMMANDS.put(Protocol.Command.ZUNIONSTORE, new ZUNIONSTORECommand(clientPool));
    COMMANDS.put(Protocol.Command.ZSCAN, new ZSCANCommand(clientPool));

    // String Commands
    COMMANDS.put(Protocol.Command.APPEND, new APPENDCommand(clientPool));
    COMMANDS.put(Protocol.Command.BITCOUNT, new BITCOUNTCommand(clientPool));
    COMMANDS.put(Protocol.Command.BITFIELD, new BITFIELDCommand(clientPool));
    COMMANDS.put(Protocol.Command.BITOP, new BITOPCommand(clientPool));
    COMMANDS.put(Protocol.Command.BITPOS, new BITPOSCommand(clientPool));
    COMMANDS.put(Protocol.Command.DECRBY, new DECRBYCommand(clientPool));
    COMMANDS.put(Protocol.Command.DECR, new DECRCommand(clientPool));
    COMMANDS.put(Protocol.Command.GETBIT, new GETBITCommand(clientPool));
    COMMANDS.put(Protocol.Command.GET, new GETCommand(clientPool));
    COMMANDS.put(Protocol.Command.GETRANGE, new GETRANGECommand(clientPool));
    COMMANDS.put(Protocol.Command.GETSET, new GETSETCommand(clientPool));
    COMMANDS.put(Protocol.Command.INCRBY, new INCRBYCommand(clientPool));
    COMMANDS.put(Protocol.Command.INCRBYFLOAT, new INCRBYFLOATCommand(clientPool));
    COMMANDS.put(Protocol.Command.INCR, new INCRCommand(clientPool));
    COMMANDS.put(Protocol.Command.MGET, new MGETCommand(clientPool));
    COMMANDS.put(Protocol.Command.MSET, new MSETCommand(clientPool));
    COMMANDS.put(Protocol.Command.MSETNX, new MSETNXCommand(clientPool));
    COMMANDS.put(Protocol.Command.PSETEX, new PSETEXCommand(clientPool));
    COMMANDS.put(Protocol.Command.SETBIT, new SETBITCommand(clientPool));
    COMMANDS.put(Protocol.Command.SET, new SETCommand(clientPool));
    COMMANDS.put(Protocol.Command.SETEX, new SETEXCommand(clientPool));
    COMMANDS.put(Protocol.Command.SETNX, new SETNXCommand(clientPool));
    COMMANDS.put(Protocol.Command.SETRANGE, new SETRANGECommand(clientPool));
    COMMANDS.put(Protocol.Command.STRLEN, new STRLENCommand(clientPool));
  }

  @Override
  public InterpreterResult interpret(String input, InterpreterContext context) {
    String[] tokens = splitTokens(input);
    String keyword = tokens[0].toUpperCase();

    InterpreterResult result;
    Protocol.Command command = Protocol.Command.valueOf(keyword);
    if (COMMANDS.containsKey(command)) {
      result = COMMANDS.get(command).execute(tokens);
    } else {
      result = new InterpreterResult(ERROR, "Keyword " + tokens + " is not supported");
    }

    return result;
  }

  private String[] splitTokens(String input) {
    List<String> list = Lists.newArrayList();
    int start = 0;
    boolean flag = false;

    for (int index = 0; index < input.length(); index++) {
      if (input.charAt(index) == DOUBLE_QUOTATION) {
        flag = !flag;
      }

      if (input.charAt(index) == SPACE && !flag) {
        String token = input.substring(start, index);
        if (!Strings.isNullOrEmpty(token.trim())) {
          list.add(token);
        }
        start = index + 1;
      }
    }

    list.add(input.substring(start, input.length()));
    String[] result = new String[list.size()];
    return list.toArray(result);
  }

  @Override
  public void cancel(InterpreterContext context) {

  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public void close() {
    if (clientPool != null && clientPool.isClosed()) {
      clientPool.close();
    }
  }
}
