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
package org.apache.zeppelin.geode;

import static org.apache.commons.lang.StringUtils.isBlank;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

import jline.console.completer.ArgumentCompleter.ArgumentList;
import jline.console.completer.ArgumentCompleter.WhitespaceArgumentDelimiter;
import jline.console.completer.StringsCompleter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gemstone.gemfire.cache.client.ClientCache;

/**
 * OQL auto-completer for the {@link GeodeOqlInterpreter}.
 */
public class OqlCompleter extends StringsCompleter {

  private static Logger logger = LoggerFactory.getLogger(OqlCompleter.class);

  private WhitespaceArgumentDelimiter delimiter = new WhitespaceArgumentDelimiter();

  public OqlCompleter(Set<String> completions) {
    super(completions);
  }

  @Override
  public int complete(String buffer, int cursor, List<CharSequence> candidates) {

    if (isBlank(buffer) || cursor > buffer.length() + 1) {
      return -1;
    }

    // The delimiter breaks the buffer into separate words (arguments), separated by the
    // whitespaces.
    ArgumentList argumentList = delimiter.delimit(buffer, cursor);
    String argument = argumentList.getCursorArgument();
    // cursor in the selected argument
    int argumentPosition = argumentList.getArgumentPosition();

    if (isBlank(argument)) {
      int argumentsCount = argumentList.getArguments().length;
      if (argumentsCount <= 0 || ((buffer.length() + 2) < cursor)
          || delimiter.isDelimiterChar(buffer, cursor - 2)) {
        return -1;
      }
      argument = argumentList.getArguments()[argumentsCount - 1];
      argumentPosition = argument.length();
    }

    int complete = super.complete(argument, argumentPosition, candidates);

    logger.debug("complete:" + complete + ", size:" + candidates.size());

    return complete;
  }

  public static Set<String> getOqlCompleterTokens(ClientCache cache) throws IOException {

    Set<String> completions = new TreeSet<String>();

    // add the default OQL completions
    String keywords =
        new BufferedReader(new InputStreamReader(
            OqlCompleter.class.getResourceAsStream("/oql.keywords"))).readLine();


    // Also allow upper-case versions of all the keywords
    keywords += "," + keywords.toUpperCase();

    StringTokenizer tok = new StringTokenizer(keywords, ",");
    while (tok.hasMoreTokens()) {
      completions.add(tok.nextToken());
    }

    return completions;
  }
}
