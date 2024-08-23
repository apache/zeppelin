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
package org.apache.zeppelin.cluster.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClusterMessageSerDeserTest {

    private ClusterMessage clusterMessage;

    @BeforeEach
    void setUp() {
        clusterMessage = new ClusterMessage(ClusterEvent.BROADCAST_NEW_PARAGRAPH);
    }

    @Test
    void toJson() {
        // Given
        clusterMessage.put("key1", "value1");
        clusterMessage.put("key2", "value2");
        clusterMessage.setMsgId(UUID.randomUUID().toString());

        // First, let's check the serialized JSON serialization
        String serializedJSON = ClusterMessage.serializeMessage(clusterMessage);
        assertEquals(
                "{\n" +
                        "  \"clusterEvent\": \"BROADCAST_NEW_PARAGRAPH\",\n" +
                        "  \"data\": {\n" +
                        "    \"key1\": \"value1\",\n" +
                        "    \"key2\": \"value2\"\n" +
                        "  },\n" +
                        "  \"msgId\": \"" + clusterMessage.getMsgId() + "\"\n" +
                        "}",
                serializedJSON);

        // Now, let's check the serialized JSON deserialization
        ClusterMessage deserialized = ClusterMessage.deserializeMessage(serializedJSON);
        assertEquals(clusterMessage.clusterEvent, deserialized.clusterEvent);
        assertEquals(clusterMessage.getData(), deserialized.getData());
        assertEquals(clusterMessage.getMsgId(), deserialized.getMsgId());
    }

    @Test
    void fromJson_withInvalidField() {
        String invalidJson = "{" +
                "\"clusterEvent\":\"SET_RUNNERS_PERMISSIONS\"," +
                "\"data\":{\"key1\":\"value1\",\"key2\":\"value2\"}," +
                "\"invalidField\":\"invalidValue\"" +
                "}";

        ClusterMessage deserialized = ClusterMessage.deserializeMessage(invalidJson);
        assertEquals(ClusterEvent.SET_RUNNERS_PERMISSIONS, deserialized.clusterEvent);
        assertEquals(mapOf("key1", "value1", "key2", "value2"), deserialized.getData());
        assertNull(deserialized.getMsgId());
    }

    @Test
    void fromJson_withNothing() {
        // When only the empty JSON object is provided,
        ClusterMessage deserialized = ClusterMessage.deserializeMessage("{}");

        // Then, all fields should be null or empty
        assertNull(deserialized.clusterEvent);
        assertNull(deserialized.getData());
        assertNull(deserialized.getMsgId());
    }

    @Test
    void fromJson_withNullFields() {
        String jsonWithNullFields = "{" +
                "\"clusterEvent\":null," +
                "\"data\":null," +
                "\"msgId\":null" +
                "}";

        ClusterMessage deserialized = ClusterMessage.deserializeMessage(jsonWithNullFields);
        assertNull(deserialized.clusterEvent);
        assertNull(deserialized.getData());
        assertNull(deserialized.getMsgId());
    }

    @Test
    void toJson_withEmptyData() {
        ClusterMessage original = new ClusterMessage(ClusterEvent.CLEAR_PERMISSION);
        original.setMsgId("");
        original.getData().clear();

        String serializedJSON = ClusterMessage.serializeMessage(original);
        assertEquals(
                "{\n" +
                        "  \"clusterEvent\": \"CLEAR_PERMISSION\",\n" +
                        "  \"data\": {},\n" +
                        "  \"msgId\": \"\"\n" +
                        "}",
                serializedJSON);

        ClusterMessage deserialized = ClusterMessage.deserializeMessage(serializedJSON);
        assertEquals(original.clusterEvent, deserialized.clusterEvent);
        assertEquals(original.getData(), deserialized.getData());
        assertEquals(original.getMsgId(), deserialized.getMsgId());
    }

    @Test
    void fromJson_withLargeDataSet() {
        Map<String, String> largeData = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            largeData.put("key" + i, "value" + i);
        }

        ClusterMessage original = new ClusterMessage(ClusterEvent.SET_READERS_PERMISSIONS);
        original.setMsgId(UUID.randomUUID().toString());
        original.put(largeData);

        String serializedJSON = ClusterMessage.serializeMessage(original);
        ClusterMessage deserialized = ClusterMessage.deserializeMessage(serializedJSON);

        assertEquals(original.clusterEvent, deserialized.clusterEvent);
        assertEquals(original.getData(), deserialized.getData());
        assertEquals(original.getMsgId(), deserialized.getMsgId());
    }

    private Map<String, String> mapOf(
            String key, String value,
            String key2, String value2
    ) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        map.put(key2, value2);
        return map;
    }

}
