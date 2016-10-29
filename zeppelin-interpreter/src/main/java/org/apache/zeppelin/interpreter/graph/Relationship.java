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

/**
 * The Zeppelin Relationship entity
 *
 */
public class Relationship extends GraphEntity {
  
  private long source;
  
  private long target;
  
  private Type type;
  
  private int count;
  
  /**
   * The relationship shape type
   *
   */
  public enum Type{arrow, curvedArrow}

  public static final String COLOR_GREY = "#D3D3D3";

  public static final double DEFALUT_SIZE = 4d;
  
  public Relationship() {}
  
  public Relationship(long id, Map<String, Object> data, long source,
      long target, String label, Type type, int count) {
    super(id, data, label, DEFALUT_SIZE, COLOR_GREY);
    this.setSource(source);
    this.setTarget(target);
    this.setType(type);
    this.setCount(count);
  }

  public long getSource() {
    return source;
  }

  public void setSource(long startNodeId) {
    this.source = startNodeId;
  }

  public long getTarget() {
    return target;
  }

  public void setTarget(long endNodeId) {
    this.target = endNodeId;
  }

  public Type getType() {
    return type;
  }
  
  public void setType(Type type) {
    this.type = type;
  }

  public int getCount() {
    return count;
  }
  
  public void setCount(int count) {
    this.count = count;
  }

}
