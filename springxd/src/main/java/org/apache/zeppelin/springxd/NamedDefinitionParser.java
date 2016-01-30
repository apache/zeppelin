/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.springxd;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Splits the input line into a "name" and a "definition" parts, divided by the '=' character. This
 * is used for SpringXD's stream and job definitions.
 */
public class NamedDefinitionParser {

  private static final Pattern NAMED_STREAM_PATTERN = Pattern.compile("\\s*(\\w+)\\s*=\\s*(.*)");
  private static final int NAME_GROUP_INDEX = 1;
  private static final int DEFINITION_GROUP_INDEX = 2;

  /**
   * Splits the input line into a name and a definition parts, divided by the '=' character.
   * 
   * @param line - Input line in the 'name = definition' format
   * @return Returns a (name, definition) pair. Returns an empty pair ("", "") when the input line
   *         doesn't comply with the name = definition convention.
   */
  public static Pair<String, String> getNamedDefinition(String line) {

    String name = "";
    String definition = "";

    if (!isBlank(line)) {
      Matcher matcher = NAMED_STREAM_PATTERN.matcher(line);

      if (matcher.matches()) {
        name = matcher.group(NAME_GROUP_INDEX);
        definition = matcher.group(DEFINITION_GROUP_INDEX);
      }
    }
    return new ImmutablePair<String, String>(name, definition);
  }
}
