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

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HeliumRegistrySerializerTest {

    private String HELIUM_PACKAGE_JSON_1 = "{\n" +
            "  \"name\" : \"[organization.A].[name.A]\",\n" +
            "  \"description\" : \"Description-A\",\n" +
            "  \"artifact\" : \"groupId:artifactId:version\",\n" +
            "  \"className\" : \"your.package.name.YourApplicationClass-A\",\n" +
            "  \"resources\" : [\n" +
            "    [\"resource.name\", \":resource.class.name\"],\n" +
            "    [\"alternative.resource.name\", \":alternative.class.name\"]\n" +
            "  ],\n" +
            "  \"icon\" : \"<i class='icon'></i>\"\n" +
            "}";

    @Test
    void testDeserialization() throws IOException {
        // Given
        Path dir = Files.createDirectory(Paths.get("./" + UUID.randomUUID()));
        FileUtils.forceDeleteOnExit(dir.toFile());
        File newFile = Files.createTempFile(dir, UUID.randomUUID().toString(), ".json").toFile();
        FileUtils.forceDeleteOnExit(newFile);

        // When
        // Define the output file
        FileUtils.writeStringToFile(newFile, HELIUM_PACKAGE_JSON_1, StandardCharsets.UTF_8);
        // Write JSON string to file using Apache Commons FileUtils
        HeliumRegistry registry = new HeliumLocalRegistry("my-registry", dir.toString());

        // Then
        assertEquals("my-registry", registry.name());
        assertNotNull(registry.getAll());
        assertEquals(1, registry.getAll().size());

        HeliumPackage heliumPackage = registry.getAll().get(0);
        assertEquals("[organization.A].[name.A]", heliumPackage.getName());
        assertEquals("Description-A", heliumPackage.getDescription());
        assertEquals("groupId:artifactId:version", heliumPackage.getArtifact());
        assertEquals("your.package.name.YourApplicationClass-A", heliumPackage.getClassName());
        assertEquals(2, heliumPackage.getResources().length);
        assertEquals("resource.name", heliumPackage.getResources()[0][0]);
        assertEquals(":resource.class.name", heliumPackage.getResources()[0][1]);
        assertEquals("alternative.resource.name", heliumPackage.getResources()[1][0]);
        assertEquals(":alternative.class.name", heliumPackage.getResources()[1][1]);
        assertEquals("<i class='icon'></i>", heliumPackage.getIcon());
    }

}
