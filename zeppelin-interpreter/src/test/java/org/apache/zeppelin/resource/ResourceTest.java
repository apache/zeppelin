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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

/**
 * Test for Resource
 */
public class ResourceTest {
  @Test
  public void testSerializeDeserialize() throws IOException, ClassNotFoundException {
    ByteBuffer buffer = Resource.serializeObject("hello");
    assertEquals("hello", Resource.deserializeObject(buffer));
  }

  @Test
  public void testInvokeMethod_shouldAbleToInvokeMethodWithNoParams() {
    Resource r = new Resource(null, new ResourceId("pool1", "name1"), "object");
    assertEquals(6, r.invokeMethod("length"));
    assertEquals(6, r.invokeMethod("length", new Class[]{}, new Object[]{}));
  }

  @Test
  public void testInvokeMethod_shouldAbleToInvokeMethodWithTypeInference() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Resource r = new Resource(null, new ResourceId("pool1", "name1"), "object");
    assertEquals("ect", r.invokeMethod("substring", new Object[]{3}));
    assertEquals("obj", r.invokeMethod("substring", new Object[]{0,3}));
    assertEquals(true, r.invokeMethod("startsWith", new Object[]{"obj"}));

    assertEquals(2, r.invokeMethod("indexOf", new Object[]{'j'}));
    assertEquals(4, r.invokeMethod("indexOf", new Object[]{"ct",3}));

    assertEquals("ect", r.invokeMethod("substring", new ArrayList<>(Arrays.asList(3))));
    assertEquals("ec", r.invokeMethod("substring", new ArrayList<>(Arrays.asList(3,5))));
    assertEquals(true, r.invokeMethod("startsWith", new ArrayList<>(Arrays.asList("obj"))));
  }

  @Test(expected = ClassNotFoundException.class)
  public void testInvokeMethod_shouldNotAbleToInvokeMethodWithTypeInference() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Resource r = new Resource(null, new ResourceId("pool1", "name1"), "object");
    r.invokeMethod("indexOf", new Object[]{"ct",3,4});
  }

    @Test
  public void testInvokeMethod_shouldAbleToInvokeMethodWithParamClassName() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Resource r = new Resource(null, new ResourceId("pool1", "name1"), "object");
    assertEquals("ect", r.invokeMethod("substring", new String[]{"int"}, new Object[]{3}));
    assertEquals(true, r.invokeMethod("startsWith", new String[]{"java.lang.String"}, new Object[]{"obj"}));

    assertEquals("ect", r.invokeMethod("substring", new ArrayList<>(Arrays.asList("int")), new ArrayList<>(Arrays.asList(3))));
    assertEquals(true, r.invokeMethod("startsWith", new ArrayList<>(Arrays.asList("java.lang.String")), new ArrayList<>(Arrays.asList("obj"))));
  }

  @Test
  public void testInvokeMethod_shouldAbleToInvokeMethodWithClass() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Resource r = new Resource(null, new ResourceId("pool1", "name1"), "object");
    assertEquals(true, r.invokeMethod("startsWith", new Class[]{ java.lang.String.class }, new Object[]{"obj"}));
  }
}
