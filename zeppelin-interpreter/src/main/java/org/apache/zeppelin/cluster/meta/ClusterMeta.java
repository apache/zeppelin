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

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Metadata stores metadata information in a KV key-value pair
 */
public class ClusterMeta implements Serializable {
  private static Logger logger = LoggerFactory.getLogger(ClusterMeta.class);

  public static String SERVER_HOST          = "SERVER_HOST";
  public static String SERVER_PORT          = "SERVER_PORT";
  public static String SERVER_TSERVER_HOST  = "SERVER_TSERVER_HOST";
  public static String SERVER_TSERVER_PORT  = "SERVER_TSERVER_PORT";
  public static String INTP_TSERVER_HOST    = "INTP_TSERVER_HOST";
  public static String INTP_TSERVER_PORT    = "INTP_TSERVER_PORT";
  public static String CPU_CAPACITY         = "CPU_CAPACITY";
  public static String CPU_USED             = "CPU_USED";
  public static String MEMORY_CAPACITY      = "MEMORY_CAPACITY";
  public static String MEMORY_USED          = "MEMORY_USED";
  public static String HEARTBEAT            = "HEARTBEAT";

  // cluster_name = host:port
  // cluster_name -> {server_tserver_host,server_tserver_port,cpu_capacity,...}
  private Map<String, Map<String, Object>> mapServerMeta = new HashMap<>();

  // InterpreterGroupId -> {cluster_name,intp_tserver_host,...}
  private Map<String, Map<String, Object>> mapInterpreterMeta = new HashMap<>();

  public void put(ClusterMetaType type, String key, Object value) {
    Map<String, Object> mapValue = (Map<String, Object>) value;

    switch (type) {
      case ServerMeta:
        // Because it may be partially updated metadata information
        if (mapServerMeta.containsKey(key)) {
          Map<String, Object> values = mapServerMeta.get(key);
          values.putAll(mapValue);
        } else {
          mapServerMeta.put(key, mapValue);
        }
        break;
      case IntpProcessMeta:
        if (mapInterpreterMeta.containsKey(key)) {
          Map<String, Object> values = mapInterpreterMeta.get(key);
          values.putAll(mapValue);
        } else {
          mapInterpreterMeta.put(key, mapValue);
        }
        break;
    }
  }

  public Object get(ClusterMetaType type, String key) {
    Map<String, Object> values = null;

    switch (type) {
      case ServerMeta:
        if (StringUtils.isEmpty(key)) {
          return mapServerMeta;
        }
        if (mapServerMeta.containsKey(key)) {
          values = mapServerMeta.get(key);
        } else {
          logger.warn("can not find key : {}", key);
        }
        break;
      case IntpProcessMeta:
        if (StringUtils.isEmpty(key)) {
          return mapInterpreterMeta;
        }
        if (mapInterpreterMeta.containsKey(key)) {
          values = mapInterpreterMeta.get(key);
        } else {
          logger.warn("can not find key : {}", key);
        }
        break;
    }

    return values;
  }

  public boolean contains(ClusterMetaType type, String key) {
    boolean isExist = false;

    switch (type) {
      case ServerMeta:
        if (mapServerMeta.containsKey(key)) {
          isExist = true;
        }
        break;
      case IntpProcessMeta:
        if (mapInterpreterMeta.containsKey(key)) {
          isExist = true;
        }
        break;
    }

    return isExist;
  }

  public Map<String, Object> remove(ClusterMetaType type, String key) {
    switch (type) {
      case ServerMeta:
        if (mapServerMeta.containsKey(key)) {
          return mapServerMeta.remove(key);
        } else {
          logger.warn("can not find key : {}", key);
        }
        break;
      case IntpProcessMeta:
        if (mapInterpreterMeta.containsKey(key)) {
          return mapInterpreterMeta.remove(key);
        } else {
          logger.warn("can not find key : {}", key);
        }
        break;
    }

    return null;
  }
}
