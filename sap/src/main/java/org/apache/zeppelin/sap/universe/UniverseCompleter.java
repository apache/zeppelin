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

package org.apache.zeppelin.sap.universe;

import jline.console.completer.ArgumentCompleter.ArgumentList;
import jline.console.completer.ArgumentCompleter.WhitespaceArgumentDelimiter;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.completer.CachedCompleter;
import org.apache.zeppelin.completer.CompletionType;
import org.apache.zeppelin.completer.StringsCompleter;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;

/**
 * SAP Universe auto complete functionality.
 */
public class UniverseCompleter {

  private static Logger logger = LoggerFactory.getLogger(UniverseCompleter.class);

  private static final String KEYWORD_SPLITERATOR = ",";
  public static final String CLEAN_NAME_REGEX = "\\[|\\]";
  public static final Character START_NAME = '[';
  public static final Character END_NAME = ']';
  public static final String KW_UNIVERSE = "universe";
  public static final String TYPE_FOLDER = "folder";

  private static final Comparator nodeInfoComparator = new Comparator<UniverseNodeInfo>() {
    @Override
    public int compare(UniverseNodeInfo o1, UniverseNodeInfo o2) {
      if (o1.getType().equalsIgnoreCase(TYPE_FOLDER)
          && o2.getType().equalsIgnoreCase(TYPE_FOLDER)) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
      if (o1.getType().equalsIgnoreCase(TYPE_FOLDER)) {
        return -1;
      }
      if (o2.getType().equalsIgnoreCase(TYPE_FOLDER)) {
        return 1;
      }
      if (!o1.getType().equalsIgnoreCase(o2.getType())) {
        return o1.getType().compareToIgnoreCase(o2.getType());
      } else {

        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    }
  };

  /**
   * Delimiter that can split keyword list
   */
  private WhitespaceArgumentDelimiter sqlDelimiter = new WhitespaceArgumentDelimiter() {

    private Pattern pattern = Pattern.compile(",|;");

    @Override
    public boolean isDelimiterChar(CharSequence buffer, int pos) {
      char c = buffer.charAt(pos);
      boolean endName = false;
      for (int i = pos; i > 0; i--) {
        char ch = buffer.charAt(i);
        if (ch == '\n') {
          break;
        }
        if (ch == START_NAME && !endName) {
          return false;
        }
        if (ch == END_NAME) {
          break;
        }
      }
      return pattern.matcher(StringUtils.EMPTY + buffer.charAt(pos)).matches()
              || super.isDelimiterChar(buffer, pos);
    }
  };

  /**
   * Universe completer
   */
  private CachedCompleter universeCompleter;

  /**
   * Keywords completer
   */
  private CachedCompleter keywordCompleter;

  /**
   * UniverseInfo completers
   */
  private Map<String, CachedCompleter> universeInfoCompletersMap = new HashMap<>();

  private int ttlInSeconds;

  public UniverseCompleter(int ttlInSeconds) {
    this.ttlInSeconds = ttlInSeconds;
  }

  public int complete(String buffer, int cursor, List<InterpreterCompletion> candidates) {
    CursorArgument cursorArgument = parseCursorArgument(buffer, cursor);

    String argument = cursorArgument.getCursorArgumentPartForComplete();
    if (cursorArgument.isUniverseNamePosition()) {
      List<CharSequence> universeCandidates = new ArrayList<>();
      universeCompleter.getCompleter().complete(argument, argument.length(),
          universeCandidates);
      addCompletions(candidates, universeCandidates, CompletionType.universe.name());
      return universeCandidates.size();
    }

    if (cursorArgument.isUniverseNodePosition()) {
      List universeNodeCandidates = new ArrayList();
      CachedCompleter completer = universeInfoCompletersMap.get(cursorArgument.getUniverse());
      if (completer != null) {
        completer.getCompleter().complete(argument, argument.length(), universeNodeCandidates);
      }
      Collections.sort(universeNodeCandidates, nodeInfoComparator);
      addCompletions(candidates, universeNodeCandidates);
      return universeNodeCandidates.size();
    }

    List<CharSequence> keywordCandidates = new ArrayList<>();
    keywordCompleter.getCompleter().complete(argument,
        argument.length() > 0 ? argument.length() : 0, keywordCandidates);
    addCompletions(candidates, keywordCandidates, CompletionType.keyword.name());

    return keywordCandidates.size();
  }

  public void createOrUpdate(UniverseClient client, String token, String buffer, int cursor) {
    try {
      CursorArgument cursorArgument = parseCursorArgument(buffer, cursor);
      if (keywordCompleter == null || keywordCompleter.getCompleter() == null
          || keywordCompleter.isExpired()) {
        Set<String> keywords = getKeywordsCompletions();
        if (keywords != null && !keywords.isEmpty()) {
          keywordCompleter = new CachedCompleter(new StringsCompleter(keywords), 0);
        }
      }
      if (cursorArgument.needLoadUniverses() || (universeCompleter == null
          || universeCompleter.getCompleter() == null || universeCompleter.isExpired())) {
        client.cleanUniverses();
        client.loadUniverses(token);
        if (client.getUniversesMap().size() > 0) {
          universeCompleter = new CachedCompleter(
              new StringsCompleter(client.getUniversesMap().keySet()), ttlInSeconds);
        }
      }
      if (cursorArgument.needLoadUniverseInfo() &&
          (!universeInfoCompletersMap.containsKey(cursorArgument.getUniverse()) ||
              universeInfoCompletersMap.get(cursorArgument.getUniverse()).getCompleter() == null ||
              universeInfoCompletersMap.get(cursorArgument.getUniverse()).isExpired())) {
        if (StringUtils.isNotBlank(cursorArgument.getUniverse())) {
          client.removeUniverseInfo(cursorArgument.getUniverse());
          Map<String, UniverseNodeInfo> info = client.getUniverseNodesInfo(token, cursorArgument
              .getUniverse());
          CachedCompleter completer = new CachedCompleter(
              new UniverseNodeInfoCompleter(info.values()), ttlInSeconds);
          universeInfoCompletersMap.put(cursorArgument.getUniverse(), completer);
        }
      }
    } catch (Exception e) {
      logger.error("Failed to update completions", e);
    }
  }

  private Set<String> getKeywordsCompletions() throws IOException {
    String keywords =
        new BufferedReader(new InputStreamReader(
            UniverseCompleter.class.getResourceAsStream("/universe.keywords"))).readLine();

    Set<String> completions = new TreeSet<>();

    if (StringUtils.isNotBlank(keywords)) {
      String[] words = keywords.split(KEYWORD_SPLITERATOR);
      for (String word : words) {
        completions.add(word);
      }
    }

    return completions;
  }

  private CursorArgument parseCursorArgument(String buffer, int cursor) {
    CursorArgument result = new CursorArgument();
    if (buffer != null && buffer.length() >= cursor) {
      String buf = buffer.substring(0, cursor);
      if (StringUtils.isNotBlank(buf)) {
        ArgumentList argList = sqlDelimiter.delimit(buf, cursor);
        int argIndex = argList.getCursorArgumentIndex();
        if (argIndex == 0) {
          result.setCursorArgumentPartForComplete(argList.getCursorArgument());
          return result;
        }

        if (argIndex > 0 && argList.getArguments()[argIndex - 1].equalsIgnoreCase(KW_UNIVERSE)) {
          result.setUniverseNamePosition(true);
          result.setCursorArgumentPartForComplete(cleanName(argList.getCursorArgument()
              .substring(0, argList.getArgumentPosition())));
          return result;
        }
        if (argIndex > 1) {
          for (int i = argIndex - 2; i >= 0; i--) {
            if (argList.getArguments()[i].equalsIgnoreCase(KW_UNIVERSE)) {
              result.setUniverse(cleanName(argList.getArguments()[i + 1]));
              break;
            }
          }

          if (StringUtils.isNotBlank(result.getUniverse())
              && argList.getCursorArgument().startsWith(START_NAME.toString())) {
            result.setCursorArgumentPartForComplete(
                argList.getCursorArgument().substring(0, argList.getArgumentPosition()));
            result.setUniverseNodePosition(true);
            return result;
          } else {
            result.setCursorArgumentPartForComplete(argList.getCursorArgument()
                .substring(0, argList.getArgumentPosition()));
          }
        }
      }
    }

    if (result.getCursorArgumentPartForComplete() == null) {
      result.setCursorArgumentPartForComplete(StringUtils.EMPTY);
    }

    return result;
  }

  private String cleanName(String name) {
    return name.replaceAll(CLEAN_NAME_REGEX, StringUtils.EMPTY);
  }

  private void addCompletions(List<InterpreterCompletion> interpreterCompletions,
                              List<CharSequence> candidates, String meta) {
    for (CharSequence candidate : candidates) {
      String value;
      if (meta.equalsIgnoreCase(CompletionType.universe.name())) {
        value = String.format("%s%s;\n", candidate.toString(), END_NAME);
      } else {
        value = candidate.toString();
      }
      interpreterCompletions.add(new InterpreterCompletion(candidate.toString(), value, meta));
    }
  }

  private void addCompletions(List<InterpreterCompletion> interpreterCompletions,
                              List<UniverseNodeInfo> candidates) {
    for (UniverseNodeInfo candidate : candidates) {
      String value;
      if (candidate.getType().equalsIgnoreCase(TYPE_FOLDER)) {
        value = String.format("%s%s.%s", candidate.getName(), END_NAME, START_NAME);
      } else {
        value = String.format("%s%s", candidate.getName(), END_NAME);
      }
      interpreterCompletions.add(new InterpreterCompletion(candidate.getName(), value,
          candidate.getType()));
    }
  }

  public CachedCompleter getUniverseCompleter() {
    return universeCompleter;
  }

  public Map<String, CachedCompleter> getUniverseInfoCompletersMap() {
    return universeInfoCompletersMap;
  }

  private class CursorArgument {
    private boolean universeNamePosition = false;
    private boolean universeNodePosition = false;
    private String universe;
    private String cursorArgumentPartForComplete;

    public boolean isUniverseNamePosition() {
      return universeNamePosition;
    }

    public void setUniverseNamePosition(boolean universeNamePosition) {
      this.universeNamePosition = universeNamePosition;
    }

    public boolean isUniverseNodePosition() {
      return universeNodePosition;
    }

    public void setUniverseNodePosition(boolean universeNodePosition) {
      this.universeNodePosition = universeNodePosition;
    }

    public String getCursorArgumentPartForComplete() {
      return cursorArgumentPartForComplete;
    }

    public void setCursorArgumentPartForComplete(String cursorArgumentPartForComplete) {
      this.cursorArgumentPartForComplete = cursorArgumentPartForComplete;
    }

    public String getUniverse() {
      return universe;
    }

    public void setUniverse(String universe) {
      this.universe = universe;
    }

    public boolean needLoadUniverses() {
      if (universe == null) {
        return true;
      }
      return false;
    }

    public boolean needLoadUniverseInfo() {
      if (universe != null && universeNodePosition) {
        return true;
      }
      return false;
    }
  }
}
