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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final Logger LOGGER = LoggerFactory.getLogger(ParagraphTextParser.class);

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

  private static Pattern REPL_PATTERN = Pattern.compile("^(\\s*)%(\\w+(?:\\.\\w+)*)");

  private static int parseLocalProperties(
          final String text, int startPos,
          Map<String, String> localProperties) throws  RuntimeException{
    startPos++;
    String propKey = null;
    boolean insideQuotes = false, parseKey = true, finished = false;
    StringBuilder sb = new StringBuilder();
    while(!finished && startPos < text.length()) {
      char ch = text.charAt(startPos);
      switch (ch) {
        case ')': {
          if (!insideQuotes) {
            if (parseKey) {
              propKey = sb.toString().trim();
              if (!propKey.isEmpty()) {
                localProperties.put(propKey, propKey);
              }
            } else {
              localProperties.put(propKey, sb.toString().trim());
            }
            finished = true;
          } else {
            sb.append(ch);
          }
          break;
        }
        case '\\': {
          if ((startPos + 1) == text.length()) {
            throw new RuntimeException(
                    "Problems by parsing paragraph. Unfinished escape sequence");
          }
          startPos++;
          ch = text.charAt(startPos);
          switch (ch) {
            case 'n':
              sb.append('\n');
              break;
            case 't':
              sb.append('\t');
              break;
            default:
              sb.append(ch);
          }
          break;
        }
        case '"': {
          insideQuotes = !insideQuotes;
          break;
        }
        case '=': {
          if (insideQuotes) {
            sb.append(ch);
          } else {
            if (!parseKey) {
              throw new RuntimeException(
                      "Invalid paragraph properties format");
            }
            propKey = sb.toString().trim();
            sb.delete(0, sb.length());
            parseKey = false;
          }
          break;
        }
        case ',': {
          if (insideQuotes) {
            sb.append(ch);
          } else if (propKey == null || propKey.trim().isEmpty()) {
            throw new RuntimeException(
                    "Problems by parsing paragraph. Local property key is empty");
          } else {
            if (parseKey) {
              propKey = sb.toString().trim();
              localProperties.put(propKey, propKey);
            } else {
              localProperties.put(propKey, sb.toString().trim());
            }
            propKey = null;
            parseKey = true;
            sb.delete(0, sb.length());
          }
          break;
        }
        default:
          sb.append(ch);
      }
      startPos++;
    }
    if (!finished) {
      throw new RuntimeException(
              "Problems by parsing paragraph. Not finished interpreter configuration");
    }
    return startPos;
  }

  public static ParseResult parse(String text) {
    Map<String, String> localProperties = new HashMap<>();
    String intpText = "";
    String scriptText = null;

    Matcher matcher = REPL_PATTERN.matcher(text);
    if (matcher.find()) {
      String headingSpace = matcher.group(1);
      intpText = matcher.group(2);
      int startPos = headingSpace.length() + intpText.length() + 1;
      if (startPos < text.length() && text.charAt(startPos) == '(') {
        startPos = parseLocalProperties(text, startPos, localProperties);
      }
      scriptText = text.substring(startPos).trim();
    } else {
      intpText = "";
      scriptText = text.trim();
    }

    return new ParseResult(intpText, scriptText, localProperties);
  }

}
