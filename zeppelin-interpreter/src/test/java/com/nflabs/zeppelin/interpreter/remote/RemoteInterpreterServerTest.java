package com.nflabs.zeppelin.interpreter.remote;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.thrift.TException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RemoteInterpreterServerTest {
  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void testStartStop() throws InterruptedException, IOException, TException {
    RemoteInterpreterServer server = new RemoteInterpreterServer(
        RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces());
    assertEquals(false, server.isRunning());

    server.start();
    long startTime = System.currentTimeMillis();
    boolean running = false;

    while (System.currentTimeMillis() - startTime < 10 * 1000) {
      if (server.isRunning()) {
        running = true;
        break;
      } else {
        Thread.sleep(200);
      }
    }

    assertEquals(true, running);
    assertEquals(true, RemoteInterpreterUtils.checkIfRemoteEndpointAccessible("localhost", server.getPort()));

    server.shutdown();

    while (System.currentTimeMillis() - startTime < 10 * 1000) {
      if (server.isRunning()) {
        Thread.sleep(200);
      } else {
        running = false;
        break;
      }
    }
    assertEquals(false, running);
  }


}
