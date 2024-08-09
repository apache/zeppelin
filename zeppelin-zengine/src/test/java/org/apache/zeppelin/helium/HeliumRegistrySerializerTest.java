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
package org.apache.zeppelin.helium;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HeliumRegistrySerializerTest {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(HeliumRegistry.class, new HeliumRegistrySerializer())
            .create();

    @Test
    void testSerialization() {
        // Given
        HeliumRegistry registry = new SimpleHeliumRegistry("TestRegistry", "http://test.uri");

        // When
        String json = gson.toJson(registry);

        // Then
        String expectedJson = "{\"uri\":\"http://test.uri\",\"name\":\"TestRegistry\"}";
        assertEquals(expectedJson, json);
    }

    @Test
    void testDeserialization() {
        // Given
        String json = "{\"class\":\"org.apache.zeppelin.helium.SimpleHeliumRegistry\",\"uri\":\"http://test.uri\",\"name\":\"TestRegistry\"}";

        // When
        HeliumRegistry registry = gson.fromJson(json, HeliumRegistry.class);

        // Then
        assertNotNull(registry);
        assertEquals("TestRegistry", registry.name());
        assertEquals("http://test.uri", registry.uri());
    }

    public static class SimpleHeliumRegistry extends HeliumRegistry {
        public SimpleHeliumRegistry(String name, String uri) {
            super(name, uri);
        }

        @Override
        public List<HeliumPackage> getAll() throws IOException {
            return Collections.emptyList();
        }
    }

}
