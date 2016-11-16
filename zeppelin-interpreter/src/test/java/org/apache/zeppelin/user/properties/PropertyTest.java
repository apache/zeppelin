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

package org.apache.zeppelin.user.properties;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Map;

import org.junit.Test;

public class PropertyTest {
  @Test
  public void testProperties() throws IOException {
    UserProperties userProperties = new UserProperties(false, null);
    userProperties.put("user1", "key1", "value1");
    userProperties.put("user1", "key2", "value2");
    userProperties.put("user2", "key1", "value1");

    Map<String, String> user1Properties = userProperties.get("user1");
    
    assertEquals(user1Properties.size(), 2);
    assertEquals("value1", user1Properties.get("key1"));
    assertEquals("value2", user1Properties.get("key2"));

    userProperties.remove("user1", "key1");
    
    user1Properties = userProperties.get("user1");

    assertEquals(user1Properties.size(), 1);
    assertEquals("value2", user1Properties.get("key2"));

    userProperties.remove("user1");

    user1Properties = userProperties.get("user1");
    assertTrue(user1Properties.isEmpty());

    Map<String, String> user2Properties = userProperties.get("user2");
    assertEquals(user2Properties.size(), 1);
    assertEquals("value1", user2Properties.get("key1"));
    
    userProperties.put("user2", "key1", "newvalue1");
    
    user2Properties = userProperties.get("user2");
    assertEquals(user2Properties.size(), 1);
    assertEquals("newvalue1", user2Properties.get("key1"));
  }

}
