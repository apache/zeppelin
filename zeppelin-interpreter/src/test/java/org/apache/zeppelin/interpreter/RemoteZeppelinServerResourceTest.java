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
package org.apache.zeppelin.interpreter;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.apache.zeppelin.interpreter.RemoteZeppelinServerResource.Type.PARAGRAPH_RUNNERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RemoteZeppelinServerResourceTest {

    @Test
    void toJson() {
        // Given
        RemoteZeppelinServerResource original = new RemoteZeppelinServerResource();
        original.setOwnerKey(UUID.randomUUID().toString());
        original.setData(mapOf("key1", "value", "key2", "value2"));
        original.setResourceType(PARAGRAPH_RUNNERS);

        // First, let's check the serializedJson serialization
        String serializedJSON = original.toJson();
        assertEquals("{" +
                "\"ownerKey\":\"" + original.getOwnerKey() + "\"," +
                "\"resourceType\":\"PARAGRAPH_RUNNERS\"," +
                "\"data\":{\"key1\":\"value\",\"key2\":\"value2\"}" +
                "}", serializedJSON);

        // Now, let's check the serializedJson deserialization
        RemoteZeppelinServerResource deserialized = RemoteZeppelinServerResource.fromJson(serializedJSON);
        assertEquals(original.getOwnerKey(), deserialized.getOwnerKey());
        assertEquals(original.getResourceType(), deserialized.getResourceType());
        assertEquals(original.getData(), deserialized.getData());
    }

    @Test
    void fromJson_withInvalidResourceType() {
        String invalidJson = "{" +
                "\"ownerKey\":\"1234\"," +
                "\"resourceType\":\"INVALID_TYPE\"," +
                "\"data\":{\"key1\":\"value\",\"key2\":\"value2\"}" +
                "}";

        RemoteZeppelinServerResource deserialized = RemoteZeppelinServerResource.fromJson(invalidJson);
        // Deserialization is successful and type is null
        assertNull(deserialized.getResourceType());
        // Owner key and data are deserialized correctly
        assertEquals("1234", deserialized.getOwnerKey());
        assertEquals(mapOf("key1", "value", "key2", "value2"), deserialized.getData());
    }

    @Test
    void fromJson_withNothing() {
        // When only the empty JSON object is provided,
        RemoteZeppelinServerResource deserialized = RemoteZeppelinServerResource.fromJson("{}");

        // Then, all fields should be null
        assertNull(deserialized.getOwnerKey());
        assertNull(deserialized.getResourceType());
        assertNull(deserialized.getData());
    }

    @Test
    void fromJson_withNullFields() {
        String jsonWithNullFields = "{" +
                "\"ownerKey\":null," +
                "\"resourceType\":null," +
                "\"data\":null" +
                "}";

        RemoteZeppelinServerResource deserialized = RemoteZeppelinServerResource.fromJson(jsonWithNullFields);
        assertNull(deserialized.getOwnerKey());
        assertNull(deserialized.getResourceType());
        assertNull(deserialized.getData());
    }

    @Test
    void toJson_withEmptyData() {
        RemoteZeppelinServerResource original = new RemoteZeppelinServerResource();
        original.setOwnerKey("");
        original.setData(new TreeMap<>());
        original.setResourceType(PARAGRAPH_RUNNERS);

        String serializedJSON = original.toJson();
        assertEquals("{" +
                "\"ownerKey\":\"\"," +
                "\"resourceType\":\"PARAGRAPH_RUNNERS\"," +
                "\"data\":{}" +
                "}", serializedJSON);

        RemoteZeppelinServerResource deserialized = RemoteZeppelinServerResource.fromJson(serializedJSON);
        assertEquals(original.getOwnerKey(), deserialized.getOwnerKey());
        assertEquals(original.getResourceType(), deserialized.getResourceType());
        assertEquals(original.getData(), deserialized.getData());
    }

    @Test
    void fromJson_withLargeDataSet() {
        Map<String, String> largeData = new TreeMap<>();
        for (int i = 0; i < 1000; i++) {
            largeData.put("key" + i, "value" + i);
        }

        RemoteZeppelinServerResource original = new RemoteZeppelinServerResource();
        original.setOwnerKey(UUID.randomUUID().toString());
        original.setData(largeData);
        original.setResourceType(PARAGRAPH_RUNNERS);

        String serializedJSON = original.toJson();
        RemoteZeppelinServerResource deserialized = RemoteZeppelinServerResource.fromJson(serializedJSON);

        assertEquals(original.getOwnerKey(), deserialized.getOwnerKey());
        assertEquals(original.getResourceType(), deserialized.getResourceType());
        assertEquals(original.getData(), deserialized.getData());
    }

    private Map<String, String> mapOf(
            String key, String value,
            String key2, String value2
    ) {
        // TreeMap to ensure serialization order
        Map<String, String> map = new TreeMap<>();
        map.put(key, value);
        map.put(key2, value2);
        return map;
    }

}
