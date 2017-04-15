package org.apache.zeppelin.cluster.yarn;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.ByteBuffer;
import java.security.PrivilegedExceptionAction;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.protocolrecords.RegisterApplicationMasterResponse;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerExitStatus;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.ContainerState;
import org.apache.hadoop.yarn.api.records.ContainerStatus;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.timeline.TimelineEntity;
import org.apache.hadoop.yarn.api.records.timeline.TimelineEvent;
import org.apache.hadoop.yarn.api.records.timeline.TimelinePutResponse;
import org.apache.hadoop.yarn.client.api.AMRMClient;
import org.apache.hadoop.yarn.client.api.TimelineClient;
import org.apache.hadoop.yarn.client.api.async.AMRMClientAsync;
import org.apache.hadoop.yarn.client.api.async.NMClientAsync;
import org.apache.hadoop.yarn.client.api.async.impl.NMClientAsyncImpl;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.security.AMRMTokenIdentifier;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ApplicationMaster {
  private static final Logger logger = LoggerFactory.getLogger(ApplicationMaster.class);


  /**
   *
   */
  public static enum DSEvent {
    DS_APP_ATTEMPT_START, DS_APP_ATTEMPT_END, DS_CONTAINER_START, DS_CONTAINER_END
  }


  /**
   *
   */
  public static enum DSEntity {
    DS_APP_ATTEMPT, DS_CONTAINER
  }


  private final Configuration conf;

  private ByteBuffer allTokens;

  private UserGroupInformation appSubmitterUgi;

  private ApplicationAttemptId appAttemptId;

  private AMRMClientAsync amRMClient;

  private NMClientAsync nmClientAsync;

  private String domainId;

  TimelineClient timelineClient;

  private NMCallbackHandler nmCallbackHandler;

  private String appMasterHostName = "";

  private int appMasterRpcPort = -1;

  private String appMasterTrackingUrl = "";

  private int containerMemory = 128;

  private int containerVCores = 1;

  private int numTotalContainers = 1;

  private int requestPriority = 0;

  private boolean done = false;

  private AtomicInteger numRequestedContainers = new AtomicInteger();

  private AtomicInteger numAllocatedContainers = new AtomicInteger();

  private AtomicInteger numCompletedContainers = new AtomicInteger();

  private AtomicInteger numFailedContainers = new AtomicInteger();

  private List<Thread> launchThreads = new ArrayList<>();

  public static void main(String[] args) {
    boolean result = false;
    try {
      ApplicationMaster appMaster = new ApplicationMaster();
      boolean doRun = appMaster.init(args);
      if (!doRun) {
        System.exit(0);
      }
      appMaster.run();
      result = appMaster.finish();
    } catch (Throwable t) {
      logger.error("Error running ApplicationMaster", t);
      ExitUtil.terminate(1, t);
    }

    if (result) {
      logger.info("Application Master completed successfully. exiting");
      System.exit(0);
    } else {
      logger.info("Application Master failed. exiting");
      System.exit(2);
    }
  }

  public ApplicationMaster() {
    Configuration hadoopConf = new Configuration(true);
    this.conf = new YarnConfiguration(hadoopConf);
  }

  public boolean init(String[] args) throws ParseException, IOException {
    Map<String, String> envs = System.getenv();
    for (Map.Entry<String, String> env : envs.entrySet()) {
      logger.info("env. key: " + env.getKey() + ", value: " + env.getValue());
    }

    if (envs.containsKey(ApplicationConstants.Environment.CONTAINER_ID.name())) {
      ContainerId containerId = ConverterUtils
          .toContainerId(envs.get(ApplicationConstants.Environment.CONTAINER_ID.name()));
      appAttemptId = containerId.getApplicationAttemptId();
    } else {
      throw new IllegalArgumentException("Application Attempt Id not set in the environment");
    }

    logger.info("Application master for app, appId={}, clustertimestamp={}, attemptId={}",
        appAttemptId.getApplicationId().getId(),
        appAttemptId.getApplicationId().getClusterTimestamp(), appAttemptId.getAttemptId());

    if (envs.containsKey(DSConstants.DISTRIBUTEDSHELLTIMELINEDOMAIN)) {
      domainId = envs.get(DSConstants.DISTRIBUTEDSHELLTIMELINEDOMAIN);
    }

    return true;
  }

  public void run() throws IOException, InterruptedException, YarnException {
    logger.info("Starting ApplicationMaster");

    Credentials credentials = UserGroupInformation.getCurrentUser().getCredentials();
    DataOutputBuffer dob = new DataOutputBuffer();
    credentials.writeTokenStorageToStream(dob);

    Iterator<Token<?>> iter = credentials.getAllTokens().iterator();
    logger.info("Executing with tokens: ");
    while (iter.hasNext()) {
      Token<?> token = iter.next();
      logger.info(token.toString());
      if (token.getKind().equals(AMRMTokenIdentifier.KIND_NAME)) {
        iter.remove();
      }
    }
    allTokens = ByteBuffer.wrap(dob.getData(), 0, dob.getLength());

    String appSubmitterUserName = System.getenv(ApplicationConstants.Environment.USER.name());

    appSubmitterUgi = UserGroupInformation.createRemoteUser(appSubmitterUserName);
    appSubmitterUgi.addCredentials(credentials);

    AMRMClientAsync.CallbackHandler rmCallbackHandler = new RMCallbackHandler();
    amRMClient = AMRMClientAsync.createAMRMClientAsync(1000, rmCallbackHandler);
    amRMClient.init(conf);
    amRMClient.start();

    nmCallbackHandler = new NMCallbackHandler(this);
    nmClientAsync = new NMClientAsyncImpl(nmCallbackHandler);
    nmClientAsync.init(conf);
    nmClientAsync.start();

    startTimelineClient(conf);
    if (timelineClient != null) {
      publishApplicationAttemptEvent(timelineClient, appAttemptId.toString(),
          DSEvent.DS_APP_ATTEMPT_START, domainId, appSubmitterUgi);
    }

    appMasterHostName = NetUtils.getHostname();

    RegisterApplicationMasterResponse response = amRMClient
        .registerApplicationMaster(appMasterHostName, appMasterRpcPort, appMasterTrackingUrl);

    int maxMem = response.getMaximumResourceCapability().getMemory();
    logger.info("Max mem capability of resources in this cluster {}", maxMem);

    int maxVCores = response.getMaximumResourceCapability().getVirtualCores();
    logger.info("Max vcores capability of resources in this cluster {}", maxVCores);

    if (containerMemory > maxMem) {
      containerMemory = maxMem;
      logger.info("container memory changed to {} because of limitation of resources", maxMem);
    }

    if (containerVCores > maxVCores) {
      containerVCores = maxVCores;
      logger.info("container vcores changed to {} because of limitation of resources", maxVCores);
    }

    List<Container> previousAMRunningContainers = response.getContainersFromPreviousAttempts();
    numAllocatedContainers.addAndGet(previousAMRunningContainers.size());

    int numTotalContainersToRequest = numTotalContainers - previousAMRunningContainers.size();

    for (int i = 0; i < numTotalContainersToRequest; i++) {
      AMRMClient.ContainerRequest containerRequest = setupContainerAskForRM();
      amRMClient.addContainerRequest(containerRequest);
    }
    numRequestedContainers.set(numTotalContainers);

  }

  public boolean finish() {
    while (!done && (numCompletedContainers.get() != numTotalContainers)) {
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
      }
    }

    if (timelineClient != null) {
      publishApplicationAttemptEvent(timelineClient, appAttemptId.toString(),
          DSEvent.DS_APP_ATTEMPT_END, domainId, appSubmitterUgi);
    }

    for (Thread launchThread : launchThreads) {
      try {
        launchThread.join(10000);
      } catch (InterruptedException e) {
        logger.info("Exception thrown in thread join", e);
      }
    }

    logger.info("Application completed. Stopping running containers");
    nmClientAsync.stop();

    logger.info("Application completed. Signalling finish to RM");

    FinalApplicationStatus appStatus;
    String appMessage = null;
    boolean success = true;
    if (numFailedContainers.get() == 0 && numCompletedContainers.get() == numTotalContainers) {
      appStatus = FinalApplicationStatus.SUCCEEDED;
    } else {
      appStatus = FinalApplicationStatus.FAILED;
      appMessage =
          "Diagnostics. total=" + numTotalContainers + ",completed=" + numCompletedContainers.get()
              + ", allocated=" + numAllocatedContainers.get() + ", failed=" + numFailedContainers
              .get();
      logger.info(appMessage);
      success = false;
    }
    try {
      amRMClient.unregisterApplicationMaster(appStatus, appMessage, null);
    } catch (YarnException | IOException e) {
      logger.error("Failed to unregister application", e);
    }

    amRMClient.stop();

    if (null != timelineClient) {
      timelineClient.stop();
    }

    return success;
  }

  private AMRMClient.ContainerRequest setupContainerAskForRM() {
    Priority pri = Priority.newInstance(requestPriority);

    Resource capability = Resource.newInstance(containerMemory, containerVCores);

    AMRMClient.ContainerRequest request =
        new AMRMClient.ContainerRequest(capability, null, null, pri);
    logger.info("Requested container ask: {}", request.toString());

    return request;
  }

  private void startTimelineClient(final Configuration conf)
      throws YarnException, IOException, InterruptedException {
    try {
      appSubmitterUgi.doAs(new PrivilegedExceptionAction<Void>() {
        @Override
        public Void run() throws Exception {
          if (conf.getBoolean(YarnConfiguration.TIMELINE_SERVICE_ENABLED,
              YarnConfiguration.DEFAULT_TIMELINE_SERVICE_ENABLED)) {
            // Creating the Timeline Client
            timelineClient = TimelineClient.createTimelineClient();
            timelineClient.init(conf);
            timelineClient.start();
          } else {
            timelineClient = null;
            logger.warn("Timeline service is not enabled");
          }
          return null;
        }
      });
    } catch (UndeclaredThrowableException e) {
      throw new YarnException(e.getCause());
    }
  }

  private static void publishApplicationAttemptEvent(final TimelineClient timelineClient,
      String appAttemptId, DSEvent appEvent, String domainId, UserGroupInformation ugi) {
    final TimelineEntity entity = new TimelineEntity();
    entity.setEntityId(appAttemptId);
    entity.setEntityType(DSEntity.DS_APP_ATTEMPT.toString());
    entity.setDomainId(domainId);
    entity.addPrimaryFilter("user", ugi.getShortUserName());
    TimelineEvent event = new TimelineEvent();
    event.setEventType(appEvent.toString());
    event.setTimestamp(System.currentTimeMillis());
    entity.addEvent(event);
    try {
      timelineClient.putEntities(entity);
    } catch (YarnException | IOException e) {
      logger.error(
          "App Attempt " + (appEvent.equals(DSEvent.DS_APP_ATTEMPT_START) ? "start" : "end")
              + " event could not be published for " + appAttemptId, e);
    }
  }

  private static void publishContainerStartEvent(final TimelineClient timelineClient,
      Container container, String domainId, UserGroupInformation ugi) {
    final TimelineEntity entity = new TimelineEntity();
    entity.setEntityId(container.getId().toString());
    entity.setEntityType(DSEntity.DS_CONTAINER.toString());
    entity.setDomainId(domainId);
    entity.addPrimaryFilter("user", ugi.getShortUserName());
    TimelineEvent event = new TimelineEvent();
    event.setTimestamp(System.currentTimeMillis());
    event.setEventType(DSEvent.DS_CONTAINER_START.toString());
    event.addEventInfo("Node", container.getNodeId().toString());
    event.addEventInfo("Resources", container.getResource().toString());
    entity.addEvent(event);
    try {
      ugi.doAs(new PrivilegedExceptionAction<TimelinePutResponse>() {
        @Override
        public TimelinePutResponse run() throws Exception {
          return timelineClient.putEntities(entity);
        }
      });
    } catch (Exception e) {
      logger
          .error("Container start event could not be published for " + container.getId().toString(),
              e instanceof UndeclaredThrowableException ? e.getCause() : e);
    }
  }

  private static void publishContainerEndEvent(final TimelineClient timelineClient,
      ContainerStatus container, String domainId, UserGroupInformation ugi) {
    final TimelineEntity entity = new TimelineEntity();
    entity.setEntityId(container.getContainerId().toString());
    entity.setEntityType(DSEntity.DS_CONTAINER.toString());
    entity.setDomainId(domainId);
    entity.addPrimaryFilter("user", ugi.getShortUserName());
    TimelineEvent event = new TimelineEvent();
    event.setTimestamp(System.currentTimeMillis());
    event.setEventType(DSEvent.DS_CONTAINER_END.toString());
    event.addEventInfo("State", container.getState().name());
    event.addEventInfo("Exit Status", container.getExitStatus());
    entity.addEvent(event);
    try {
      timelineClient.putEntities(entity);
    } catch (YarnException | IOException e) {
      logger.error(
          "Container end event could not be published for " + container.getContainerId().toString(),
          e);
    }
  }

  private class RMCallbackHandler implements AMRMClientAsync.CallbackHandler {
    @Override
    public void onContainersCompleted(List<ContainerStatus> completedContainers) {
      logger.info(
          "Got response from RM for container ask, completedCnt=" + completedContainers.size());
      for (ContainerStatus containerStatus : completedContainers) {
        logger.info(appAttemptId + " got container status for containerID=" + containerStatus
            .getContainerId() + ", state=" + containerStatus.getState() + ", exitStatus="
            + containerStatus.getExitStatus() + ", diagnostics=" + containerStatus
            .getDiagnostics());

        // non complete containers should not be here
        assert (containerStatus.getState() == ContainerState.COMPLETE);

        // increment counters for completed/failed containers
        int exitStatus = containerStatus.getExitStatus();
        if (0 != exitStatus) {
          // container failed
          if (ContainerExitStatus.ABORTED != exitStatus) {
            // shell script failed
            // counts as completed
            numCompletedContainers.incrementAndGet();
            numFailedContainers.incrementAndGet();
          } else {
            // container was killed by framework, possibly preempted
            // we should re-try as the container was lost for some reason
            numAllocatedContainers.decrementAndGet();
            numRequestedContainers.decrementAndGet();
            // we do not need to release the container as it would be done
            // by the RM
          }
        } else {
          // nothing to do
          // container completed successfully
          numCompletedContainers.incrementAndGet();
          logger.info("Container completed successfully." + ", containerId=" + containerStatus
              .getContainerId());
        }
        if (timelineClient != null) {
          publishContainerEndEvent(timelineClient, containerStatus, domainId, appSubmitterUgi);
        }
      }
    }

    @Override
    public void onContainersAllocated(List<Container> allocatedContainers) {
      logger.info(
          "Got response from RM for container ask, allocatedCnt=" + allocatedContainers.size());
      numAllocatedContainers.addAndGet(allocatedContainers.size());
      for (Container allocatedContainer : allocatedContainers) {
        logger.info(
            "Launching shell command on a new container." + ", containerId=" + allocatedContainer
                .getId() + ", containerNode=" + allocatedContainer.getNodeId().getHost() + ":"
                + allocatedContainer.getNodeId().getPort() + ", containerNodeURI="
                + allocatedContainer.getNodeHttpAddress() + ", containerResourceMemory"
                + allocatedContainer.getResource().getMemory() + ", containerResourceVirtualCores"
                + allocatedContainer.getResource().getVirtualCores());
        // + ", containerToken"
        // +allocatedContainer.getContainerToken().getIdentifier().toString());

        LaunchContainerRunnable runnableLaunchContainer =
            new LaunchContainerRunnable(allocatedContainer, nmCallbackHandler);
        Thread launchThread = new Thread(runnableLaunchContainer);

        // launch and start the container on a separate thread to keep
        // the main thread unblocked
        // as all containers may not be allocated at one go.
        launchThreads.add(launchThread);
        launchThread.start();
      }

    }

    @Override
    public void onShutdownRequest() {
      done = true;
    }

    @Override
    public void onNodesUpdated(List<NodeReport> list) {

    }

    @Override
    public float getProgress() {
      return (float) numCompletedContainers.get() / numTotalContainers;
    }

    @Override
    public void onError(Throwable throwable) {
      done = true;
      amRMClient.stop();
    }
  }


  private class NMCallbackHandler implements NMClientAsync.CallbackHandler {
    private ConcurrentMap<ContainerId, Container> containers = new ConcurrentHashMap<>();
    private final ApplicationMaster applicationMaster;

    public NMCallbackHandler(ApplicationMaster applicationMaster) {
      this.applicationMaster = applicationMaster;
    }

    public void addContainer(ContainerId containerId, Container container) {
      containers.putIfAbsent(containerId, container);
    }

    @Override
    public void onContainerStarted(ContainerId containerId, Map<String, ByteBuffer> map) {
      logger.debug("Succeeded to start Container {}", containerId);
      Container container = containers.get(containerId);
      if (container != null) {
        applicationMaster.nmClientAsync.getContainerStatusAsync(containerId, container.getNodeId());
      }
      if (applicationMaster.timelineClient != null) {
        ApplicationMaster
            .publishContainerStartEvent(timelineClient, container, applicationMaster.domainId,
                applicationMaster.appSubmitterUgi);
      }
    }

    @Override
    public void onContainerStatusReceived(ContainerId containerId,
        ContainerStatus containerStatus) {
      logger.debug("Container status: id={}, status={}", containerId, containerStatus);

    }

    @Override
    public void onContainerStopped(ContainerId containerId) {
      logger.debug("Succeeded to stop Container {}", containerId);
      containers.remove(containerId);
    }

    @Override
    public void onStartContainerError(ContainerId containerId, Throwable throwable) {
      logger.error("Failed to start Container {}", containerId);
      containers.remove(containerId);
      applicationMaster.numCompletedContainers.incrementAndGet();
      applicationMaster.numFailedContainers.incrementAndGet();
    }

    @Override
    public void onGetContainerStatusError(ContainerId containerId, Throwable throwable) {
      logger.error("Failed to query the status of Container {}", containerId);
    }

    @Override
    public void onStopContainerError(ContainerId containerId, Throwable throwable) {
      logger.error("Failed to stop Container {}", containerId);
      containers.remove(containerId);
    }
  }


  private class LaunchContainerRunnable implements Runnable {
    Container container;
    NMCallbackHandler containerListener;

    public LaunchContainerRunnable(Container container, NMCallbackHandler containerListener) {
      this.container = container;
      this.containerListener = containerListener;
    }

    @Override
    public void run() {
      logger.info("Setting up container launch container for containerid={}", container.getId());

      Map<String, LocalResource> localResources = new HashMap<>();

      Vector<CharSequence> vargs = new Vector<>(30);
      vargs.add("echo \"madeng\"");
      vargs.add("1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/stdout");
      vargs.add("2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/stderr");
      String command = Joiner.on(" ").join(vargs);
      ContainerLaunchContext ctx = ContainerLaunchContext
          .newInstance(localResources, System.getenv(), Lists.newArrayList(command), null,
              allTokens.duplicate(), null);
      containerListener.addContainer(container.getId(), container);
      nmClientAsync.startContainerAsync(container, ctx);
    }
  }

}
