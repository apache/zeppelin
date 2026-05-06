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
package org.apache.zeppelin.notebook.repo;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Note-path validation helpers shared by {@link NotebookRepo} implementations
 * and the service layer. A {@code final} class with {@code static} methods
 * (rather than {@link NotebookRepo} default methods) prevents an
 * implementation from accidentally — or intentionally — overriding the
 * checks and bypassing them.
 */
public final class NotebookPathValidator {

  private static final Pattern PATH_SEGMENT_SPLIT = Pattern.compile("/+");
  private static final String PARENT_SEGMENT = "..";
  private static final String CURRENT_SEGMENT = ".";
  private static final int MAX_DECODE_LAYERS = 5;

  private NotebookPathValidator() {
  }

  /**
   * Refuses any {@code ..} or {@code .} segment in {@code notePath}. The
   * input is URL-decoded repeatedly first so that variants such as
   * {@code %2e%2e} or {@code %252e%252e} cannot bypass the check.
   *
   * @throws IOException if the path is null, contains a traversal segment,
   *     or has more URL-encoding layers than {@value #MAX_DECODE_LAYERS}
   */
  public static void rejectTraversalSegments(String notePath) throws IOException {
    if (notePath == null) {
      throw new IOException("Path must not be null");
    }
    String decoded = decodeRepeatedly(notePath);
    String stripped = decoded.startsWith("/") ? decoded.substring(1) : decoded;
    for (String segment : PATH_SEGMENT_SPLIT.split(stripped)) {
      if (PARENT_SEGMENT.equals(segment) || CURRENT_SEGMENT.equals(segment)) {
        throw new IOException("Path traversal segments are not allowed: " + notePath);
      }
    }
  }

  /**
   * Repeatedly URL-decodes {@code encoded} until it stabilises, capped at
   * {@value #MAX_DECODE_LAYERS} layers.
   */
  public static String decodeRepeatedly(String encoded) throws IOException {
    String previous = encoded;
    for (int attempt = 0; attempt < MAX_DECODE_LAYERS; attempt++) {
      String decoded = URLDecoder.decode(previous, StandardCharsets.UTF_8);
      if (decoded.equals(previous)) {
        return decoded;
      }
      previous = decoded;
    }
    throw new IOException("Exceeded maximum decode attempts. Possible malicious input.");
  }
}
