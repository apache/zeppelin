package com.nflabs.zeppelin.interpreter.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.HashMap;

import org.junit.Test;

import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;

public class RemoteInterpreterProcessTest {

  @Test
  public void testStartStop() {
    RemoteInterpreterProcess rip = new RemoteInterpreterProcess("../bin/interpreter.sh", "nonexists", new HashMap<String, String>());
    assertFalse(rip.isRunning());
    assertEquals(0, rip.referenceCount());
    assertEquals(1, rip.reference());
    assertEquals(2, rip.reference());
    assertEquals(true, rip.isRunning());
    assertEquals(1, rip.dereference());
    assertEquals(true, rip.isRunning());
    assertEquals(0, rip.dereference());
    assertEquals(false, rip.isRunning());
  }

  @Test
  public void testClientFactory() throws Exception {
    RemoteInterpreterProcess rip = new RemoteInterpreterProcess("../bin/interpreter.sh", "nonexists", new HashMap<String, String>());
    rip.reference();
    assertEquals(0, rip.getNumActiveClient());
    assertEquals(0, rip.getNumIdleClient());

    Client client = rip.getClient();
    assertEquals(1, rip.getNumActiveClient());
    assertEquals(0, rip.getNumIdleClient());

    rip.releaseClient(client);
    assertEquals(0, rip.getNumActiveClient());
    assertEquals(1, rip.getNumIdleClient());

    rip.dereference();
  }

}
