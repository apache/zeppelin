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

package org.apache.zeppelin.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.zeppelin.common.Message.OP;
import org.junit.jupiter.api.Test;

class MessageContractTest {
  @Test
  void operationNamesRoundTripWithoutChangingWireValues() {
    for (OP operation : OP.values()) {
      Message message = new Message(operation);
      JsonObject json = JsonParser.parseString(message.toJson()).getAsJsonObject();
      assertEquals(operation.name(), json.get("op").getAsString());
      assertEquals(operation, Message.fromJson(json.toString()).op);
    }
  }

  @Test
  void messageDefaultsRemainBackwardCompatible() {
    Message message = new Message(OP.GET_NOTE);

    assertTrue(message.data.isEmpty());
    assertEquals("anonymous", message.ticket);
    assertEquals("anonymous", message.principal);
    assertEquals("", message.roles);
    assertNull(message.msgId);

    JsonObject json = JsonParser.parseString(message.toJson()).getAsJsonObject();
    assertEquals("GET_NOTE", json.get("op").getAsString());
    assertTrue(json.getAsJsonObject("data").entrySet().isEmpty());
    assertEquals("anonymous", json.get("ticket").getAsString());
    assertEquals("anonymous", json.get("principal").getAsString());
    assertEquals("", json.get("roles").getAsString());
    assertFalse(json.has("msgId"));
  }
}
