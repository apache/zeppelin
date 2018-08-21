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
package org.apache.zeppelin.cluster.meta;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Cluster operations, cluster types, encapsulation objects for keys and values
 */
public class ClusterMetaEntity implements Serializable {
  private ClusterMetaOperation operation;
  private ClusterMetaType type;
  private String key;
  private HashMap<String, Object> values = new HashMap<>();

  public ClusterMetaEntity(ClusterMetaOperation operation, ClusterMetaType type,
                           String key, HashMap<String, Object> values) {
    this.operation = operation;
    this.type = type;
    this.key = key;

    if (null != values) {
      this.values.putAll(values);
    }
  }

  public ClusterMetaOperation getOperation() {
    return operation;
  }

  public ClusterMetaType getMetaType() {
    return type;
  }

  public String getKey() {
    return key;
  }

  public HashMap<String, Object> getValues() {
    return values;
  }
}
