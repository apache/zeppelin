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
package org.apache.zeppelin.ignite;

import java.util.Collections;
import java.util.Properties;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterResult.Type;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for Apache Ignite SQL interpreter ({@link IgniteSqlInterpreter}).
 */
public class IgniteSqlInterpreterTest {
  private static final String HOST = "127.0.0.1:47500..47509";

  private static final InterpreterContext INTP_CONTEXT =
      new InterpreterContext(null, null, null, null, null, null, null, null, null, null, null, null);

  private Ignite ignite;
  private IgniteSqlInterpreter intp;

  @Before
  public void setUp() {
    TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
    ipFinder.setAddresses(Collections.singletonList(HOST));

    TcpDiscoverySpi discoSpi = new TcpDiscoverySpi();
    discoSpi.setIpFinder(ipFinder);

    IgniteConfiguration cfg = new IgniteConfiguration();
    cfg.setDiscoverySpi(discoSpi);
    cfg.setPeerClassLoadingEnabled(true);

    cfg.setGridName("test");

    ignite = Ignition.start(cfg);

    Properties props = new Properties();
    props.setProperty(IgniteSqlInterpreter.IGNITE_JDBC_URL, "jdbc:ignite:cfg://cache=person@default-ignite-jdbc.xml");

    intp = new IgniteSqlInterpreter(props);

    CacheConfiguration<Integer, Person> cacheConf = new CacheConfiguration<>();
    cacheConf.setIndexedTypes(Integer.class, Person.class);
    cacheConf.setName("person");

    IgniteCache<Integer, Person> cache = ignite.createCache(cacheConf);
    cache.put(1, new Person("sun", 100));
    cache.put(2, new Person("moon", 50));
    assertEquals("moon", cache.get(2).getName());

    intp.open();
  }

  @After
  public void tearDown() throws InterpreterException {
    intp.close();
    ignite.close();
  }

  @Test
  public void testSql() {
    InterpreterResult result = intp.interpret("select name, age from person where age > 10", INTP_CONTEXT);

    assertEquals(Code.SUCCESS, result.code());
    assertEquals(Type.TABLE, result.message().get(0).getType());
    assertEquals("NAME\tAGE\nsun\t100\nmoon\t50\n", result.message().get(0).getData());
  }

  @Test
  public void testInvalidSql() throws Exception {
    InterpreterResult result = intp.interpret("select * hrom person", INTP_CONTEXT);

    assertEquals(Code.ERROR, result.code());
  }
}
