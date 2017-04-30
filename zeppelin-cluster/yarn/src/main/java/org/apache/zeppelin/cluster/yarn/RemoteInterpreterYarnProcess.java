package org.apache.zeppelin.cluster.yarn;

import java.io.IOException;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class RemoteInterpreterYarnProcess extends RemoteInterpreterProcess {

  private static final Logger logger = LoggerFactory.getLogger(RemoteInterpreterYarnProcess.class);

  private final YarnClient yarnClient;
  private final ApplicationSubmissionContext appContext;

  public RemoteInterpreterYarnProcess(int connectTimeout,
      RemoteInterpreterProcessListener listener,
      ApplicationEventListener appListener, YarnClient yarnClient,
      ApplicationSubmissionContext appContext) {
    super(connectTimeout, listener, appListener);
    this.yarnClient = yarnClient;
    this.appContext = appContext;
  }

  @Override
  public String getHost() {
    return null;
  }

  @Override
  public int getPort() {
    return 0;
  }

  @Override
  public void start(String userName, Boolean isUserImpersonate) {
    if (isUserImpersonate) {

    }
    try {
      yarnClient.submitApplication(appContext);
    } catch (YarnException | IOException e) {
      throw new InterpreterException(e);
    }
  }

  @Override
  public void stop() {

  }

  @Override
  public boolean isRunning() {
    return false;
  }
}
