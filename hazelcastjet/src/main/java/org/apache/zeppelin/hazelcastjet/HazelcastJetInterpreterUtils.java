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

package org.apache.zeppelin.hazelcastjet;

import com.google.gson.Gson;
import com.hazelcast.jet.core.DAG;
import org.apache.zeppelin.interpreter.graph.GraphResult;
import org.apache.zeppelin.tabledata.Node;
import org.apache.zeppelin.tabledata.Relationship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hazelcast Jet interpreter utility methods
 */
public class HazelcastJetInterpreterUtils {

  private static final Gson gson = new Gson();

  /**
   * Convert an Hazelcast Jet DAG to %network display system
   * to leverage Zeppelin's built in visualization
   * @param dag DAG object to convert
   * @return Zeppelin %network
   */
  public static String displayNetworkFromDAG(DAG dag){
    GraphResult.Graph graph = new GraphResult.Graph();
    graph.setDirected(true);

    // Map between vertex name (from DAG) and node id (for graph)
    Map<String, Integer> nodeIds = new HashMap<>();

    // Create graph nodes based on dag vertices
    List<Node> nodes = new ArrayList<>();
    AtomicInteger nodeCount = new AtomicInteger(1);
    dag.forEach(v -> {
      // Assign an index to the vertex name
      nodeIds.put(v.getName(), nodeCount.getAndIncrement());
      Node node = new Node();
      node.setId(nodeIds.get(v.getName()));
      // Define node label from vertex name
      if (v.getName().toLowerCase().contains("sink"))
        node.setLabel("Sink");
      else if (v.getName().toLowerCase().contains("source"))
        node.setLabel("Source");
      else
        node.setLabel("Transform");
      // Add node description
      Map<String, Object> data = new HashMap<>();
      data.put("description", v.getName());
      node.setData(data);
      nodes.add(node);
    });
    graph.setNodes(nodes);

    // Set labels colors
    Map<String, String> labels = new HashMap<>();
    labels.put("Source", "#00317c");
    labels.put("Transform", "#ff7600");
    labels.put("Sink", "#00317c");
    graph.setLabels(labels);

    // Map between edge name (from DAG) and relationship id (for graph)
    Map<String, Integer> edgeIds = new HashMap<>();

    // Create graph relationships
    List<Relationship> rels = new ArrayList<>();
    AtomicInteger relCount = new AtomicInteger(1);
    dag.forEach(v -> {
      dag.getInboundEdges(v.getName()).forEach(e -> {
        String edgeName = e.getSourceName() + " to " + e.getDestName();
        if (edgeIds.get(edgeName) == null) {
          // Assign an index to the edge name if not found
          edgeIds.put(edgeName, relCount.getAndIncrement());
          Relationship rel = new Relationship();
          rel.setId(edgeIds.get(edgeName));
          rel.setSource(nodeIds.get(e.getSourceName()));
          rel.setTarget(nodeIds.get(e.getDestName()));
          // Add rel data
          Map<String, Object> data = new HashMap<>();
          data.put("routing", e.getRoutingPolicy().toString());
          data.put("priority", e.getPriority());
          data.put("distributed", e.isDistributed());
          rel.setData(data);
          rels.add(rel);
        }
      });
      dag.getOutboundEdges(v.getName()).forEach(e -> {
        String edgeName = e.getSourceName() + " to " + e.getDestName();
        if (edgeIds.get(edgeName) == null) {
          // Assign an index to the edge name if not found
          edgeIds.put(edgeName, relCount.getAndIncrement());
          Relationship rel = new Relationship();
          rel.setId(edgeIds.get(edgeName));
          rel.setSource(nodeIds.get(e.getSourceName()));
          rel.setTarget(nodeIds.get(e.getDestName()));
          // Add rel data
          Map<String, Object> data = new HashMap<>();
          data.put("routing", e.getRoutingPolicy().toString());
          data.put("priority", e.getPriority());
          data.put("distributed", e.isDistributed());
          rel.setData(data);
          rels.add(rel);
        }
      });
    });
    graph.setEdges(rels);

    return "%network " + gson.toJson(graph);
  }

}
