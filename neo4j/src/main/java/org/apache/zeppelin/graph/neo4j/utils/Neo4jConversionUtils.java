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

import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Neo4jConversionUtils.
 */
public class Neo4jConversionUtils {
  private Neo4jConversionUtils() {}
  
  private static final String[] LETTERS = "0123456789ABCDEF".split("");

  public static final String COLOR_GREY = "#D3D3D3";
  
  public static org.apache.zeppelin.tabledata.Node toZeppelinNode(Node n,
      Map<String, String> graphLabels) {
    Set<String> labels = new LinkedHashSet<>();
    String firstLabel = null;
    for (String label : n.labels()) {
      if (firstLabel == null) {
        firstLabel = label;
      }
      labels.add(label);
    }
    return new org.apache.zeppelin.tabledata.Node(n.id(), n.asMap(),
        labels);
  }
  
  public static org.apache.zeppelin.tabledata.Relationship toZeppelinRelationship(Relationship r) {
    return new org.apache.zeppelin.tabledata.Relationship(r.id(), r.asMap(),
        r.startNodeId(), r.endNodeId(), r.type());
  }

  public static String getRandomLabelColor() {
    char[] color = new char[7];
    color[0] = '#';
    for (int i = 1; i < color.length; i++) {
      color[i] = LETTERS[(int) Math.floor(Math.random() * 16)].charAt(0);
    }
    return new String(color);
  }
}
