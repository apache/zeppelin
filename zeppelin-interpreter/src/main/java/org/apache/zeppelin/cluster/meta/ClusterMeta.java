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

import com.google.gson.Gson;
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

  // zeppelin-server meta
  public static String SERVER_HOST          = "SERVER_HOST";
  public static String SERVER_PORT          = "SERVER_PORT";
  public static String SERVER_TSERVER_HOST  = "SERVER_TSERVER_HOST";
  public static String SERVER_TSERVER_PORT  = "SERVER_TSERVER_PORT";
  public static String SERVER_START_TIME    = "SERVER_START_TIME";

  // interperter-process meta
  public static String INTP_TSERVER_HOST    = "INTP_TSERVER_HOST";
  public static String INTP_TSERVER_PORT    = "INTP_TSERVER_PORT";
  public static String INTP_START_TIME      = "INTP_START_TIME";

  // zeppelin-server resource usage
  public static String CPU_CAPACITY         = "CPU_CAPACITY";
  public static String CPU_USED             = "CPU_USED";
  public static String MEMORY_CAPACITY      = "MEMORY_CAPACITY";
  public static String MEMORY_USED          = "MEMORY_USED";

  public static String HEARTBEAT            = "HEARTBEAT";

  // zeppelin-server or interperter-process status
  public static String STATUS               = "STATUS";
  public static String ONLINE_STATUS        = "ONLINE";
  public static String OFFLINE_STATUS       = "OFFLINE";

  // cluster_name = host:port
  // Map:cluster_name -> {server_tserver_host,server_tserver_port,cpu_capacity,...}
  private Map<String, Map<String, Object>> mapServerMeta = new HashMap<>();

  // Map:InterpreterGroupId -> {cluster_name,intp_tserver_host,...}
  private Map<String, Map<String, Object>> mapInterpreterMeta = new HashMap<>();

  public static Gson gson = new Gson();

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

  public Map<String, Map<String, Object>> get(ClusterMetaType type, String key) {
    Map<String, Object> values = null;

    switch (type) {
      case ServerMeta:
        if (null == key || StringUtils.isEmpty(key)) {
          return mapServerMeta;
        }
        if (mapServerMeta.containsKey(key)) {
          values = mapServerMeta.get(key);
        } else {
          logger.warn("can not find key : {}", key);
        }
        break;
      case IntpProcessMeta:
        if (null == key || StringUtils.isEmpty(key)) {
          return mapInterpreterMeta;
        }
        if (mapInterpreterMeta.containsKey(key)) {
          values = mapInterpreterMeta.get(key);
        } else {
          logger.warn("can not find key : {}", key);
        }
        break;
    }

    Map<String, Map<String, Object>> result = new HashMap<>();
    result.put(key, values);

    return result;
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
