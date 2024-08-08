package org.apache.zeppelin.conf;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ZeppelinConfigurationFieldTest {

    /**
     * Test method to verify that all 'private final ZeppelinConfiguration' fields
     * in the project's parent directory are named 'zConf'.
     *
     * @throws IOException if an I/O error occurs during file reading
     */
    @Test
    public void testZeppelinConfigurationFields() throws IOException {
        File projectRoot = new File(System.getProperty("user.dir"));
        File parentDirectory = projectRoot.getParentFile();
        boolean allFieldsCorrect = checkFilesInDirectory(parentDirectory);
        assertTrue(allFieldsCorrect, "Some private final ZeppelinConfiguration fields are not named 'zConf'");
    }

    /**
     * Recursively checks all Java files in the given directory to ensure
     * 'private final ZeppelinConfiguration' fields are named 'zConf'.
     *
     * @param dir the directory to check
     * @return true if all fields are named 'zConf', false otherwise
     * @throws IOException if an I/O error occurs
     */
    private boolean checkFilesInDirectory(File dir) throws IOException {
        boolean allFieldsCorrect = true;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    if (!checkFilesInDirectory(file)) {
                        allFieldsCorrect = false;
                    }
                } else if (file.getName().endsWith(".java")) {
                    if (!checkJavaFile(file)) {
                        allFieldsCorrect = false;
                    }
                }
            }
        }
        return allFieldsCorrect;
    }

    /**
     * Checks if a given Java file contains 'private final ZeppelinConfiguration' fields
     * named 'zConf'.
     *
     * @param file the Java file to check
     * @return true if the field is named 'zConf', false otherwise
     * @throws IOException if an I/O error occurs
     */
    private boolean checkJavaFile(File file) throws IOException {
        boolean allFieldsCorrect = true;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Pattern pattern = Pattern.compile("private\\s+final\\s+ZeppelinConfiguration\\s+(\\w+);");
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String fieldName = matcher.group(1);
                    if (!"zConf".equals(fieldName)) {
                        System.err.println("File " + file.getPath() + " contains private final ZeppelinConfiguration field not named 'zConf'");
                        allFieldsCorrect = false;
                    }
                }
            }
        }
        return allFieldsCorrect;
    }
}
