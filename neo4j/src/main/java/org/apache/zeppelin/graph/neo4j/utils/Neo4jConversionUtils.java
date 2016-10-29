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

package org.apache.zeppelin.graph.neo4j.utils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.zeppelin.interpreter.graph.Relationship.Type;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;

/**
 * Neo4jConversionUtils
 */
public class Neo4jConversionUtils {
  private Neo4jConversionUtils() {}
  
  public static org.apache.zeppelin.interpreter.graph.Node toZeppelinNode(Node n,
      Map<String, String> graphLabels) {
    Set<String> labels = new LinkedHashSet<>();
    String firstLabel = null;
    for (String label : n.labels()) {
      if (firstLabel == null) {
        firstLabel = label;
      }
      labels.add(label);
    }
    String color = graphLabels.get(firstLabel);
    return new org.apache.zeppelin.interpreter.graph.Node(n.id(), n.asMap(),
        labels, color);
  }
  
  public static org.apache.zeppelin.interpreter.graph.Relationship
  toZeppelinRelationship(Relationship r, Type type, int count) {
    return new org.apache.zeppelin.interpreter.graph.Relationship(r.id(), r.asMap(),
        r.startNodeId(), r.endNodeId(), r.type(), type, count);
  }
  
}
