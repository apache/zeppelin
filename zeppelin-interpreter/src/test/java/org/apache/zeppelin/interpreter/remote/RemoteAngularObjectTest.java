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

package org.apache.zeppelin.interpreter.remote;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.zeppelin.display.*;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.remote.mock.MockInterpreterAngular;
import org.apache.zeppelin.resource.LocalResourcePool;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RemoteAngularObjectTest implements AngularObjectRegistryListener {
  private static final String INTERPRETER_SCRIPT =
          System.getProperty("os.name").startsWith("Windows") ?
                  "../bin/interpreter.cmd" :
                  "../bin/interpreter.sh";

  private InterpreterGroup intpGroup;
  private HashMap<String, String> env;
  private RemoteInterpreter intp;
  private InterpreterContext context;
  private RemoteAngularObjectRegistry localRegistry;

  private AtomicInteger onAdd;
  private AtomicInteger onUpdate;
  private AtomicInteger onRemove;

  @Before
  public void setUp() throws Exception {
    onAdd = new AtomicInteger(0);
    onUpdate = new AtomicInteger(0);
    onRemove = new AtomicInteger(0);

    intpGroup = new InterpreterGroup("intpId");
    localRegistry = new RemoteAngularObjectRegistry("intpId", this, intpGroup);
    intpGroup.setAngularObjectRegistry(localRegistry);
    env = new HashMap<String, String>();
    env.put("ZEPPELIN_CLASSPATH", new File("./target/test-classes").getAbsolutePath());

    Properties p = new Properties();

    intp = new RemoteInterpreter(
        p,
        "note",
        MockInterpreterAngular.class.getName(),
        new File(INTERPRETER_SCRIPT).getAbsolutePath(),
        "fake",
        "fakeRepo",
        env,
        10 * 1000,
        null
    );

    intpGroup.put("note", new LinkedList<Interpreter>());
    intpGroup.get("note").add(intp);
    intp.setInterpreterGroup(intpGroup);

    context = new InterpreterContext(
        "note",
        "id",
        "title",
        "text",
        new AuthenticationInfo(),
        new HashMap<String, Object>(),
        new GUI(),
        new AngularObjectRegistry(intpGroup.getId(), null),
        new LocalResourcePool("pool1"),
        new LinkedList<InterpreterContextRunner>(), null);

    intp.open();
  }

  @After
  public void tearDown() throws Exception {
    intp.close();
    intpGroup.close();
    intpGroup.destroy();
  }

  @Test
  public void testAngularObjectInterpreterSideCRUD() throws InterruptedException {
    InterpreterResult ret = intp.interpret("get", context);
    Thread.sleep(500); // waitFor eventpoller pool event
    String[] result = ret.message().split(" ");
    assertEquals("0", result[0]); // size of registry
    assertEquals("0", result[1]); // num watcher called

    // create object
    ret = intp.interpret("add n1 v1", context);
    Thread.sleep(500);
    result = ret.message().split(" ");
    assertEquals("1", result[0]); // size of registry
    assertEquals("0", result[1]); // num watcher called
    assertEquals("v1", localRegistry.get("n1", "note", null).get());

    // update object
    ret = intp.interpret("update n1 v11", context);
    result = ret.message().split(" ");
    Thread.sleep(500);
    assertEquals("1", result[0]); // size of registry
    assertEquals("1", result[1]); // num watcher called
    assertEquals("v11", localRegistry.get("n1", "note", null).get());

    // remove object
    ret = intp.interpret("remove n1", context);
    result = ret.message().split(" ");
    Thread.sleep(500);
    assertEquals("0", result[0]); // size of registry
    assertEquals("1", result[1]); // num watcher called
    assertEquals(null, localRegistry.get("n1", "note", null));
  }

  @Test
  public void testAngularObjectRemovalOnZeppelinServerSide() throws InterruptedException {
    // test if angularobject removal from server side propagate to interpreter process's registry.
    // will happen when notebook is removed.

    InterpreterResult ret = intp.interpret("get", context);
    Thread.sleep(500); // waitFor eventpoller pool event
    String[] result = ret.message().split(" ");
    assertEquals("0", result[0]); // size of registry
    
    // create object
    ret = intp.interpret("add n1 v1", context);
    Thread.sleep(500);
    result = ret.message().split(" ");
    assertEquals("1", result[0]); // size of registry
    assertEquals("v1", localRegistry.get("n1", "note", null).get());

    // remove object in local registry.
    localRegistry.removeAndNotifyRemoteProcess("n1", "note", null);
    ret = intp.interpret("get", context);
    Thread.sleep(500); // waitFor eventpoller pool event
    result = ret.message().split(" ");
    assertEquals("0", result[0]); // size of registry
  }

  @Test
  public void testAngularObjectAddOnZeppelinServerSide() throws InterruptedException {
    // test if angularobject add from server side propagate to interpreter process's registry.
    // will happen when zeppelin server loads notebook and restore the object into registry

    InterpreterResult ret = intp.interpret("get", context);
    Thread.sleep(500); // waitFor eventpoller pool event
    String[] result = ret.message().split(" ");
    assertEquals("0", result[0]); // size of registry
    
    // create object
    localRegistry.addAndNotifyRemoteProcess("n1", "v1", "note", null);
    
    // get from remote registry 
    ret = intp.interpret("get", context);
    Thread.sleep(500); // waitFor eventpoller pool event
    result = ret.message().split(" ");
    assertEquals("1", result[0]); // size of registry
  }

  @Override
  public void onAdd(String interpreterGroupId, AngularObject object) {
    onAdd.incrementAndGet();
  }

  @Override
  public void onUpdate(String interpreterGroupId, AngularObject object) {
    onUpdate.incrementAndGet();
  }

  @Override
  public void onRemove(String interpreterGroupId, String name, String noteId, String paragraphId) {
    onRemove.incrementAndGet();
  }

}
