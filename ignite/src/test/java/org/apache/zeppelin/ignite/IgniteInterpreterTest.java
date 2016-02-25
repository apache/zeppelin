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
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for Apache Ignite interpreter ({@link IgniteInterpreter}).
 */
public class IgniteInterpreterTest {
  private static final String HOST = "127.0.0.1:47500..47509";

  private static final InterpreterContext INTP_CONTEXT =
      new InterpreterContext(null, null, null, null, null, null, null, null, null, null, null);

  private IgniteInterpreter intp;
  private Ignite ignite;

  @Before
  public void setUp() {
    TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
    ipFinder.setAddresses(Collections.singletonList(HOST));

    TcpDiscoverySpi discoSpi = new TcpDiscoverySpi();
    discoSpi.setIpFinder(ipFinder);

    IgniteConfiguration cfg = new IgniteConfiguration();
    cfg.setDiscoverySpi(discoSpi);

    cfg.setGridName("test");

    ignite = Ignition.start(cfg);

    Properties props = new Properties();
    props.setProperty(IgniteSqlInterpreter.IGNITE_JDBC_URL, "jdbc:ignite:cfg://cache=person@default-ignite-jdbc.xml");
    props.setProperty(IgniteInterpreter.IGNITE_CLIENT_MODE, "false");
    props.setProperty(IgniteInterpreter.IGNITE_PEER_CLASS_LOADING_ENABLED, "false");

    intp = new IgniteInterpreter(props);
    intp.open();
  }

  @After
  public void tearDown() {
    ignite.close();
    intp.close();
  }

  @Test
  public void testInterpret() {
    String sizeVal = "size";

    InterpreterResult result = intp.interpret("import org.apache.ignite.IgniteCache\n" +
            "val " + sizeVal + " = ignite.cluster().nodes().size()", INTP_CONTEXT);

    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(result.message().contains(sizeVal + ": Int = " + ignite.cluster().nodes().size()));

    result = intp.interpret("\"123\"\n  .toInt", INTP_CONTEXT);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
  }

  @Test
  public void testInterpretInvalidInput() {
    InterpreterResult result = intp.interpret("invalid input", INTP_CONTEXT);

    assertEquals(InterpreterResult.Code.ERROR, result.code());
  }

}
