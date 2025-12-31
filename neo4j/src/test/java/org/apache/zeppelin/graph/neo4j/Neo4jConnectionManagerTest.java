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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class Neo4jConnectionManagerTest {

  private static final String NEO4J_AUTH_TYPE = "neo4j.auth.type";
  private static final String NEO4J_AUTH_USER = "neo4j.auth.user";
  private static final String NEO4J_AUTH_PASSWORD = "neo4j.auth.password";


  @Test
  void testCreateNeo4jConnectionManagerSuccess() {
    Properties props = new Properties();
    props.setProperty(NEO4J_AUTH_TYPE, "BASIC");
    props.setProperty(NEO4J_AUTH_USER, "admin");
    props.setProperty(NEO4J_AUTH_PASSWORD, "secret");

    Neo4jConnectionManager neo4jConnectionManager = new Neo4jConnectionManager(props);

    assertNotNull(neo4jConnectionManager);
  }

  @Test
  void testCreateNeo4jConnectionManagerDefaultNoneAuthWhenMissingTypeSuccess() {
    Properties props = new Properties();
    props.setProperty(NEO4J_AUTH_USER, "admin");
    props.setProperty(NEO4J_AUTH_PASSWORD, "secret");

    Neo4jConnectionManager neo4jConnectionManager = new Neo4jConnectionManager(props);

    assertNotNull(neo4jConnectionManager);
  }

  @Test
  void testCreateNeo4jConnectionAuthTypeCaseInsensitiveSuccess() {
    Properties props = new Properties();
    props.setProperty(NEO4J_AUTH_TYPE, "basic"); // lowercase intentionally
    props.setProperty(NEO4J_AUTH_USER, "user");
    props.setProperty(NEO4J_AUTH_PASSWORD, "pw");

    Neo4jConnectionManager neo4jConnectionManager = new Neo4jConnectionManager(props);

    assertNotNull(neo4jConnectionManager);
  }

  @Test
  void testCreateNeo4jConnectionManagerInvalidAuthTypeFail() {
    Properties props = new Properties();
    props.setProperty(NEO4J_AUTH_TYPE, "INVALID");
    props.setProperty(NEO4J_AUTH_USER, "admin");
    props.setProperty(NEO4J_AUTH_PASSWORD, "secret");

    Exception exception = assertThrows(IllegalArgumentException.class, () ->
        new Neo4jConnectionManager(props)
    );

    assertTrue(exception.getMessage().contains("Unsupported Neo4j authentication type: INVALID"));
  }

  @Test
  void testCreateNeo4jConnectionWithMissingUsernameFail() {
    Properties props = new Properties();
    props.setProperty(NEO4J_AUTH_TYPE, "BASIC");
    props.setProperty(NEO4J_AUTH_PASSWORD, "secret");

    Exception exception = assertThrows(NullPointerException.class, () ->
        new Neo4jConnectionManager(props)
    );

    assertTrue(exception.getMessage().contains("Username can't be null"));
  }
}
