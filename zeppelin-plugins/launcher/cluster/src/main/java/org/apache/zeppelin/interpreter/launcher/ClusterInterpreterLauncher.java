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

package org.apache.zeppelin.interpreter.launcher;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.cluster.ClusterManagerServer;
import org.apache.zeppelin.cluster.event.ClusterEvent;
import org.apache.zeppelin.cluster.event.ClusterEventListener;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterRunner;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterRunningProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.apache.zeppelin.cluster.event.ClusterEvent.CREATE_INTP_PROCESS;
import static org.apache.zeppelin.cluster.meta.ClusterMeta.INTP_TSERVER_HOST;
import static org.apache.zeppelin.cluster.meta.ClusterMeta.INTP_TSERVER_PORT;
import static org.apache.zeppelin.cluster.meta.ClusterMeta.ONLINE_STATUS;
import static org.apache.zeppelin.cluster.meta.ClusterMeta.SERVER_HOST;
import static org.apache.zeppelin.cluster.meta.ClusterMeta.SERVER_PORT;
import static org.apache.zeppelin.cluster.meta.ClusterMeta.STATUS;
import static org.apache.zeppelin.cluster.meta.ClusterMetaType.INTP_PROCESS_META;

/**
 * Interpreter Launcher which use cluster to launch the interpreter process.
 */
public class ClusterInterpreterLauncher extends StandardInterpreterLauncher
    implements ClusterEventListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterInterpreterLauncher.class);

  public static final int CHECK_META_INTERVAL = 500; // ms
  private InterpreterLaunchContext context;
  private ClusterManagerServer clusterServer = ClusterManagerServer.getInstance();

  public ClusterInterpreterLauncher(ZeppelinConfiguration zConf, RecoveryStorage recoveryStorage)
      throws IOException {
    super(zConf, recoveryStorage);
    clusterServer.addClusterEventListeners(ClusterManagerServer.CLUSTER_INTP_EVENT_TOPIC, this);
  }

  @Override
  public InterpreterClient launch(InterpreterLaunchContext context) throws IOException {
    LOGGER.info("Launching Interpreter: " + context.getInterpreterSettingGroup());

    this.context = context;
    this.properties = context.getProperties();
    int connectTimeout = getConnectTimeout();
    String intpGroupId = context.getInterpreterGroupId();

    HashMap<String, Object> intpProcMeta = clusterServer
        .getClusterMeta(INTP_PROCESS_META, intpGroupId).get(intpGroupId);
    if (null != intpProcMeta && intpProcMeta.containsKey(INTP_TSERVER_HOST)
        && intpProcMeta.containsKey(INTP_TSERVER_PORT) && intpProcMeta.containsKey(STATUS)
        && StringUtils.equals((String) intpProcMeta.get(STATUS), ONLINE_STATUS)) {
      // connect exist Interpreter Process
      String intpTserverHost = (String) intpProcMeta.get(INTP_TSERVER_HOST);
      int intpTserverPort = (int) intpProcMeta.get(INTP_TSERVER_PORT);
      return new RemoteInterpreterRunningProcess(
          context.getInterpreterSettingName(),
          connectTimeout,
          intpTserverHost,
          intpTserverPort);
    } else {
      // No process was found for the InterpreterGroup ID
      HashMap<String, Object> meta = clusterServer.getIdleNodeMeta();
      if (null == meta) {
        LOGGER.error("Don't get idle node meta, launch interpreter on local.");
        super.launch(context);
      }

      String srvHost = (String) meta.get(SERVER_HOST);
      String localhost = RemoteInterpreterUtils.findAvailableHostAddress();

      if (localhost.equalsIgnoreCase(srvHost)) {
        // launch interpreter on local
        return createInterpreterProcess(context);
      } else {
        int srvPort = (int) meta.get(SERVER_PORT);

        Gson gson = new Gson();
        String sContext = gson.toJson(context);

        Map<String, Object> mapEvent = new HashMap<>();
        mapEvent.put(CLUSTER_EVENT, CREATE_INTP_PROCESS);
        mapEvent.put(CLUSTER_EVENT_MSG, sContext);
        String strEvent = gson.toJson(mapEvent);
        // Notify other server in the cluster that the resource is idle to create an interpreter
        clusterServer.unicastClusterEvent(
            srvHost, srvPort, ClusterManagerServer.CLUSTER_INTP_EVENT_TOPIC, strEvent);

        // Find the ip and port of thrift registered by the remote interpreter process
        // through the cluster metadata
        HashMap<String, Object> intpMeta = clusterServer
            .getClusterMeta(INTP_PROCESS_META, intpGroupId).get(intpGroupId);

        int MAX_RETRY_GET_META = connectTimeout / ClusterInterpreterLauncher.CHECK_META_INTERVAL;
        int retryGetMeta = 0;
        while ((retryGetMeta++ < MAX_RETRY_GET_META)
            && (null == intpMeta || !intpMeta.containsKey(INTP_TSERVER_HOST)
            || !intpMeta.containsKey(INTP_TSERVER_PORT)) ) {
          try {
            Thread.sleep(CHECK_META_INTERVAL);
            intpMeta = clusterServer
                .getClusterMeta(INTP_PROCESS_META, intpGroupId).get(intpGroupId);
            LOGGER.warn("retry {} times to get {} meta!", retryGetMeta, intpGroupId);
          } catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
          }
        }

        // Check if the remote creation process is successful
        if (null == intpMeta || !intpMeta.containsKey(INTP_TSERVER_HOST)
            || !intpMeta.containsKey(INTP_TSERVER_PORT)) {
          String errorInfo = String.format("Creating process %s failed on remote server %s:%d",
              intpGroupId, srvHost, srvPort);
          throw new IOException(errorInfo);
        } else {
          // connnect remote interpreter process
          String intpTSrvHost = (String) intpMeta.get(INTP_TSERVER_HOST);
          int intpTSrvPort = (int) intpMeta.get(INTP_TSERVER_PORT);
          return new RemoteInterpreterRunningProcess(
              context.getInterpreterSettingName(),
              connectTimeout,
              intpTSrvHost,
              intpTSrvPort);
        }
      }
    }
  }

  @Override
  public void onClusterEvent(String msg) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(msg);
    }

    try {
      Gson gson = new Gson();
      Map<String, Object> mapEvent = gson.fromJson(msg,
          new TypeToken<Map<String, Object>>(){}.getType());
      String sEvent = (String) mapEvent.get(CLUSTER_EVENT);
      ClusterEvent clusterEvent = ClusterEvent.valueOf(sEvent);

      switch (clusterEvent) {
        case CREATE_INTP_PROCESS:
          // 1）Other zeppelin servers in the cluster send requests to create an interpreter process
          // 2）After the interpreter process is created, and the interpreter is started,
          //    the interpreter registers the thrift ip and port into the cluster metadata.
          // 3）Other servers connect through the IP and port of thrift in the cluster metadata,
          //    using this remote interpreter process
          String eventMsg = (String) mapEvent.get(CLUSTER_EVENT_MSG);
          InterpreterLaunchContext context = gson.fromJson(
              eventMsg, new TypeToken<InterpreterLaunchContext>() {}.getType());
          ClusterInterpreterProcess clusterInterpreterProcess = createInterpreterProcess(context);
          clusterInterpreterProcess.start(context.getUserName());
          break;
        default:
          LOGGER.error("Unknown clusterEvent:{}, msg:{} ", clusterEvent, msg);
          break;
      }
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  private ClusterInterpreterProcess createInterpreterProcess(InterpreterLaunchContext context) {
    ClusterInterpreterProcess clusterInterpreterProcess = null;
    try {
      this.properties = context.getProperties();
      InterpreterOption option = context.getOption();
      InterpreterRunner runner = context.getRunner();
      String intpSetGroupName = context.getInterpreterSettingGroup();
      String intpSetName = context.getInterpreterSettingName();
      int connectTimeout = getConnectTimeout();
      String localRepoPath = zConf.getInterpreterLocalRepoPath() + "/"
          + context.getInterpreterSettingId();

      clusterInterpreterProcess = new ClusterInterpreterProcess(
          runner != null ? runner.getPath() : zConf.getInterpreterRemoteRunnerPath(),
          context.getZeppelinServerRPCPort(),
          context.getZeppelinServerHost(),
          zConf.getInterpreterPortRange(),
          zConf.getInterpreterDir() + "/" + intpSetGroupName,
          localRepoPath,
          buildEnvFromProperties(context),
          connectTimeout,
          intpSetName,
          context.getInterpreterGroupId(),
          option.isUserImpersonate());
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }

    return clusterInterpreterProcess;
  }
}
