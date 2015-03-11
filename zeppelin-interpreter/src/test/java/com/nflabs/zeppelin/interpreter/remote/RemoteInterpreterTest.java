package com.nflabs.zeppelin.interpreter.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.thrift.transport.TTransportException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.display.GUI;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterGroup;
import com.nflabs.zeppelin.interpreter.remote.mock.MockInterpreterA;
import com.nflabs.zeppelin.interpreter.remote.mock.MockInterpreterB;

public class RemoteInterpreterTest {


  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void testRemoteInterperterCall() throws TTransportException, IOException {
    Properties p = new Properties();
    InterpreterGroup intpGroup = new InterpreterGroup();
    Map<String, String> env = new HashMap<String, String>();
    env.put("ZEPPELIN_CLASSPATH", new File("./target/test-classes").getAbsolutePath());

    RemoteInterpreter intpA = new RemoteInterpreter(
        p,
        MockInterpreterA.class.getName(),
        new File("../bin/interpreter.sh").getAbsolutePath(),
        "fake",
        env
        );

    intpGroup.add(intpA);
    intpA.setInterpreterGroup(intpGroup);

    RemoteInterpreter intpB = new RemoteInterpreter(
        p,
        MockInterpreterB.class.getName(),
        new File("../bin/interpreter.sh").getAbsolutePath(),
        "fake",
        env
        );

    intpGroup.add(intpB);
    intpB.setInterpreterGroup(intpGroup);


    RemoteInterpreterProcess process = intpA.getInterpreterProcess();
    process.equals(intpB.getInterpreterProcess());

    assertFalse(process.isRunning());
    assertEquals(0, process.getNumIdleClient());
    assertEquals(0, process.referenceCount());

    intpA.open();
    assertTrue(process.isRunning());
    assertEquals(1, process.getNumIdleClient());
    assertEquals(1, process.referenceCount());

    intpA.interpret("1",
        new InterpreterContext(
            "id",
            "title",
            "text",
            new HashMap<String, Object>(),
            new GUI()));

    intpB.open();
    assertEquals(2, process.referenceCount());

    intpA.close();
    assertEquals(1, process.referenceCount());
    intpB.close();
    assertEquals(0, process.referenceCount());

    assertFalse(process.isRunning());

  }

  @Test
  public void testRemoteSchedulerSharing() throws TTransportException, IOException {
    Properties p = new Properties();
    InterpreterGroup intpGroup = new InterpreterGroup();
    Map<String, String> env = new HashMap<String, String>();
    env.put("ZEPPELIN_CLASSPATH", new File("./target/test-classes").getAbsolutePath());

    RemoteInterpreter intpA = new RemoteInterpreter(
        p,
        MockInterpreterA.class.getName(),
        new File("../bin/interpreter.sh").getAbsolutePath(),
        "fake",
        env
        );

    intpGroup.add(intpA);
    intpA.setInterpreterGroup(intpGroup);

    RemoteInterpreter intpB = new RemoteInterpreter(
        p,
        MockInterpreterB.class.getName(),
        new File("../bin/interpreter.sh").getAbsolutePath(),
        "fake",
        env
        );

    intpGroup.add(intpB);
    intpB.setInterpreterGroup(intpGroup);

    intpA.open();
    intpB.open();

    long start = System.currentTimeMillis();
    intpA.interpret("500",
        new InterpreterContext(
            "id",
            "title",
            "text",
            new HashMap<String, Object>(),
            new GUI()));

    intpB.interpret("500",
        new InterpreterContext(
            "id",
            "title",
            "text",
            new HashMap<String, Object>(),
            new GUI()));
    long end = System.currentTimeMillis();
    assertTrue(end - start >= 1000);


    intpA.close();
    intpB.close();

    RemoteInterpreterProcess process = intpA.getInterpreterProcess();
    assertFalse(process.isRunning());

  }
}
