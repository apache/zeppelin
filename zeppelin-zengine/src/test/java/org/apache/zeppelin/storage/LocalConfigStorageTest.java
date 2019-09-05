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

package org.apache.zeppelin.storage;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import static org.junit.Assert.*;

public class LocalConfigStorageTest {
    public static final String TEST_STRING = "this is a test!";

    @Test
    public void testWritingAtomically() throws IOException {
        final Path destination = Files.createTempFile("test-", "file");
        final File destinationFile = destination.toFile();
        try {
            LocalConfigStorage.atomicWriteToFile(TEST_STRING, destinationFile);
            try (InputStream is = Files.newInputStream(destination)) {
                String read = IOUtils.toString(is);
                assertEquals(TEST_STRING, read);
            }
        } finally {
            Files.deleteIfExists(destination);
        }
    }

    @Test
    public void testWritingAtomicallyNonExistingDir() throws IOException {
        Random rnd = new Random();
        final Path destDir = Paths.get(System.getProperty("java.io.tmpdir"), "non-existing-" + rnd.nextLong());
        final Path destination = Paths.get(destDir.toString(),"test-" + rnd.nextLong() + "-file");
        final File destinationFile = destination.toFile();
        try {
            LocalConfigStorage.atomicWriteToFile(TEST_STRING, destinationFile);
            try (InputStream is = Files.newInputStream(destination)) {
                String read = IOUtils.toString(is);
                assertEquals(TEST_STRING, read);
            }
        } finally {
            Files.deleteIfExists(destination);
            Files.deleteIfExists(destDir);
        }
    }

    @Test
    public void testReading() throws IOException {
        final Path destination = Files.createTempFile("test-", "file");
        final File destinationFile = destination.toFile();

        try {
            try (BufferedWriter writer = Files.newBufferedWriter(destination)) {
                writer.write(TEST_STRING);
            }
            String read = LocalConfigStorage.readFromFile(destinationFile);
            assertEquals(TEST_STRING, read);
        } finally {
            Files.deleteIfExists(destination);
        }
    }


}