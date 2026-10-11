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
package org.apache.zeppelin.notebook.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Command line options for {@link NotebookRunner}: {@code -i <notePath>} (required),
 * {@code -o <outputPath>} (optional), {@code -p <key> <value>} (repeatable). Manual switch-based
 * parsing, following the same style as {@code InstallInterpreter.main}.
 */
public final class RunNoteCliOptions {

  private static final Logger LOGGER = LoggerFactory.getLogger(RunNoteCliOptions.class);

  private final String notePath;
  private final String outputPath;
  private final Map<String, Object> params;

  private RunNoteCliOptions(String notePath, String outputPath, Map<String, Object> params) {
    this.notePath = notePath;
    this.outputPath = outputPath;
    this.params = params;
  }

  public String getNotePath() {
    return notePath;
  }

  /**
   * @return the output note path, or {@code null} when the input note should be overwritten.
   */
  public String getOutputPath() {
    return outputPath;
  }

  public Map<String, Object> getParams() {
    return params;
  }

  public static void printUsage() {
    System.out.println("Usage: run-note.sh -i <notePath> [-o <outputPath>] [-p <key> <value>]...");
    System.out.println("Options");
    System.out.println("  -i, --input   [PATH]         Path of the note to run (required)");
    System.out.println("  -o, --output  [PATH]         Path to save the executed note to. "
        + "Defaults to overwriting the input note");
    System.out.println("  -p, --param   [KEY] [VALUE]  Note parameter, can be repeated");
    System.out.println("  -h, --help                    Print this help");
  }

  /**
   * @return the parsed options, or {@code null} when {@code -h}/{@code --help} was given (usage
   *     already printed and the caller should exit without running anything).
   * @throws IllegalArgumentException when required options are missing or an option is unknown.
   */
  public static RunNoteCliOptions parse(String[] args) {
    String notePath = null;
    String outputPath = null;
    Map<String, Object> params = new LinkedHashMap<>();

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      switch (arg) {
        case "-i":
        case "--input":
          if (i + 1 >= args.length) {
            throw new IllegalArgumentException("Missing value for " + arg);
          }
          notePath = args[++i];
          break;
        case "-o":
        case "--output":
          if (i + 1 >= args.length) {
            throw new IllegalArgumentException("Missing value for " + arg);
          }
          outputPath = args[++i];
          break;
        case "-p":
        case "--param":
          if (i + 2 >= args.length) {
            throw new IllegalArgumentException("Missing key/value for " + arg);
          }
          String key = args[++i];
          String value = args[++i];
          if (params.containsKey(key)) {
            LOGGER.warn("Duplicate -p key '{}': overwriting previous value", key);
          }
          params.put(key, value);
          break;
        case "-h":
        case "--help":
          printUsage();
          return null;
        default:
          throw new IllegalArgumentException("Unknown option: " + arg);
      }
    }

    if (notePath == null) {
      throw new IllegalArgumentException("-i <notePath> is required");
    }

    return new RunNoteCliOptions(notePath, outputPath, params);
  }
}
