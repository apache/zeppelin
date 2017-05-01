package org.apache.zeppelin.cluster.yarn;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventPoller;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;

/**
 *
 */
public class RemoteInterpreterYarnProcess extends RemoteInterpreterProcess {

  private static final Logger logger = LoggerFactory.getLogger(RemoteInterpreterYarnProcess.class);

  private final YarnClient yarnClient;
  private final ApplicationSubmissionContext appContext;
  private final ApplicationId applicationId;

  private boolean isRunning = false;
  private ScheduledFuture monitor;
  private YarnApplicationState oldState;

  private String host = null;
  private int port = -1;

  public RemoteInterpreterYarnProcess(int connectTimeout, RemoteInterpreterProcessListener listener,
      ApplicationEventListener appListener, YarnClient yarnClient,
      ApplicationSubmissionContext appContext) {
    super(new RemoteInterpreterEventPoller(listener, appListener), connectTimeout);
    this.yarnClient = yarnClient;
    this.appContext = appContext;
    this.applicationId = appContext.getApplicationId();
  }

  @Override
  public String getHost() {
    return host;
  }

  private void setHost(String host) {
    this.host = host;
  }

  @Override
  public int getPort() {
    return port;
  }

  private void setPort(int port) {
    this.port = port;
  }

  @Override
  public void start(String userName, Boolean isUserImpersonate) {
    if (isUserImpersonate) {

    }
    try {
      yarnClient.submitApplication(appContext);
      monitor = Client.scheduledExecutorService
          .scheduleAtFixedRate(new ApplicationMonitor(), 1, 1, TimeUnit.SECONDS);
      while (!isRunning()) {
        Thread.sleep(500);
        logger.debug("Waiting until connected");
      }
    } catch (YarnException | IOException | InterruptedException e) {
      throw new InterpreterException(e);
    }
  }

  @Override
  public void stop() {
    isRunning = false;
    monitor.cancel(false);
  }

  @Override
  public boolean isRunning() {
    return isRunning;
  }

  private void setRunning(boolean running) {
    this.isRunning = running;
  }

  private class ApplicationMonitor implements Runnable {

    @Override
    public void run() {
      try {
        ApplicationReport applicationReport = yarnClient.getApplicationReport(applicationId);
        YarnApplicationState curState = applicationReport.getYarnApplicationState();
        switch (curState) {
            case NEW:
            case NEW_SAVING:
            case SUBMITTED:
            case ACCEPTED:
              if (null == oldState) {
                logger.info("new application added. applicationId: {}", applicationId);
              }
              oldState = curState;
              break;
            case RUNNING:
              if (!YarnApplicationState.RUNNING.equals(oldState)) {
                String host = applicationReport.getHost();
                int port = applicationReport.getRpcPort();
                logger
                    .info("applicationId {} started. Host: {}, port: {}", applicationId, host
                        , port);
                oldState = curState;
                setHost(host);
                setPort(port);
                setRunning(true);
              }
              break;
            case FINISHED:
              if (!YarnApplicationState.FINISHED.equals(oldState)) {
                logger.info("applicationId {} finished with final Status {}", applicationId,
                    applicationReport.getFinalApplicationStatus());
                setRunning(false);
                //TODO(jl): Handle it!!
              }
              break;
            case FAILED:
              if (!YarnApplicationState.FAILED.equals(oldState)) {
                logger.info("id {}, applicationId {} failed with final Status {}", applicationId,
                    applicationReport.getFinalApplicationStatus());
                setRunning(false);
                //TODO(jl): Handle it!!
              }
              break;
            case KILLED:
              if (!YarnApplicationState.KILLED.equals(oldState)) {
                logger.info("applicationId {} killed with final Status {}", applicationId,
                    applicationReport.getFinalApplicationStatus());
                setRunning(false);
                //TODO(jl): Handle it!!
              }
              break;
        }
      } catch (YarnException | IOException e) {
        logger.debug("Error occurs while fetching status of {}", applicationId, e);
      }
    }
  }
}
