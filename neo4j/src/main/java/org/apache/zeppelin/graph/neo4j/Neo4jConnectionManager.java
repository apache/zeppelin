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

package org.apache.zeppelin.graph.neo4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourcePool;
import org.neo4j.driver.v1.AuthToken;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Neo4j connection manager for Zeppelin.
 */
public class Neo4jConnectionManager {
  static final Logger LOGGER = LoggerFactory.getLogger(Neo4jConnectionManager.class);

  private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\{\\w+\\}");
  private static final String REPLACE_CURLY_BRACKETS = "\\{|\\}";

  private static final Pattern $_PATTERN = Pattern.compile("\\$\\w+\\}");
  private static final String REPLACE_$ = "\\$";

  private Driver driver = null;

  private final String neo4jUrl;

  private final Config config;

  private final AuthToken authToken;

  public Neo4jConnectionManager(String neo4jUrl, AuthToken authToken,
          Config config) {
    this.neo4jUrl = neo4jUrl;
    this.authToken = authToken;
    this.config = config;
  }

  private Driver getDriver() {
    if (driver == null) {
      driver = GraphDatabase.driver(this.neo4jUrl, this.authToken, this.config);
    }
    return driver;
  }

  public void open() {
    getDriver();
  }

  public void close() {
    getDriver().close();
  }

  private Session getSession() {
    return getDriver().session();
  }

  public StatementResult execute(String cypherQuery,
      InterpreterContext interpreterContext) {
    Map<String, Object> params = new HashMap<>();
    if (interpreterContext != null) {
      ResourcePool resourcePool = interpreterContext.getResourcePool();
      Set<String> keys = extractParams(cypherQuery, PROPERTY_PATTERN, REPLACE_CURLY_BRACKETS);
      keys.addAll(extractParams(cypherQuery, $_PATTERN, REPLACE_$));
      for (String key : keys) {
        Resource resource = resourcePool.get(key);
        if (resource != null) {
          params.put(key, resource.get());
        }
      }
    }
    LOGGER.debug("Executing cypher query {} with params {}", cypherQuery, params);
    StatementResult result;
    try (Session session = getSession()) {
      result = params.isEmpty()
            ? getSession().run(cypherQuery) : getSession().run(cypherQuery, params);
    }
    return result;
  }

  public StatementResult execute(String cypherQuery) {
    return execute(cypherQuery, null);
  }

  private Set<String> extractParams(String cypherQuery, Pattern pattern, String replaceChar) {
    Matcher matcher = pattern.matcher(cypherQuery);
    Set<String> keys = new HashSet<>();
    while (matcher.find()) {
      keys.add(matcher.group().replaceAll(replaceChar, StringUtils.EMPTY));
    }
    return keys;
  }

}
