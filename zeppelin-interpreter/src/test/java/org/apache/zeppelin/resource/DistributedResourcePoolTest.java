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
package org.apache.zeppelin.resource;

import com.google.gson.Gson;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventPoller;
import org.apache.zeppelin.interpreter.remote.mock.MockInterpreterResourcePool;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unittest for DistributedResourcePool
 */
public class DistributedResourcePoolTest {
  private InterpreterGroup intpGroup1;
  private InterpreterGroup intpGroup2;
  private HashMap<String, String> env;
  private RemoteInterpreter intp1;
  private RemoteInterpreter intp2;
  private InterpreterContext context;
  private RemoteInterpreterEventPoller eventPoller1;
  private RemoteInterpreterEventPoller eventPoller2;


  @Before
  public void setUp() throws Exception {
    env = new HashMap<String, String>();
    env.put("ZEPPELIN_CLASSPATH", new File("./target/test-classes").getAbsolutePath());

    Properties p = new Properties();

    intp1 = new RemoteInterpreter(
        p,
        MockInterpreterResourcePool.class.getName(),
        new File("../bin/interpreter.sh").getAbsolutePath(),
        "fake",
        "fakeRepo",
        env,
        10 * 1000,
        null
    );

    intpGroup1 = new InterpreterGroup("intpGroup1");
    intpGroup1.add(intp1);
    intp1.setInterpreterGroup(intpGroup1);

    intp2 = new RemoteInterpreter(
        p,
        MockInterpreterResourcePool.class.getName(),
        new File("../bin/interpreter.sh").getAbsolutePath(),
        "fake",
        "fakeRepo",        
        env,
        10 * 1000,
        null
    );

    intpGroup2 = new InterpreterGroup("intpGroup2");
    intpGroup2.add(intp2);
    intp2.setInterpreterGroup(intpGroup2);

    context = new InterpreterContext(
        "note",
        "id",
        "title",
        "text",
        new HashMap<String, Object>(),
        new GUI(),
        null,
        null,
        new LinkedList<InterpreterContextRunner>(),
        null);

    intp1.open();
    intp2.open();

    eventPoller1 = new RemoteInterpreterEventPoller(null);
    eventPoller1.setInterpreterGroup(intpGroup1);
    eventPoller1.setInterpreterProcess(intpGroup1.getRemoteInterpreterProcess());

    eventPoller2 = new RemoteInterpreterEventPoller(null);
    eventPoller2.setInterpreterGroup(intpGroup2);
    eventPoller2.setInterpreterProcess(intpGroup2.getRemoteInterpreterProcess());

    eventPoller1.start();
    eventPoller2.start();
  }

  @After
  public void tearDown() throws Exception {
    eventPoller1.shutdown();
    intp1.close();
    intpGroup1.close();
    intpGroup1.destroy();
    eventPoller2.shutdown();
    intp2.close();
    intpGroup2.close();
    intpGroup2.destroy();
  }

  @Test
  public void testRemoteDistributedResourcePool() {
    Gson gson = new Gson();
    InterpreterResult ret;
    intp1.interpret("put key1 value1", context);
    intp2.interpret("put key2 value2", context);

    ret = intp1.interpret("getAll", context);
    assertEquals(2, gson.fromJson(ret.message(), ResourceSet.class).size());

    ret = intp2.interpret("getAll", context);
    assertEquals(2, gson.fromJson(ret.message(), ResourceSet.class).size());

    ret = intp1.interpret("get key1", context);
    assertEquals("value1", gson.fromJson(ret.message(), String.class));

    ret = intp1.interpret("get key2", context);
    assertEquals("value2", gson.fromJson(ret.message(), String.class));
  }

  @Test
  public void testDistributedResourcePool() {
    final LocalResourcePool pool2 = new LocalResourcePool("pool2");
    final LocalResourcePool pool3 = new LocalResourcePool("pool3");

    DistributedResourcePool pool1 = new DistributedResourcePool("pool1", new ResourcePoolConnector() {
      @Override
      public ResourceSet getAllResources() {
        ResourceSet set = pool2.getAll();
        set.addAll(pool3.getAll());

        ResourceSet remoteSet = new ResourceSet();
        Gson gson = new Gson();
        for (Resource s : set) {
          RemoteResource remoteResource = gson.fromJson(gson.toJson(s), RemoteResource.class);
          remoteResource.setResourcePoolConnector(this);
          remoteSet.add(remoteResource);
        }
        return remoteSet;
      }

      @Override
      public Object readResource(ResourceId id) {
        if (id.getResourcePoolId().equals(pool2.id())) {
          return pool2.get(id.getName()).get();
        }
        if (id.getResourcePoolId().equals(pool3.id())) {
          return pool3.get(id.getName()).get();
        }
        return null;
      }
    });

    assertEquals(0, pool1.getAll().size());


    // test get() can get from pool
    pool2.put("object1", "value2");
    assertEquals(1, pool1.getAll().size());
    assertTrue(pool1.get("object1").isRemote());
    assertEquals("value2", pool1.get("object1").get());

    // test get() is locality aware
    pool1.put("object1", "value1");
    assertEquals(1, pool2.getAll().size());
    assertEquals("value1", pool1.get("object1").get());

    // test getAll() is locality aware
    assertEquals("value1", pool1.getAll().get(0).get());
    assertEquals("value2", pool1.getAll().get(1).get());
  }
}
