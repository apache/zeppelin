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

package org.apache.zeppelin.graph.model;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.zeppelin.interpreter.InterpreterResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
/**
 * 
 * @author a.santurbano
 *
 */
public class GraphResult extends InterpreterResult {
  private static final long serialVersionUID = -7269726748696145519L;

  /**
   * 
   * @author a.santurbano
   *
   */
  public static class Graph {
    private Collection<Node> nodes;
    
    private Collection<Relationship> edges;
    
    private Map<String, String> labels;
    
    private Set<String> types;
    
    public Graph() {}

    public Graph(Collection<Node> nodes, Collection<Relationship> edges,
        Map<String, String> labels, Set<String> types) {
      super();
      this.setNodes(nodes);
      this.setEdges(edges);
      this.setLabels(labels);
      this.setTypes(types);
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

  }
  
  private static final ObjectMapper jsonMapper = new ObjectMapper();

  public GraphResult(Code code, Graph response) throws JsonProcessingException {
    super(code, Type.NETWORK, jsonMapper.writeValueAsString(response));
  }

}
