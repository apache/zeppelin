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

package org.apache.zeppelin.utils;

import org.apache.zeppelin.util.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link CommandLineUtils} written in Given-When-Then style.
 */
class CommandLineUtilsTest {

    /**
     * Captures standard output during test execution.
     */
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    /**
     * Original System.out kept for restoration.
     */
    private final PrintStream originalOut = System.out;

    @AfterEach
    void restoreStdout() {
        // Restore the original System.out after each test
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("Given --version flag, when main is executed, then version is printed")
    void givenVersionFlag_whenMain_thenPrintsVersion() {
        // ----- Given -----
        System.setOut(new PrintStream(outContent));
        String expected = Util.getVersion();         // whatever the project returns

        // ----- When -----
        CommandLineUtils.main(new String[]{"--version"});

        // ----- Then -----
        assertEquals(expected, outContent.toString().trim());
    }

    @Test
    @DisplayName("Given -v flag, when main is executed, then version is printed")
    void givenShortVersionFlag_whenMain_thenPrintsVersion() {
        // ----- Given -----
        System.setOut(new PrintStream(outContent));
        String expected = Util.getVersion();

        // ----- When -----
        CommandLineUtils.main(new String[]{"-v"});

        // ----- Then -----
        assertEquals(expected, outContent.toString().trim());
    }

    @Test
    @DisplayName("Given no arguments, when main is executed, then nothing is printed")
    void givenNoArgs_whenMain_thenPrintsNothing() {
        // ----- Given -----
        System.setOut(new PrintStream(outContent));

        // ----- When -----
        CommandLineUtils.main(new String[]{});

        // ----- Then -----
        assertEquals("", outContent.toString().trim());
    }
}