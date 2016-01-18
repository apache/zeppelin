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

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unittest for LocalResourcePool
 */
public class LocalResourcePoolTest {

  @Test
  public void testGetPutResourcePool() {

    LocalResourcePool pool = new LocalResourcePool("pool1");
    assertEquals("pool1", pool.id());

    assertNull(pool.get("notExists"));
    pool.put("item1", "value1");
    Resource resource = pool.get("item1");
    assertNotNull(resource);
    assertEquals(pool.id(), resource.getResourceId().getResourcePoolId());
    assertEquals("value1", resource.get());
    assertTrue(resource.isLocal());
    assertTrue(resource.isSerializable());

    assertEquals(1, pool.getAll().size());

    assertNotNull(pool.remove("item1"));
    assertNull(pool.remove("item1"));
  }
}
