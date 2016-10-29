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

import java.util.Map;
import java.util.Set;

/**
 * The Zeppelin Node Entity
 *
 */
public class Node extends GraphEntity {

  private Set<String> labels;

  private double x;

  private double y;
  
  private String defaultLabel;

  public static final double DEFALUT_SIZE = 10d;
  
  public Node() {}

  public Node(long id, Map<String, Object> data, Set<String> labels,
      String color, double x, double y) {
    super(id, data, labels.iterator().next(), DEFALUT_SIZE, color);
    this.setLabels(labels);
    this.setX(x);
    this.setY(y);
    this.defaultLabel = super.getLabel();
  }
  
  public Node(long id, Map<String, Object> data, Set<String> labels,
      String color) {
    this(id, data, labels, color, Math.random(), Math.random());
  }

  public Set<String> getLabels() {
    return labels;
  }

  public void setLabels(Set<String> labels) {
    this.labels = labels;
  }
 
  public double getX() {
    return x;
  }

  public void setX(double x) {
    this.x = x;
  }

  public double getY() {
    return y;
  }

  public void setY(double y) {
    this.y = y;
  }

  public String getDefaultLabel() {
    return defaultLabel;
  }

}
