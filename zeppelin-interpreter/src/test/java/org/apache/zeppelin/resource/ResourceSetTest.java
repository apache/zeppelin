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

import static org.junit.Assert.assertEquals;

/**
 * Unit test for ResourceSet
 */
public class ResourceSetTest {

  @Test
  public void testFilterByName() {
    ResourceSet set = new ResourceSet();

    set.add(new Resource(new ResourceId("poo1", "resource1"), "value1"));
    set.add(new Resource(new ResourceId("poo1", "resource2"), new Integer(2)));
    assertEquals(2, set.filterByNameRegex(".*").size());
    assertEquals(1, set.filterByNameRegex("resource1").size());
    assertEquals(1, set.filterByNameRegex("resource2").size());
    assertEquals(0, set.filterByNameRegex("res").size());
    assertEquals(2, set.filterByNameRegex("res.*").size());
  }

  @Test
  public void testFilterByClassName() {
    ResourceSet set = new ResourceSet();

    set.add(new Resource(new ResourceId("poo1", "resource1"), "value1"));
    set.add(new Resource(new ResourceId("poo1", "resource2"), new Integer(2)));

    assertEquals(1, set.filterByClassnameRegex(".*String").size());
    assertEquals(1, set.filterByClassnameRegex(String.class.getName()).size());
    assertEquals(1, set.filterByClassnameRegex(".*Integer").size());
    assertEquals(1, set.filterByClassnameRegex(Integer.class.getName()).size());
  }
}
