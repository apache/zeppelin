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

package org.apache.zeppelin.rest;

import com.google.gson.Gson;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.cluster.ClusterManagerServer;
import org.apache.zeppelin.cluster.meta.ClusterMeta;
import org.apache.zeppelin.cluster.meta.ClusterMetaType;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.server.JsonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * clusters Rest api.
 */
@Path("/cluster")
@Produces("application/json")
public class ClusterRestApi {
  private static final Logger LOG = LoggerFactory.getLogger(ClusterRestApi.class);
  Gson gson = new Gson();

  private ClusterManagerServer clusterManagerServer = ClusterManagerServer.getInstance();

  // Do not modify, Use by `zeppelin-web/src/app/cluster/cluster.html`
  private static String PROPERTIES = "properties";

  private boolean isTest = false;

  @GET
  @Path("/address")
  @ZeppelinApi
  public Response getClusterAddress() {
    ZeppelinConfiguration zconf = ZeppelinConfiguration.create();
    String clusterAddr = zconf.getClusterAddress();
    Map<String, String> data = new HashMap<>();
    data.put("clusterAddr", clusterAddr);

    return new JsonResponse<>(Response.Status.OK, "Cluster Address", data).build();
  }

  /**
   * get all nodes of clusters
   */
  @GET
  @Path("/nodes")
  @ZeppelinApi
  public Response getClusterNodes(){
    ArrayList<HashMap<String, Object>> nodes = new ArrayList<HashMap<String, Object>>();

    Map<String, HashMap<String, Object>> clusterMeta = null;
    Map<String, HashMap<String, Object>> intpMeta = null;
    if (isTest) {
      clusterMeta = mockNodesMeta();
      intpMeta = mockIntpMeta();
    } else {
      clusterMeta = clusterManagerServer.getClusterMeta(ClusterMetaType.SERVER_META, "");
      intpMeta = clusterManagerServer.getClusterMeta(ClusterMetaType.INTP_PROCESS_META, "");
    }

    // Number of calculation processes
    for (Map.Entry<String, HashMap<String, Object>> serverMetaEntity : clusterMeta.entrySet()) {
      if (!serverMetaEntity.getValue().containsKey(ClusterMeta.NODE_NAME)) {
        continue;
      }
      String serverNodeName = (String) serverMetaEntity.getValue().get(ClusterMeta.NODE_NAME);

      ArrayList<String> arrIntpProcess = new ArrayList<>();
      int intpProcCount = 0;
      for (Map.Entry<String, HashMap<String, Object>> intpMetaEntity : intpMeta.entrySet()) {
        if (!intpMetaEntity.getValue().containsKey(ClusterMeta.NODE_NAME)
            && !intpMetaEntity.getValue().containsKey(ClusterMeta.INTP_PROCESS_NAME)) {
          continue;
        }
        String intpNodeName = (String) intpMetaEntity.getValue().get(ClusterMeta.NODE_NAME);

        if (serverNodeName.equals(intpNodeName)) {
          intpProcCount ++;
          String intpName = (String) intpMetaEntity.getValue().get(ClusterMeta.INTP_PROCESS_NAME);
          arrIntpProcess.add(intpName);
        }
      }
      serverMetaEntity.getValue().put(ClusterMeta.INTP_PROCESS_COUNT, intpProcCount);
      serverMetaEntity.getValue().put(ClusterMeta.INTP_PROCESS_LIST, arrIntpProcess);
    }

    for (Map.Entry<String, HashMap<String, Object>> entry : clusterMeta.entrySet()) {
      String nodeName = entry.getKey();
      Map<String, Object> properties = entry.getValue();

      Map<String, Object> sortProperties = new HashMap<>();

      if (properties.containsKey(ClusterMeta.CPU_USED)
          && properties.containsKey(ClusterMeta.CPU_CAPACITY)) {
        float cpuUsed = (long) properties.get(ClusterMeta.CPU_USED) / (float) 100.0;
        float cpuCapacity = (long) properties.get(ClusterMeta.CPU_CAPACITY) / (float) 100.0;
        float cpuRate = cpuUsed / cpuCapacity * 100;

        String cpuInfo = String.format("%.2f / %.2f = %.2f", cpuUsed, cpuCapacity, cpuRate);
        sortProperties.put(ClusterMeta.CPU_USED + " / " + ClusterMeta.CPU_CAPACITY, cpuInfo + "%");
      }

      if (properties.containsKey(ClusterMeta.MEMORY_USED)
          && properties.containsKey(ClusterMeta.MEMORY_CAPACITY)) {
        float memoryUsed = (long) properties.get(ClusterMeta.MEMORY_USED) / (float) (1024*1024*1024);
        float memoryCapacity = (long) properties.get(ClusterMeta.MEMORY_CAPACITY) / (float) (1024*1024*1024);
        float memoryRate = memoryUsed / memoryCapacity * 100;

        String memoryInfo = String.format("%.2fGB / %.2fGB = %.2f",
            memoryUsed, memoryCapacity, memoryRate);
        sortProperties.put(ClusterMeta.MEMORY_USED + " / " + ClusterMeta.MEMORY_CAPACITY, memoryInfo + "%");
      }

      if (properties.containsKey(ClusterMeta.SERVER_START_TIME)) {
        sortProperties.put(ClusterMeta.SERVER_START_TIME, properties.get(ClusterMeta.SERVER_START_TIME));
      }
      if (properties.containsKey(ClusterMeta.STATUS)) {
        sortProperties.put(ClusterMeta.STATUS, properties.get(ClusterMeta.STATUS));
      }
      if (properties.containsKey(ClusterMeta.LAST_HEARTBEAT)) {
        sortProperties.put(ClusterMeta.LAST_HEARTBEAT, properties.get(ClusterMeta.LAST_HEARTBEAT));
      }
      if (properties.containsKey(ClusterMeta.INTP_PROCESS_LIST)) {
        sortProperties.put(ClusterMeta.INTP_PROCESS_LIST, properties.get(ClusterMeta.INTP_PROCESS_LIST));
      }

      HashMap<String, Object> node = new HashMap<String, Object>();
      node.put(ClusterMeta.NODE_NAME, nodeName);
      node.put(PROPERTIES, sortProperties);

      nodes.add(node);
    }

    return new JsonResponse(Response.Status.OK, "", nodes).build();
  }

  private String formatIntpLink(String intpName) {
    return String.format("<a href=\"/#/cluster/%s\">%s</a>", intpName, intpName);
  }

  /**
   * get node info by id
   */
  @GET
  @Path("/node/{nodeName}/{intpName}")
  @ZeppelinApi
  public Response getClusterNode(@PathParam("nodeName") String nodeName,
                                 @PathParam("intpName") String intpName){
    ArrayList<HashMap<String, Object>> intpProcesses = new ArrayList<HashMap<String, Object>>();

    Map<String, HashMap<String, Object>> intpMeta = null;
    if (isTest) {
      intpMeta = mockIntpMeta();
    } else {
      intpMeta = clusterManagerServer.getClusterMeta(ClusterMetaType.INTP_PROCESS_META, "");
    }

    // Number of calculation processes
    for (Map.Entry<String, HashMap<String, Object>> intpMetaEntity : intpMeta.entrySet()) {
      String intpNodeName = (String) intpMetaEntity.getValue().get(ClusterMeta.NODE_NAME);

      if (null != intpNodeName && intpNodeName.equals(nodeName)) {
        HashMap<String, Object> node = new HashMap<String, Object>();
        node.put(ClusterMeta.NODE_NAME, intpNodeName);
        node.put(PROPERTIES, intpMetaEntity.getValue());
        intpProcesses.add(node);
      }
    }

    return new JsonResponse(Response.Status.OK, "", intpProcesses).build();
  }


  private Map<String, HashMap<String, Object>> mockNodesMeta() {
    Map<String, HashMap<String, Object>> serverMeta = new HashMap<>();

    for (Integer i = 0; i < 32; i ++) {
      HashMap<String, Object> nodeMeta = new HashMap<>();
      nodeMeta.put(ClusterMeta.NODE_NAME, "127.0.0." + i.toString() + ":" + "8080");
      nodeMeta.put(ClusterMeta.SERVER_HOST, "127.0.0." + i.toString());
      nodeMeta.put(ClusterMeta.SERVER_PORT, "8080");
      nodeMeta.put(ClusterMeta.MEMORY_USED, 100);
      nodeMeta.put(ClusterMeta.MEMORY_CAPACITY, 4800);
      nodeMeta.put(ClusterMeta.CPU_USED, 200);
      nodeMeta.put(ClusterMeta.CPU_CAPACITY, 10000);
      nodeMeta.put(ClusterMeta.SERVER_HOST, "host1");
      nodeMeta.put(ClusterMeta.SERVER_PORT, 1084);
      nodeMeta.put(ClusterMeta.STATUS, ClusterMeta.ONLINE_STATUS);
      nodeMeta.put(ClusterMeta.SERVER_START_TIME, new Date());
      nodeMeta.put(ClusterMeta.LAST_HEARTBEAT, new Date());

      serverMeta.put("127.0.0." + i.toString() + ":" + "8080", nodeMeta);
    }

    return serverMeta;
  }

  private Map<String, HashMap<String, Object>> mockIntpMeta() {
    Map<String, HashMap<String, Object>> intpMeta = new HashMap<>();

    for (Integer i = 0; i < 42; i ++) {
      HashMap<String, Object> meta = new HashMap<>();
      meta.put(ClusterMeta.INTP_PROCESS_NAME, "sh:hzliuxun:" + i.toString());
      meta.put(ClusterMeta.NODE_NAME, "127.0.0.0:8080");
      meta.put(ClusterMeta.SERVER_HOST, "127.0.0." + i.toString());
      meta.put(ClusterMeta.SERVER_PORT, 8080);
      meta.put(ClusterMeta.INTP_TSERVER_HOST, "127.0.0." + i.toString());
      meta.put(ClusterMeta.INTP_TSERVER_PORT, 1084);
      meta.put(ClusterMeta.STATUS, ClusterMeta.ONLINE_STATUS);
      meta.put(ClusterMeta.INTP_START_TIME, new Date());
      meta.put(ClusterMeta.LAST_HEARTBEAT, new Date());

      intpMeta.put("sh:hzliuxun0:"+i, meta);
    }

    for (Integer i = 0; i < 32; i ++) {
      HashMap<String, Object> meta = new HashMap<>();
      meta.put(ClusterMeta.INTP_PROCESS_NAME, "sh:hzliuxun:" + i.toString());
      meta.put(ClusterMeta.NODE_NAME, "127.0.0.1:8080");
      meta.put(ClusterMeta.SERVER_HOST, "127.0.0." + i.toString());
      meta.put(ClusterMeta.SERVER_PORT, 8080);
      meta.put(ClusterMeta.INTP_TSERVER_HOST, "127.0.0." + i.toString());
      meta.put(ClusterMeta.INTP_TSERVER_PORT, 1084);
      meta.put(ClusterMeta.STATUS, ClusterMeta.ONLINE_STATUS);
      meta.put(ClusterMeta.INTP_START_TIME, new Date());
      meta.put(ClusterMeta.LAST_HEARTBEAT, new Date());

      intpMeta.put("sh:hzliuxun1:"+i, meta);
    }

    for (Integer i = 0; i < 22; i ++) {
      HashMap<String, Object> meta = new HashMap<>();
      meta.put(ClusterMeta.INTP_PROCESS_NAME, "sh:hzliuxun:" + i.toString());
      meta.put(ClusterMeta.NODE_NAME, "127.0.0.2:8080");
      meta.put(ClusterMeta.SERVER_HOST, "127.0.0." + i.toString());
      meta.put(ClusterMeta.SERVER_PORT, 8080);
      meta.put(ClusterMeta.INTP_TSERVER_HOST, "127.0.0." + i.toString());
      meta.put(ClusterMeta.INTP_TSERVER_PORT, 1084);
      meta.put(ClusterMeta.STATUS, ClusterMeta.ONLINE_STATUS);
      meta.put(ClusterMeta.INTP_START_TIME, new Date());
      meta.put(ClusterMeta.LAST_HEARTBEAT, new Date());

      intpMeta.put("sh:liuxun:"+i, meta);
    }

    return intpMeta;
  }
}
