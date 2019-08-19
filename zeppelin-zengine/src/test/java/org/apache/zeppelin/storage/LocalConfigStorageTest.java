package org.apache.zeppelin.storage;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

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