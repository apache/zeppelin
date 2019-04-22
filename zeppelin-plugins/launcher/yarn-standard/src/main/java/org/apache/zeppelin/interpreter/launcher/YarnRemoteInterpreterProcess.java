package org.apache.zeppelin.interpreter.launcher;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.Constants;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterManagedProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.apache.zeppelin.interpreter.launcher.YarnConstants.ZEPPELIN_INTERPRETER_RPC_PORTRANGE;

public class YarnRemoteInterpreterProcess extends RemoteInterpreterManagedProcess {
  private static final Logger LOGGER = LoggerFactory.getLogger(YarnRemoteInterpreterProcess.class);

  private YarnClient yarnClient = null;
  private Properties properties;
  private String interpreterGroupId;

  public YarnRemoteInterpreterProcess(
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
      boolean isUserImpersonated,
      Properties properties) {
    super(intpRunner, zeppelinServerRPCPort, zeppelinServerRPCHost,
        interpreterPortRange, intpDir, localRepoDir,
        env, connectTimeout, interpreterSettingName,
        interpreterGroupId, isUserImpersonated);

    // Because need to modify the properties, make a clone
    this.properties = (Properties) properties.clone();
    this.interpreterGroupId = interpreterGroupId;

    yarnClient = new YarnClient();
  }

  @Override
  public void start(String userName) throws IOException {
    String intpYarnAppName = YarnClient.formatYarnAppName(interpreterGroupId);

    // Create a submarine interpreter process with hadoop submarine
    // First delete the submarine interpreter that may already exist in YARN
    yarnClient.deleteService(intpYarnAppName);
    try {
      // wait for delete submarine interpreter service
      Thread.sleep(3000);
    } catch (InterruptedException e) {
      LOGGER.error(e.getMessage(), e);
    }

    super.start(userName);
  }

  @Override
  public void stop() {
    String intpYarnAppName = YarnClient.formatYarnAppName(interpreterGroupId);
    Map<String, Object> mapAttrib = yarnClient.getAppServices(intpYarnAppName);
    if (mapAttrib.size() > 0) {
      yarnClient.deleteService(intpYarnAppName);
      try {
        // wait for delete submarine interpreter service
        Thread.sleep(3000);
      } catch (InterruptedException e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
  }

  @Override
  public void processStarted(int port, String host) {
    LOGGER.info("Interpreter container internal {}:{}", host, port);

    String intpYarnAppName = YarnClient.formatYarnAppName(interpreterGroupId);

    // setting port range of submarine interpreter container
    String intpPort = String.valueOf(Constants.ZEPPELIN_INTERPRETER_DEFAUlT_PORT);
    String intpAppHostIp = "";
    String intpAppHostPort = "";
    String intpAppContainerPort = "";
    boolean findExistIntpContainer = false;

    // The submarine interpreter already exists in the connection yarn
    // Or create a submarine interpreter
    // 1. Query the IP and port of the submarine interpreter process through the yarn client
    Map<String, String> exportPorts = yarnClient.getAppExportPorts(intpYarnAppName, intpPort);
    if (exportPorts.size() == 0) {
      // It may take a few seconds to query the docker container export port.
      try {
        Thread.sleep(3000);
        exportPorts = yarnClient.getAppExportPorts(intpYarnAppName, intpPort);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    if (exportPorts.containsKey(YarnClient.HOST_IP) && exportPorts.containsKey(YarnClient.HOST_PORT)
        && exportPorts.containsKey(YarnClient.CONTAINER_PORT)) {
      intpAppHostIp = (String) exportPorts.get(YarnClient.HOST_IP);
      intpAppHostPort = (String) exportPorts.get(YarnClient.HOST_PORT);
      intpAppContainerPort = (String) exportPorts.get(YarnClient.CONTAINER_PORT);
      if (StringUtils.equals(intpPort, intpAppContainerPort)) {
        findExistIntpContainer = true;
        LOGGER.info("Detection Submarine interpreter Container hostIp:{}, hostPort:{}, containerPort:{}.",
            intpAppHostIp, intpAppHostPort, intpAppContainerPort);
      }
    }

    if (findExistIntpContainer) {
      LOGGER.info("Interpreter container external {}:{}", intpAppHostIp, intpAppHostPort);
      super.processStarted(Integer.parseInt(intpAppHostPort), intpAppHostIp);
    } else {
      LOGGER.error("Cann't detection Submarine interpreter Container! {}", exportPorts.toString());
    }
  }
}
