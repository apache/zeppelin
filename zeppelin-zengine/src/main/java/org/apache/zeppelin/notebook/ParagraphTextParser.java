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

package org.apache.zeppelin.notebook;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Parser which is used for parsing the paragraph text.
 * The parsed result will be in 3 parts:
 * 1. interpreter text
 * 2. script text
 * 3. paragraph local properties
 *
 * e.g.
 * %spark(pool=pool_1) sc.version
 *
 * The above text will be parsed into 3 parts:
 *
 * intpText: spark
 * scriptText: sc.version
 * localProperties: Map(pool->pool_1)
 */
public class ParagraphTextParser {

  public static class ParseResult {
    private String intpText;
    private String scriptText;
    private Map<String, String> localProperties;

    public ParseResult(String intpText, String scriptText, Map<String, String> localProperties) {
      this.intpText = intpText;
      this.scriptText = scriptText;
      this.localProperties = localProperties;
    }

    public String getIntpText() {
      return intpText;
    }

    public String getScriptText() {
      return scriptText;
    }

    public Map<String, String> getLocalProperties() {
      return localProperties;
    }
  }

  private static Pattern REPL_PATTERN =
          Pattern.compile("(\\s*)%([\\w\\.]+)(\\(.*?\\))?.*", Pattern.DOTALL);

  public static ParseResult parse(String text) {
    Map<String, String> localProperties = new HashMap<>();
    String intpText = null;
    String scriptText = null;

    Matcher matcher = REPL_PATTERN.matcher(text);
    if (matcher.matches()) {
      String headingSpace = matcher.group(1);
      intpText = matcher.group(2);
      if (matcher.groupCount() == 3 && matcher.group(3) != null) {
        String localPropertiesText = matcher.group(3);
        String[] splits = localPropertiesText.substring(1, localPropertiesText.length() - 1)
                .split(",");
        for (String split : splits) {
          String[] kv = split.split("=");
          if (StringUtils.isBlank(split) || kv.length == 0) {
            continue;
          }
          if (kv.length > 2) {
            throw new RuntimeException("Invalid paragraph properties format: " + split);
          }
          if (kv.length == 1) {
            localProperties.put(kv[0].trim(), kv[0].trim());
          } else {
            localProperties.put(kv[0].trim(), kv[1].trim());
          }
        }
        scriptText = text.substring(headingSpace.length() + intpText.length() +
                localPropertiesText.length() + 1).trim();
      } else {
        scriptText = text.substring(headingSpace.length() + intpText.length() + 1).trim();
      }
    } else {
      intpText = "";
      scriptText = text;
    }
    return new ParseResult(intpText, scriptText, localProperties);
  }

}
