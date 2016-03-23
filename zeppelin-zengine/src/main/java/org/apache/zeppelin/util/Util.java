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

package org.apache.zeppelin.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * TODO(moon) : add description.
 */
public class Util {
  private static final String PROJECT_PROPERTIES_VERSION_KEY = "version";

  private static Properties projectProperties;

  static {
    projectProperties = new Properties();
    try {
      projectProperties.load(Util.class.getResourceAsStream("/project.properties"));
    } catch (IOException e) {
      //Fail to read project.properties
    }
  }

  public static String[] split(String str, char split) {
    return split(str, new String[] {String.valueOf(split)}, false);
  }

  public static String[] split(String str, String[] splitters, boolean includeSplitter) {
    String escapeSeq = "\"',;<%>";
    char escapeChar = '\\';
    String[] blockStart = new String[] {"\"", "'", "<%", "N_<"};
    String[] blockEnd = new String[] {"\"", "'", "%>", "N_>"};

    return split(str, escapeSeq, escapeChar, blockStart, blockEnd, splitters, includeSplitter);

  }

  public static String[] split(String str, String escapeSeq, char escapeChar, String[] blockStart,
      String[] blockEnd, String[] splitters, boolean includeSplitter) {

    List<String> splits = new ArrayList<String>();

    String curString = "";

    boolean escape = false; // true when escape char is found
    int lastEscapeOffset = -1;
    int blockStartPos = -1;
    List<Integer> blockStack = new LinkedList<Integer>();

    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);

      // escape char detected
      if (c == escapeChar && escape == false) {
        escape = true;
        continue;
      }

      // escaped char comes
      if (escape == true) {
        if (escapeSeq.indexOf(c) < 0) {
          curString += escapeChar;
        }
        curString += c;
        escape = false;
        lastEscapeOffset = curString.length();
        continue;
      }

      if (blockStack.size() > 0) { // inside of block
        curString += c;
        // check multichar block
        boolean multicharBlockDetected = false;
        for (int b = 0; b < blockStart.length; b++) {
          if (blockStartPos >= 0
              && getBlockStr(blockStart[b]).compareTo(str.substring(blockStartPos, i)) == 0) {
            blockStack.remove(0);
            blockStack.add(0, b);
            multicharBlockDetected = true;
            break;
          }
        }
        if (multicharBlockDetected == true) {
          continue;
        }

        // check if current block is nestable
        if (isNestedBlock(blockStart[blockStack.get(0)]) == true) {
          // try to find nested block start

          if (curString.substring(lastEscapeOffset + 1).endsWith(
              getBlockStr(blockStart[blockStack.get(0)])) == true) {
            blockStack.add(0, blockStack.get(0)); // block is started
            blockStartPos = i;
            continue;
          }
        }

        // check if block is finishing
        if (curString.substring(lastEscapeOffset + 1).endsWith(
            getBlockStr(blockEnd[blockStack.get(0)]))) {
          // the block closer is one of the splitters (and not nested block)
          if (isNestedBlock(blockEnd[blockStack.get(0)]) == false) {
            for (String splitter : splitters) {
              if (splitter.compareTo(getBlockStr(blockEnd[blockStack.get(0)])) == 0) {
                splits.add(curString);
                if (includeSplitter == true) {
                  splits.add(splitter);
                }
                curString = "";
                lastEscapeOffset = -1;

                break;
              }
            }
          }
          blockStartPos = -1;
          blockStack.remove(0);
          continue;
        }

      } else { // not in the block
        boolean splitted = false;
        for (String splitter : splitters) {
          // forward check for splitter
          if (splitter.compareTo(
              str.substring(i, Math.min(i + splitter.length(), str.length()))) == 0) {
            splits.add(curString);
            if (includeSplitter == true) {
              splits.add(splitter);
            }
            curString = "";
            lastEscapeOffset = -1;
            i += splitter.length() - 1;
            splitted = true;
            break;
          }
        }
        if (splitted == true) {
          continue;
        }

        // add char to current string
        curString += c;

        // check if block is started
        for (int b = 0; b < blockStart.length; b++) {
          if (curString.substring(lastEscapeOffset + 1)
                       .endsWith(getBlockStr(blockStart[b])) == true) {
            blockStack.add(0, b); // block is started
            blockStartPos = i;
            break;
          }
        }
      }
    }
    if (curString.length() > 0) {
      splits.add(curString.trim());
    }
    return splits.toArray(new String[] {});

  }

  private static String getBlockStr(String blockDef) {
    if (blockDef.startsWith("N_")) {
      return blockDef.substring("N_".length());
    } else {
      return blockDef;
    }
  }

  private static boolean isNestedBlock(String blockDef) {
    if (blockDef.startsWith("N_")) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Get Zeppelin version
   *
   * @return Current Zeppelin version
   */
  public static String getVersion() {
    return StringUtils.defaultIfEmpty(projectProperties.getProperty(PROJECT_PROPERTIES_VERSION_KEY),
            StringUtils.EMPTY);
  }
}
