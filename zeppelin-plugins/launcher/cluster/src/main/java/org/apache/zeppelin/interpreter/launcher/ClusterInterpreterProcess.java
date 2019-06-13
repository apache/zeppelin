package org.apache.zeppelin.interpreter.launcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.zeppelin.cluster.ClusterManagerServer;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterManagedProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.zeppelin.cluster.meta.ClusterMeta.INTP_TSERVER_HOST;
import static org.apache.zeppelin.cluster.meta.ClusterMeta.INTP_TSERVER_PORT;
import static org.apache.zeppelin.cluster.meta.ClusterMetaType.INTP_PROCESS_META;


public class ClusterInterpreterProcess extends RemoteInterpreterManagedProcess {
  private static final Logger LOGGER
      = LoggerFactory.getLogger(ClusterInterpreterProcess.class);

  private String intpHost = "";
  private int intpPort = 0;

  private ClusterManagerServer clusterServer = ClusterManagerServer.getInstance();

  public ClusterInterpreterProcess(
      String intpRunner,
      int zeppelinServerRPCPort,
      String zeppelinServerRPCHost,
      String interpreterPortRange,
      String intpDir,
      String localRepoDir,
      Map<String, String> env,
      int connectTimeout,
      String interpreterSettingName,
      String interpreterGroupId,
      boolean isUserImpersonated) {

    super(intpRunner,
      zeppelinServerRPCPort,
      zeppelinServerRPCHost,
      interpreterPortRange,
      intpDir,
      localRepoDir,
      env,
      connectTimeout,
      interpreterSettingName,
      interpreterGroupId,
      isUserImpersonated);
  }

  @Override
  public void start(String userName) throws IOException {
    CheckIntpRunStatusThread checkIntpRunStatusThread = new CheckIntpRunStatusThread(this);
    checkIntpRunStatusThread.start();

    super.start(userName);
  }

  @Override
  public void processStarted(int port, String host) {
    // Cluster mode, discovering interpreter processes through metadata registration
    this.intpHost = host;
    this.intpPort = port;
    super.processStarted(port, host);
  }

  @Override
  public String getHost() {
    return intpHost;
  }

  @Override
  public int getPort() {
    return intpPort;
  }

  @Override
  public boolean isRunning() {
    if (RemoteInterpreterUtils.checkIfRemoteEndpointAccessible(getHost(), getPort())) {
      return true;
    }
    return false;
  }

  @Override
  public String getErrorMessage() {
    return null;
  }

  // Metadata registered in the cluster by the interpreter process,
  // Keep the interpreter process started
  private class CheckIntpRunStatusThread extends Thread {
    private ClusterInterpreterProcess intpProcess;

    CheckIntpRunStatusThread(ClusterInterpreterProcess intpProcess) {
      this.intpProcess = intpProcess;
    }

    @Override
    public void run() {
      LOGGER.info("checkIntpRunStatusThread run() >>>");

      String intpGroupId = getInterpreterGroupId();

      HashMap<String, Object> intpMeta = clusterServer
          .getClusterMeta(INTP_PROCESS_META, intpGroupId).get(intpGroupId);
      int connectTimeout = intpProcess.getConnectTimeout();
      int retryGetMeta = connectTimeout / ClusterInterpreterLauncher.CHECK_META_INTERVAL;
      while ((retryGetMeta-- > 0)
          && (null == intpMeta || !intpMeta.containsKey(INTP_TSERVER_HOST)
          || !intpMeta.containsKey(INTP_TSERVER_PORT))) {
        try {
          Thread.sleep(ClusterInterpreterLauncher.CHECK_META_INTERVAL);
          intpMeta = clusterServer
              .getClusterMeta(INTP_PROCESS_META, intpGroupId).get(intpGroupId);
          LOGGER.info("retry {} times to get {} meta!", retryGetMeta, intpGroupId);
        } catch (InterruptedException e) {
          LOGGER.error(e.getMessage(), e);
        }

        if (null != intpMeta && intpMeta.containsKey(INTP_TSERVER_HOST)
            && intpMeta.containsKey(INTP_TSERVER_PORT)) {
          String intpHost = (String) intpMeta.get(INTP_TSERVER_HOST);
          int intpPort = (int) intpMeta.get(INTP_TSERVER_PORT);
          LOGGER.info("Found cluster interpreter {}:{}", intpHost, intpPort);

          intpProcess.processStarted(intpPort, intpHost);
        }
      }

      if (null == intpMeta || !intpMeta.containsKey(INTP_TSERVER_HOST)
          || !intpMeta.containsKey(INTP_TSERVER_PORT)) {
        LOGGER.error("Can not found interpreter meta!");
      }

      LOGGER.info("checkIntpRunStatusThread run() <<<");
    }
  }
}
