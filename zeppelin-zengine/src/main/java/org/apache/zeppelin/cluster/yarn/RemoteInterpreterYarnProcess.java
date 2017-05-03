package org.apache.zeppelin.cluster.yarn;

import java.io.IOException;
import java.net.InetAddress;
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

import static org.apache.hadoop.yarn.api.records.YarnApplicationState.FAILED;
import static org.apache.hadoop.yarn.api.records.YarnApplicationState.FINISHED;
import static org.apache.hadoop.yarn.api.records.YarnApplicationState.KILLED;
import static org.apache.hadoop.yarn.api.records.YarnApplicationState.RUNNING;

/**
 *
 */
public class RemoteInterpreterYarnProcess extends RemoteInterpreterProcess {

  private static final Logger logger = LoggerFactory.getLogger(RemoteInterpreterYarnProcess.class);

  private final YarnClient yarnClient;
  private final ApplicationSubmissionContext appContext;

  private ApplicationId applicationId;
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
      this.applicationId = appContext.getApplicationId();
      yarnClient.submitApplication(appContext);
      monitor = Client.scheduledExecutorService
          .scheduleAtFixedRate(new ApplicationMonitor(), 1, 1, TimeUnit.SECONDS);
      while (!isRunning()) {
        if (oldState == FINISHED || oldState == FAILED || oldState == KILLED) {
          throw new YarnException("Failed to initialize yarn application: " + applicationId);
        }
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
    if (null != oldState && oldState != FINISHED && oldState != FAILED && oldState != KILLED) {
      try {
        yarnClient.killApplication(applicationId);
      } catch (YarnException | IOException e) {
        logger.debug("error while killing application: {}", applicationId);
      }
    }
    oldState = null;
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
              if (!RUNNING.equals(oldState)) {
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
              if (!FINISHED.equals(oldState)) {
                logger.info("applicationId {} finished with final Status {}", applicationId,
                    applicationReport.getFinalApplicationStatus());
                oldState = FINISHED;
                stop();
                //TODO(jl): Handle it!!
              }
              break;
            case FAILED:
              if (!FAILED.equals(oldState)) {
                logger.info("id {}, applicationId {} failed with final Status {}", applicationId,
                    applicationReport.getFinalApplicationStatus());
                oldState = FAILED;
                stop();
                //TODO(jl): Handle it!!
              }
              break;
            case KILLED:
              if (!KILLED.equals(oldState)) {
                logger.info("applicationId {} killed with final Status {}", applicationId,
                    applicationReport.getFinalApplicationStatus());
                oldState = KILLED;
                stop();
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
