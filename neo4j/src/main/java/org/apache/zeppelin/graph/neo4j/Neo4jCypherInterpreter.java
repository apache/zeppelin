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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.graph.model.GraphResult;
import org.apache.zeppelin.graph.model.Relationship.Type;
import org.apache.zeppelin.graph.neo4j.utils.Neo4jConversionUtils;
import org.apache.zeppelin.graph.utils.GraphUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.InternalPath;
import org.neo4j.driver.internal.InternalRelationship;
import org.neo4j.driver.internal.util.Iterables;
import org.neo4j.driver.internal.value.ListValue;
import org.neo4j.driver.internal.value.NodeValue;
import org.neo4j.driver.internal.value.PathValue;
import org.neo4j.driver.internal.value.RelationshipValue;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Neo4j interpreter for Zeppelin.
 */
public class Neo4jCypherInterpreter extends Interpreter {
  static final Logger LOGGER = LoggerFactory.getLogger(Neo4jCypherInterpreter.class);
  
  public static final String NEO4J_SERVER_URL = "neo4j.url";
  public static final String NEO4J_SERVER_USER = "neo4j.user";
  public static final String NEO4J_SERVER_PASSWORD = "neo4j.password";
  public static final String NEO4J_MAX_CONCURRENCY = "neo4j.max.concurrency";
  
  private static final String TABLE = "%table";
  private static final String NEW_LINE = "\n";
  private static final String TAB = "\t";
  
  private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\{\\w+\\}");
  private static final String REPLACE_CURLY_BRACKETS = "\\{|\\}";
  
  private Driver driver = null;
  
  private Map<String, String> labels;
  
  private Set<String> types;
  
  public Neo4jCypherInterpreter(Properties properties) {
    super(properties);
  }
  
  private Driver getDriver() {
    if (driver == null) {
      Config config = Config.build().withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig();
      driver = GraphDatabase.driver(getProperty(NEO4J_SERVER_URL),
                AuthTokens.basic(getProperty(NEO4J_SERVER_USER),
                    getProperty(NEO4J_SERVER_PASSWORD)), config);
    }
    return driver;
  }

  @Override
  public void open() {
    getDriver();
    getLabels();
    getTypes();
  }

  @Override
  public void close() {
    getDriver().close();
  }
  
  public Map<String, String> getLabels() {
    if (labels == null) {
      labels = new LinkedHashMap<>();
      try (Session session = getDriver().session()) {
        StatementResult result = session.run("CALL db.labels()");
        Set<String> colors = new HashSet<>();
        while (result.hasNext()) {
          Record record = result.next();
          String color = null;
          do {
            color = GraphUtils.getRandomColor();
          } while(colors.contains(color));
          colors.add(color);
          labels.put(record.get("label").asString(), color);
        }
      }
    }
    return labels;
  }
  
  private Set<String> getTypes() {
    if (types == null) {
      types = new HashSet<>();
      try (Session session = getDriver().session()) {
        StatementResult result = session.run("CALL db.relationshipTypes()");
        while (result.hasNext()) {
          Record record = result.next();
          types.add(record.get("relationshipType").asString());
        }
      }
    }
    return types;
  }
  
  private void setResultValue(Object value, Set<Node> nodes, Set<Relationship> relationships,
      List<String> line) {
    if (value instanceof NodeValue
        || value instanceof InternalNode) {
      NodeValue nodeVal = value instanceof NodeValue ?
          (NodeValue) value : (NodeValue) ((InternalNode) value).asValue();
      nodes.add(nodeVal.asNode());
    } else if (value instanceof RelationshipValue
        || value instanceof InternalRelationship) {
      RelationshipValue relVal = value instanceof RelationshipValue ?
          (RelationshipValue) value : (RelationshipValue) ((InternalRelationship) value).asValue();
      relationships.add(relVal.asRelationship());
    } else if (value instanceof PathValue
        || value instanceof InternalPath) {
      PathValue pathVal = value instanceof PathValue ?
          (PathValue) value : (PathValue) ((InternalPath) value).asValue();
      nodes.addAll(Iterables.asList(pathVal.asPath().nodes()));
      relationships.addAll(Iterables.asList(pathVal.asPath().relationships()));
    } else if (value instanceof ListValue) {
      ListValue listValues = (ListValue) value;
      List<Object> listObject = listValues.asList();
      for (Object val : listObject) {
        setResultValue(val, nodes, relationships, line);
      }
    } else {
      line.add(String.valueOf(value));
    }
  }
  
  @Override
  public InterpreterResult interpret(String cypherQuery, InterpreterContext interpreterContext) {
    logger.info("Opening session");
    if (StringUtils.isEmpty(cypherQuery)) {
      return new InterpreterResult(Code.ERROR, "Cypher query is Empty");
    }
    try (Session session = getDriver().session()){
      StatementResult result = execute(session, cypherQuery, interpreterContext);
      Set<String> cols = new HashSet<>();
      List<List<String>> lines = new ArrayList<>();
      Set<Node> nodes = new HashSet<>();
      Set<Relationship> relationships = new HashSet<>();
      while (result.hasNext()) {
        Record record = result.next();
        if (cols.isEmpty()) {
          cols.addAll(record.keys());
        }
        List<String> line = new ArrayList<>();
        for (String col : cols) {
          Object value = record.get(col);
          setResultValue(value, nodes, relationships, line);
        }
        if (!line.isEmpty()) {
          lines.add(line);
        }
      }
      if (!nodes.isEmpty()) {
        return renderGraph(nodes, relationships);
      } else {
        return renderTable(cols, lines);
      }
    } catch (Exception e) {
      return new InterpreterResult(Code.ERROR, e.getMessage());
    }
  }

  private StatementResult execute(Session session, String cypherQuery,
      InterpreterContext interpreterContext) {
    Matcher matcher = PROPERTY_PATTERN.matcher(cypherQuery);
    Map<String, Object> params = new HashMap<>();
    ResourcePool resourcePool = interpreterContext.getResourcePool();
    while (matcher.find()) {
      String key = matcher.group().replaceAll(REPLACE_CURLY_BRACKETS, StringUtils.EMPTY);
      Resource resource = resourcePool.get(key);
      if (resource != null) {
        params.put(key, resource.get());
      }
    }
    logger.info("Executing cypher query {} with params {}", cypherQuery, params);
    return params.isEmpty() ? session.run(cypherQuery) : session.run(cypherQuery, params);
  }

  private InterpreterResult renderTable(Set<String> cols, List<List<String>> lines) {
    logger.info("Executing renderTable method");
    StringBuilder msg = new StringBuilder(TABLE);
    msg.append(NEW_LINE);
    msg.append(StringUtils.join(cols, TAB));
    msg.append(NEW_LINE);
    for (List<String> line : lines) {
      msg.append(StringUtils.join(line, TAB));
      msg.append(NEW_LINE);
    }
    return new InterpreterResult(Code.SUCCESS, msg.toString());
  }

  private InterpreterResult renderGraph(Set<Node> nodes,
      Set<Relationship> relationships) throws JsonProcessingException {
    logger.info("Executing renderGraph method");
    List<org.apache.zeppelin.graph.model.Node> nodesList = new ArrayList<>();
    List<org.apache.zeppelin.graph.model.Relationship> relsList = new ArrayList<>();
    Map<String, Integer> relCount = new HashMap<>();
    for (Relationship rel : relationships) {
      Type type = null;
      String keyStartEnd = String.format("%s-%s", rel.startNodeId(), rel.endNodeId());
      String keyEndStart = String.format("%s-%s", rel.endNodeId(), rel.startNodeId());
      if (!relCount.containsKey(keyStartEnd) && !relCount.containsKey(keyEndStart)) {
        type = Type.arrow;
      } else {
        type = Type.curvedArrow;
      }
      if (!relCount.containsKey(keyStartEnd)) {
        relCount.put(keyStartEnd, 0);
      }
      Integer count = relCount.get(keyStartEnd);
      relCount.put(keyStartEnd, ++count);
      relsList.add(Neo4jConversionUtils.toZeppelinRelationship(rel, type, count));
    }
    for (Node node : nodes) {
      nodesList.add(Neo4jConversionUtils.toZeppelinNode(node, getLabels()));
    }
    return new GraphResult(Code.SUCCESS,
        new GraphResult.Graph(nodesList, relsList, getLabels(), getTypes()));
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton()
        .createOrGetParallelScheduler(Neo4jCypherInterpreter.class.getName() + this.hashCode(),
            Integer.parseInt(getProperty(NEO4J_MAX_CONCURRENCY)));
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public void cancel(InterpreterContext context) {
  }

}
