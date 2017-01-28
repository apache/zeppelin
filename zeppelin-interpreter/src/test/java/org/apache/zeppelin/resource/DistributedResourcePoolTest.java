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
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.*;
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
  private static final String INTERPRETER_SCRIPT =
          System.getProperty("os.name").startsWith("Windows") ?
                  "../bin/interpreter.cmd" :
                  "../bin/interpreter.sh";
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
    env = new HashMap<>();
    env.put("ZEPPELIN_CLASSPATH", new File("./target/test-classes").getAbsolutePath());

    Properties p = new Properties();

    intp1 = new RemoteInterpreter(
        p,
        "note",
        MockInterpreterResourcePool.class.getName(),
        new File(INTERPRETER_SCRIPT).getAbsolutePath(),
        "fake",
        "fakeRepo",
        env,
        10 * 1000,
        null,
        null,
        "anonymous",
        false
    );

    intpGroup1 = new InterpreterGroup("intpGroup1");
    intpGroup1.put("note", new LinkedList<Interpreter>());
    intpGroup1.get("note").add(intp1);
    intp1.setInterpreterGroup(intpGroup1);

    intp2 = new RemoteInterpreter(
        p,
        "note",
        MockInterpreterResourcePool.class.getName(),
        new File(INTERPRETER_SCRIPT).getAbsolutePath(),
        "fake",
        "fakeRepo",        
        env,
        10 * 1000,
        null,
        null,
        "anonymous",
        false
    );

    intpGroup2 = new InterpreterGroup("intpGroup2");
    intpGroup2.put("note", new LinkedList<Interpreter>());
    intpGroup2.get("note").add(intp2);
    intp2.setInterpreterGroup(intpGroup2);

    context = new InterpreterContext(
        "note",
        "id",
        null,
        "title",
        "text",
        new AuthenticationInfo(),
        new HashMap<String, Object>(),
        new GUI(),
        null,
        null,
        new LinkedList<InterpreterContextRunner>(),
        null);

    intp1.open();
    intp2.open();

    eventPoller1 = new RemoteInterpreterEventPoller(null, null);
    eventPoller1.setInterpreterGroup(intpGroup1);
    eventPoller1.setInterpreterProcess(intpGroup1.getRemoteInterpreterProcess());

    eventPoller2 = new RemoteInterpreterEventPoller(null, null);
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
    eventPoller2.shutdown();
    intp2.close();
    intpGroup2.close();
  }

  @Test
  public void testRemoteDistributedResourcePool() {
    Gson gson = new Gson();
    InterpreterResult ret;
    intp1.interpret("put key1 value1", context);
    intp2.interpret("put key2 value2", context);

    ret = intp1.interpret("getAll", context);
    assertEquals(2, gson.fromJson(ret.message().get(0).getData(), ResourceSet.class).size());

    ret = intp2.interpret("getAll", context);
    assertEquals(2, gson.fromJson(ret.message().get(0).getData(), ResourceSet.class).size());

    ret = intp1.interpret("get key1", context);
    assertEquals("value1", gson.fromJson(ret.message().get(0).getData(), String.class));

    ret = intp1.interpret("get key2", context);
    assertEquals("value2", gson.fromJson(ret.message().get(0).getData(), String.class));
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

      @Override
      public Object invokeMethod(ResourceId id, String methodName, Class[] paramTypes, Object[] params) {
        return null;
      }

      @Override
      public Resource invokeMethod(ResourceId id, String methodName, Class[] paramTypes, Object[]
          params, String returnResourceName) {
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

  @Test
  public void testResourcePoolUtils() {
    Gson gson = new Gson();
    InterpreterResult ret;

    // when create some resources
    intp1.interpret("put note1:paragraph1:key1 value1", context);
    intp1.interpret("put note1:paragraph2:key1 value2", context);
    intp2.interpret("put note2:paragraph1:key1 value1", context);
    intp2.interpret("put note2:paragraph2:key2 value2", context);


    // then get all resources.
    assertEquals(4, ResourcePoolUtils.getAllResources().size());

    // when remove all resources from note1
    ResourcePoolUtils.removeResourcesBelongsToNote("note1");

    // then resources should be removed.
    assertEquals(2, ResourcePoolUtils.getAllResources().size());
    assertEquals("", gson.fromJson(
        intp1.interpret("get note1:paragraph1:key1", context).message().get(0).getData(),
        String.class));
    assertEquals("", gson.fromJson(
        intp1.interpret("get note1:paragraph2:key1", context).message().get(0).getData(),
        String.class));


    // when remove all resources from note2:paragraph1
    ResourcePoolUtils.removeResourcesBelongsToParagraph("note2", "paragraph1");

    // then 1
    assertEquals(1, ResourcePoolUtils.getAllResources().size());
    assertEquals("value2", gson.fromJson(
        intp1.interpret("get note2:paragraph2:key2", context).message().get(0).getData(),
        String.class));

  }

  @Test
  public void testResourceInvokeMethod() {
    Gson gson = new Gson();
    InterpreterResult ret;
    intp1.interpret("put key1 hey", context);
    intp2.interpret("put key2 world", context);

    // invoke method in local resource pool
    ret = intp1.interpret("invoke key1 length", context);
    assertEquals("3", ret.message().get(0).getData());

    // invoke method in remote resource pool
    ret = intp1.interpret("invoke key2 length", context);
    assertEquals("5", ret.message().get(0).getData());

    // make sure no resources are automatically created
    ret = intp1.interpret("getAll", context);
    assertEquals(2, gson.fromJson(ret.message().get(0).getData(), ResourceSet.class).size());

    // invoke method in local resource pool and save result
    ret = intp1.interpret("invoke key1 length ret1", context);
    assertEquals("3", ret.message().get(0).getData());

    ret = intp1.interpret("getAll", context);
    assertEquals(3, gson.fromJson(ret.message().get(0).getData(), ResourceSet.class).size());

    ret = intp1.interpret("get ret1", context);
    assertEquals("3", gson.fromJson(ret.message().get(0).getData(), String.class));

    // invoke method in remote resource pool and save result
    ret = intp1.interpret("invoke key2 length ret2", context);
    assertEquals("5", ret.message().get(0).getData());

    ret = intp1.interpret("getAll", context);
    assertEquals(4, gson.fromJson(ret.message().get(0).getData(), ResourceSet.class).size());

    ret = intp1.interpret("get ret2", context);
    assertEquals("5", gson.fromJson(ret.message().get(0).getData(), String.class));
  }
}
