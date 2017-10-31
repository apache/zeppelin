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

package org.apache.zeppelin.tabledata;

import java.util.Map;

/**
 * The Zeppelin Relationship entity
 *
 */
public class Relationship extends GraphEntity {

  /**
   * Source node ID
   */
  private long source;

  /**
   * End node ID
   */
  private long target;

  public Relationship() {}

  public Relationship(long id, Map<String, Object> data, long source,
      long target, String label) {
    super(id, data, label);
    this.setSource(source);
    this.setTarget(target);
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

}
