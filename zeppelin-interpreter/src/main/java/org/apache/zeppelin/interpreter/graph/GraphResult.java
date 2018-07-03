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

package org.apache.zeppelin.interpreter.graph;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.tabledata.Node;
import org.apache.zeppelin.tabledata.Relationship;

import com.google.gson.Gson;

/**
 * The intepreter result template for Networks
 *
 */
public class GraphResult extends InterpreterResult {

  /**
   * The Graph structure parsed from the front-end
   *
   */
  public static class Graph {
    private Collection<Node> nodes;
    
    private Collection<Relationship> edges;
    
    /**
     * The node types in the whole graph, and the related colors
     * 
     */
    private Map<String, String> labels;
    
    /**
     * The relationship types in the whole graph
     * 
     */
    private Set<String> types;

    /**
     * Is a directed graph
     */
    private boolean directed;
    
    public Graph() {}

    public Graph(Collection<Node> nodes, Collection<Relationship> edges,
        Map<String, String> labels, Set<String> types, boolean directed) {
      super();
      this.setNodes(nodes);
      this.setEdges(edges);
      this.setLabels(labels);
      this.setTypes(types);
      this.setDirected(directed);
    }

    public Collection<Node> getNodes() {
      return nodes;
    }

    public void setNodes(Collection<Node> nodes) {
      this.nodes = nodes;
    }

    public Collection<Relationship> getEdges() {
      return edges;
    }

    public void setEdges(Collection<Relationship> edges) {
      this.edges = edges;
    }

    public Map<String, String> getLabels() {
      return labels;
    }

    public void setLabels(Map<String, String> labels) {
      this.labels = labels;
    }

    public Set<String> getTypes() {
      return types;
    }
    
    public void setTypes(Set<String> types) {
      this.types = types;
    }

    public boolean isDirected() {
      return directed;
    }

    public void setDirected(boolean directed) {
      this.directed = directed;
    }

  }
  
  private static final Gson gson = new Gson();

  public GraphResult(Code code, Graph graphObject) {
    super(code, Type.NETWORK, gson.toJson(graphObject));
  }

}
