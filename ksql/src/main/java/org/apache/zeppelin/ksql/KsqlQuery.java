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
package org.apache.zeppelin.ksql;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KsqlQuery {
  private static final Logger LOGGER = LoggerFactory.getLogger(KsqlQuery.class);

  public enum QueryType {
    SHOW_TABLES,
    SHOW_STREAMS,
    DESCRIBE,
    EXPLAIN,
    SHOW_PROPS,
    SHOW_TOPICS,
    SHOW_QUERIES,
    SHOW_FUNCTIONS,
    DESCRIBE_FUNCTION,
    CREATE_TABLE_STREAM,
    TERMINATE,
    INSERT_INTO,
    SELECT,
    DROP_TABLE,
    UNSUPPORTED
  }

  QueryType type;
  String query;
  List<String> captures = Collections.emptyList();

  private static List<Pair<Pattern, QueryType>> PATTERNS;

  private static final String END_STATEMENT = "\\s*;\\s*$";
  private static final String TABLE_NAME_PATTERN = "[_a-zA-Z0-9][-a-zA-Z0-9._]*";
  
  private static final int DEFAULT_PATTERN_OPTIONS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

  static {
    PATTERNS = new ArrayList<>();
    PATTERNS.add(Pair.of(Pattern.compile("^(?:show|list)\\s+streams" + END_STATEMENT,
        DEFAULT_PATTERN_OPTIONS), QueryType.SHOW_STREAMS));
    PATTERNS.add(Pair.of(Pattern.compile("^(?:show|list)\\s+tables" + END_STATEMENT,
        DEFAULT_PATTERN_OPTIONS), QueryType.SHOW_TABLES));
    PATTERNS.add(Pair.of(Pattern.compile("^(?:show|list)\\s+properties" + END_STATEMENT,
        DEFAULT_PATTERN_OPTIONS), QueryType.SHOW_PROPS));
    PATTERNS.add(Pair.of(Pattern.compile("^(?:show|list)\\s+topics" + END_STATEMENT,
        DEFAULT_PATTERN_OPTIONS), QueryType.SHOW_TOPICS));
    PATTERNS.add(Pair.of(Pattern.compile("^(?:show|list)\\s+queries" + END_STATEMENT,
        DEFAULT_PATTERN_OPTIONS), QueryType.SHOW_QUERIES));
    PATTERNS.add(Pair.of(Pattern.compile("^(?:show|list)\\s+functions" + END_STATEMENT,
        DEFAULT_PATTERN_OPTIONS), QueryType.SHOW_FUNCTIONS));
    PATTERNS.add(Pair.of(Pattern.compile("^drop\\s+(?:table|stream)\\s+(?:if\\s+exists\\s+)?" +
        TABLE_NAME_PATTERN + "(?:\\s+delete\\s+topic)?" + END_STATEMENT,
        DEFAULT_PATTERN_OPTIONS), QueryType.DROP_TABLE));
    PATTERNS.add(Pair.of(Pattern.compile("^create\\s+(?:table|stream)\\s" +
        TABLE_NAME_PATTERN + "\\s+.+" + END_STATEMENT,
        DEFAULT_PATTERN_OPTIONS), QueryType.CREATE_TABLE_STREAM));
    PATTERNS.add(Pair.of(Pattern.compile("^terminate\\s+" + TABLE_NAME_PATTERN + END_STATEMENT,
        DEFAULT_PATTERN_OPTIONS), QueryType.TERMINATE));
    PATTERNS.add(Pair.of(Pattern.compile("^describe\\s+function\\s*"
        + TABLE_NAME_PATTERN + END_STATEMENT,
        DEFAULT_PATTERN_OPTIONS), QueryType.DESCRIBE_FUNCTION));
    PATTERNS.add(Pair.of(Pattern.compile("^describe\\s+(extended)?\\s*"
        + TABLE_NAME_PATTERN + END_STATEMENT,
        DEFAULT_PATTERN_OPTIONS), QueryType.DESCRIBE));
    PATTERNS.add(Pair.of(Pattern.compile("^select\\s+.*" + END_STATEMENT,
        DEFAULT_PATTERN_OPTIONS), QueryType.SELECT));
    PATTERNS.add(Pair.of(Pattern.compile("^insert\\s+into\\s" +
        TABLE_NAME_PATTERN + "\\s+.+" + END_STATEMENT,
        DEFAULT_PATTERN_OPTIONS), QueryType.INSERT_INTO));
  }

  KsqlQuery() {
    type = QueryType.UNSUPPORTED;
  }

  KsqlQuery(final String q) {
    query = q.trim().replace('\n', ' ').replace('\r', ' ');
    type = analyzeQuery(query);
    LOGGER.debug("Initializing KsqlQuery: {}, type: {}", query, type.name());
  }

  public boolean isUnsupported() {
    return type == QueryType.UNSUPPORTED;
  }

  public QueryType getType() {
    return type;
  }

  public void setType(QueryType type) {
    this.type = type;
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public QueryType analyzeQuery(final String query) {
    for (Pair<Pattern, QueryType> pair : PATTERNS) {
      Matcher m = pair.getLeft().matcher(query);
      if (m.matches()) {
        int groupCount = m.groupCount();
        if (groupCount > 0) {
          captures = new ArrayList<>(groupCount);
          for (int i = 1; i < groupCount + 1; i++) {
            captures.add(m.group(i));
          }
        }
        return pair.getRight();
      }
    }
    return QueryType.UNSUPPORTED;
  }
}
