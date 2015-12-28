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

package org.apache.zeppelin.display;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Input type.
 */
public class Input implements Serializable {
  /**
   * Parameters option.
   */
  public static class ParamOption {
    Object value;
    String displayName;

    public ParamOption(Object value, String displayName) {
      super();
      this.value = value;
      this.displayName = displayName;
    }

    public Object getValue() {
      return value;
    }

    public void setValue(Object value) {
      this.value = value;
    }

    public String getDisplayName() {
      return displayName;
    }

    public void setDisplayName(String displayName) {
      this.displayName = displayName;
    }

  }

  String name;
  String displayName;
  String type;
  Object defaultValue;
  ParamOption[] options;
  boolean hidden;

  public Input(String name, Object defaultValue) {
    this.name = name;
    this.displayName = name;
    this.defaultValue = defaultValue;
  }

  public Input(String name, Object defaultValue, ParamOption[] options) {
    this.name = name;
    this.displayName = name;
    this.defaultValue = defaultValue;
    this.options = options;
  }


  public Input(String name, String displayName, String type, Object defaultValue,
      ParamOption[] options, boolean hidden) {
    super();
    this.name = name;
    this.displayName = displayName;
    this.type = type;
    this.defaultValue = defaultValue;
    this.options = options;
    this.hidden = hidden;
  }

  @Override
  public boolean equals(Object o) {
    return name.equals(((Input) o).getName());
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Object getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(Object defaultValue) {
    this.defaultValue = defaultValue;
  }

  public ParamOption[] getOptions() {
    return options;
  }

  public void setOptions(ParamOption[] options) {
    this.options = options;
  }

  public boolean isHidden() {
    return hidden;
  }


  private static String[] getNameAndDisplayName(String str) {
    Pattern p = Pattern.compile("([^(]*)\\s*[(]([^)]*)[)]");
    Matcher m = p.matcher(str.trim());
    if (m == null || m.find() == false) {
      return null;
    }
    String[] ret = new String[2];
    ret[0] = m.group(1);
    ret[1] = m.group(2);
    return ret;
  }

  private static String[] getType(String str) {
    Pattern p = Pattern.compile("([^:]*)\\s*:\\s*(.*)");
    Matcher m = p.matcher(str.trim());
    if (m == null || m.find() == false) {
      return null;
    }
    String[] ret = new String[2];
    ret[0] = m.group(1).trim();
    ret[1] = m.group(2).trim();
    return ret;
  }

  public static Map<String, Input> extractSimpleQueryParam(String script) {
    Map<String, Input> params = new HashMap<String, Input>();
    if (script == null) {
      return params;
    }
    String replaced = script;

    Pattern pattern = Pattern.compile("([_])?[$][{]([^=}]*([=][^}]*)?)[}]");

    Matcher match = pattern.matcher(replaced);
    while (match.find()) {
      String hiddenPart = match.group(1);
      boolean hidden = false;
      if ("_".equals(hiddenPart)) {
        hidden = true;
      }
      String m = match.group(2);

      String namePart;
      String valuePart;

      int p = m.indexOf('=');
      if (p > 0) {
        namePart = m.substring(0, p);
        valuePart = m.substring(p + 1);
      } else {
        namePart = m;
        valuePart = null;
      }


      String varName;
      String displayName = null;
      String type = null;
      String defaultValue = "";
      ParamOption[] paramOptions = null;

      // get var name type
      String varNamePart;
      String[] typeArray = getType(namePart);
      if (typeArray != null) {
        type = typeArray[0];
        varNamePart = typeArray[1];
      } else {
        varNamePart = namePart;
      }

      // get var name and displayname
      String[] varNameArray = getNameAndDisplayName(varNamePart);
      if (varNameArray != null) {
        varName = varNameArray[0];
        displayName = varNameArray[1];
      } else {
        varName = varNamePart.trim();
      }

      // get defaultValue
      if (valuePart != null) {
        // find default value
        int optionP = valuePart.indexOf(",");
        if (optionP > 0) { // option available
          defaultValue = valuePart.substring(0, optionP);
          String optionPart = valuePart.substring(optionP + 1);
          String[] options = Input.splitPipe(optionPart);

          paramOptions = new ParamOption[options.length];

          for (int i = 0; i < options.length; i++) {

            String[] optNameArray = getNameAndDisplayName(options[i]);
            if (optNameArray != null) {
              paramOptions[i] = new ParamOption(optNameArray[0], optNameArray[1]);
            } else {
              paramOptions[i] = new ParamOption(options[i], null);
            }
          }


        } else { // no option
          defaultValue = valuePart;
        }

      }

      Input param = new Input(varName, displayName, type, defaultValue, paramOptions, hidden);
      params.put(varName, param);
    }

    params.remove("pql");
    return params;
  }

  public static String getSimpleQuery(Map<String, Object> params, String script) {
    String replaced = script;

    for (String key : params.keySet()) {
      Object value = params.get(key);
      replaced =
          replaced.replaceAll("[_]?[$][{]([^:]*[:])?" + key + "([(][^)]*[)])?(=[^}]*)?[}]",
                              value.toString());
    }

    Pattern pattern = Pattern.compile("[$][{]([^=}]*[=][^}]*)[}]");
    while (true) {
      Matcher match = pattern.matcher(replaced);
      if (match != null && match.find()) {
        String m = match.group(1);
        int p = m.indexOf('=');
        String replacement = m.substring(p + 1);
        int optionP = replacement.indexOf(",");
        if (optionP > 0) {
          replacement = replacement.substring(0, optionP);
        }
        replaced =
            replaced.replaceFirst("[_]?[$][{]"
                + m.replaceAll("[(]", ".").replaceAll("[)]", ".").replaceAll("[|]", ".") + "[}]",
                replacement);
      } else {
        break;
      }
    }

    replaced = replaced.replace("[_]?[$][{]([^=}]*)[}]", "");
    return replaced;
  }


  public static String[] split(String str) {
    return str.split(";(?=([^\"']*\"[^\"']*\")*[^\"']*$)");

  }

  /*
   * public static String [] splitPipe(String str){ //return
   * str.split("\\|(?=([^\"']*\"[^\"']*\")*[^\"']*$)"); return
   * str.split("\\|(?=([^\"']*\"[^\"']*\")*[^\"']*$)"); }
   */


  public static String[] splitPipe(String str) {
    return split(str, '|');
  }

  public static String[] split(String str, char split) {
    return split(str, new String[] {String.valueOf(split)}, false);
  }

  public static String[] split(String str, String[] splitters, boolean includeSplitter) {
    String escapeSeq = "\"',;${}";
    char escapeChar = '\\';

    String[] blockStart = new String[] {"\"", "'", "${", "N_(", "N_<"};
    String[] blockEnd = new String[] {"\"", "'", "}", "N_)", "N_>"};

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
          int curentLenght = i + splitter.length();
          if (splitter.compareTo(str.substring(i, Math.min(curentLenght, str.length()))) == 0) {
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
}
