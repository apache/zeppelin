package org.apache.zeppelin.interpreter.launcher;

import java.io.IOException;
import java.util.Map;

import org.apache.zeppelin.interpreter.remote.RemoteInterpreterManagedProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;

public class ClusterInterpreterProcess extends RemoteInterpreterManagedProcess {

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
    ClusterInterpreterCheckThread interpreterCheckThread = new ClusterInterpreterCheckThread(this);
    interpreterCheckThread.start();

    super.start(userName);
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
}
