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

import static org.junit.Assert.assertEquals;

import java.util.Properties;

import org.apache.ignite.IgniteCache;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IgniteSqlInterpreterTest {

  private IgniteInterpreter ignite;
  private IgniteSqlInterpreter sql;
  private InterpreterContext context;

  @Before
  public void setUp() {
    Properties p = new Properties();
    p.setProperty("url", "localhost:11211");
    p.setProperty("ignite.clientMode", "true");
    ignite = new IgniteInterpreter(p);
    ignite.open();

    sql = new IgniteSqlInterpreter(p);
    sql.open();

    context = new InterpreterContext(null, null, null, null, null, null, null);
  }

  @After
  public void tearDown() {
    sql.close();
    ignite.close();
  }

  @Test
  public void testSql() {
    CacheConfiguration<Integer, Person> cacheConf = new CacheConfiguration<Integer, Person>();
    cacheConf.setName(null); // use default cache

    IgniteCache<Integer, Person> cache = ignite.getIgnite().cache("query");
    cache.put(1, new Person("sun", 100));
    cache.put(2, new Person("moon", 50));
    InterpreterResult result = sql.interpret("select name, age from Person where age > 10", context);
    assertEquals(Code.SUCCESS, result.code());
  }

}
