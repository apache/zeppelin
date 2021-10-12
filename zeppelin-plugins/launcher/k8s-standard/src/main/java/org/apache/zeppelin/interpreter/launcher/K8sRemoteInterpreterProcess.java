/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterManagedProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterServer;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hubspot.jinjava.Jinjava;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.dsl.ParameterNamespaceListVisitFromServerGetDeleteRecreateWaitApplicable;

public class K8sRemoteInterpreterProcess extends RemoteInterpreterManagedProcess {
  private static final Logger LOGGER = LoggerFactory.getLogger(K8sRemoteInterpreterProcess.class);
  private static final int K8S_INTERPRETER_SERVICE_PORT = 12321;
  private final KubernetesClient client;
  private final String interpreterNamespace;
  private final String interpreterGroupName;
  private final File specTemplates;
  private final String containerImage;
  private final Properties properties;

  private final String podName;
  private final String sparkImage;

  // Pod Forward
  private final boolean portForward;
  private LocalPortForward localPortForward;

  private final boolean timeoutDuringPending;

  private final AtomicBoolean started = new AtomicBoolean(false);

  private static final String SPARK_DRIVER_MEMORY = "spark.driver.memory";
  private static final String SPARK_DRIVER_MEMORY_OVERHEAD = "spark.driver.memoryOverhead";
  private static final String SPARK_DRIVER_CORES = "spark.driver.cores";
  private static final String SPARK_CONTAINER_IMAGE = "zeppelin.k8s.spark.container.image";
  private static final String ENV_SERVICE_DOMAIN = "SERVICE_DOMAIN";
  private static final String ENV_ZEPPELIN_HOME = "ZEPPELIN_HOME";

  public K8sRemoteInterpreterProcess(
          KubernetesClient client,
          String interpreterNamespace,
          File specTemplates,
          String containerImage,
          String interpreterGroupId,
          String interpreterGroupName,
          String interpreterSettingName,
          Properties properties,
          Map<String, String> envs,
          String intpEventServerHost,
          int intpEventServerPort,
          boolean portForward,
          String sparkImage,
          int connectTimeout,
          int connectionPoolSize,
          boolean isUserImpersonatedForSpark,
          boolean timeoutDuringPending
  ) {
    super(intpEventServerPort,
          intpEventServerHost,
          String.format("%d:%d", K8S_INTERPRETER_SERVICE_PORT, K8S_INTERPRETER_SERVICE_PORT),
          "${ZEPPELIN_HOME}/interpreter/" + interpreterGroupName,
          "/tmp/local-repo",
          envs,
          connectTimeout,
          connectionPoolSize,
          interpreterSettingName,
          interpreterGroupId,
          isUserImpersonatedForSpark);
    this.client = client;
    this.interpreterNamespace = interpreterNamespace;
    this.specTemplates = specTemplates;
    this.containerImage = containerImage;
    this.interpreterGroupName = interpreterGroupName;
    this.properties = properties;
    this.portForward = portForward;
    this.sparkImage = sparkImage;
    this.podName = interpreterGroupName.toLowerCase() + "-"
        + RandomStringUtils.randomAlphabetic(6).toLowerCase();
    this.timeoutDuringPending = timeoutDuringPending;
  }

  /**
   * @return Get interpreter pod name
   */
  public String getPodName() {
    return podName;
  }

  /**
   * @return Get namespace
   */
  public String getInterpreterNamespace() {
    return interpreterNamespace;
  }

  /**
   * Get the service account. If user does not set the service account from the interpreter settings, return default.
   * @return the service account
   */
  public String getServiceAccount(){
    if(properties.containsKey("zeppelin.k8s.interpreter.serviceAccount")){
      return properties.getProperty("zeppelin.k8s.interpreter.serviceAccount");
    }
    else{
      return "default";
    }
  }

  @Override
  public void start(String userName) throws IOException {

    Properties templateProperties = getTemplateBindings(userName);
    // create new pod
    apply(specTemplates, false, templateProperties);

    // special handling if we doesn't want timeout the process during lifecycle phase pending
    if (!timeoutDuringPending) {
      // WATCH
      PodPhaseWatcher podWatcher = new PodPhaseWatcher(
          phase -> StringUtils.equalsAnyIgnoreCase(phase, "Succeeded", "Failed", "Running"));
      try (Watch watch = client.pods().inNamespace(interpreterNamespace).withName(podName).watch(podWatcher)) {
        podWatcher.getCountDownLatch().await();
      } catch (InterruptedException e) {
        LOGGER.error("Interrupt received during waiting for Running phase. Try to stop the interpreter and interrupt the current thread.", e);
        processStopped("Start process was interrupted during waiting for Running phase");
        stop();
        Thread.currentThread().interrupt();
      }
    }

    long startTime = System.currentTimeMillis();
    long timeoutTime = startTime + getConnectTimeout();

    // wait until interpreter send started message through thrift rpc
    synchronized (started) {
      while (!started.get() && !Thread.currentThread().isInterrupted()) {
        long timeToTimeout = timeoutTime - System.currentTimeMillis();
        if (timeToTimeout <= 0) {
          processStopped("The start process was aborted while waiting for the interpreter to start. PodPhase before stop: " + getPodPhase());
          stop();
          throw new IOException("Launching zeppelin interpreter on kubernetes is time out, kill it now");
        }
        try {
          started.wait(timeToTimeout);
        } catch (InterruptedException e) {
          LOGGER.error("Interrupt received during started wait. Try to stop the interpreter and interrupt the current thread.", e);
          processStopped("The start process was interrupted while waiting for the interpreter to start. PodPhase before stop: " + getPodPhase());
          stop();
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  @Override
  public void stop() {
    super.stop();
    // WATCH for soft shutdown
    PodPhaseWatcher podWatcher = new PodPhaseWatcher(phase -> StringUtils.equalsAny(phase, "Succeeded", "Failed"));
    try (Watch watch = client.pods().inNamespace(interpreterNamespace).withName(podName).watch(podWatcher)) {
      if (!podWatcher.getCountDownLatch().await(RemoteInterpreterServer.DEFAULT_SHUTDOWN_TIMEOUT + 500L,
          TimeUnit.MILLISECONDS)) {
        LOGGER.warn("Pod {} doesn't terminate in time", podName);
      }
    } catch (InterruptedException e) {
      LOGGER.error("Interruption received while waiting for stop.", e);
      processStopped("Stop process was interrupted during termination");
      Thread.currentThread().interrupt();
    }
    Properties templateProperties = getTemplateBindings(null);
    // delete pod
    try {
      apply(specTemplates, true, templateProperties);
    } catch (IOException e) {
      LOGGER.info("Error on removing interpreter pod", e);
    }
    if (portForward && localPortForward != null) {
      LOGGER.info("Stopping Port Forwarding");
      try {
        localPortForward.close();
      } catch (IOException e) {
        LOGGER.info("Error on closing Port Forwarding", e);
      }
    }
  }

  @Override
  public boolean isRunning() {
    return "Running".equalsIgnoreCase(getPodPhase()) && started.get();
  }

  public String getPodPhase() {
    try {
      Pod pod = client.pods().inNamespace(interpreterNamespace).withName(podName).get();
      if (pod != null) {
        PodStatus status = pod.getStatus();
        if (status != null) {
          return status.getPhase();
        }
      }
    } catch (Exception e) {
      LOGGER.error("Can't get pod phase", e);
    }
    return "Unknown";
  }
  /**
   * Apply spec file(s) in the path.
   * @param path Path to the K8s resources
   * @param delete set to true, the K8s resources are deleted
   * @param templateProperties properties to enrich the template
   */
  void apply(File path, boolean delete, Properties templateProperties) throws IOException {
    if (path.getName().startsWith(".") || path.isHidden() || path.getName().endsWith("~")) {
      LOGGER.info("Skip {}", path.getAbsolutePath());
      return;
    }

    if (path.isDirectory()) {
      File[] files = path.listFiles();
      Arrays.sort(files);
      if (delete) {
        ArrayUtils.reverse(files);
      }

      for (File f : files) {
        apply(f, delete, templateProperties);
      }
    } else if (path.isFile()) {
      K8sSpecTemplate specTemplate = new K8sSpecTemplate();
      specTemplate.loadProperties(templateProperties);
      String template = specTemplate.render(path);
      ParameterNamespaceListVisitFromServerGetDeleteRecreateWaitApplicable<HasMetadata> k8sObjects = client.load(IOUtils.toInputStream(template, StandardCharsets.UTF_8));
      LOGGER.info("Apply {} with {} K8s Objects", path.getAbsolutePath(), k8sObjects.get().size());
      LOGGER.debug(template);
      if (delete) {
        k8sObjects.inNamespace(interpreterNamespace).delete();
      } else {
        k8sObjects.inNamespace(interpreterNamespace).createOrReplace();
      }
    } else {
      LOGGER.error("Can't apply {}", path.getAbsolutePath());
    }
  }

  @VisibleForTesting
  Properties getTemplateBindings(String userName) {
    Properties k8sProperties = new Properties();

    // k8s template properties
    k8sProperties.put("zeppelin.k8s.interpreter.user", String.valueOf(userName).trim());
    k8sProperties.put("zeppelin.k8s.interpreter.namespace", getInterpreterNamespace());
    k8sProperties.put("zeppelin.k8s.interpreter.pod.name", getPodName());
    k8sProperties.put("zeppelin.k8s.interpreter.serviceAccount", getServiceAccount());
    k8sProperties.put("zeppelin.k8s.interpreter.container.name", interpreterGroupName.toLowerCase());
    k8sProperties.put("zeppelin.k8s.interpreter.container.image", containerImage);
    k8sProperties.put("zeppelin.k8s.interpreter.group.id", getInterpreterGroupId());
    k8sProperties.put("zeppelin.k8s.interpreter.group.name", interpreterGroupName);
    k8sProperties.put("zeppelin.k8s.interpreter.setting.name", getInterpreterSettingName());
    k8sProperties.put("zeppelin.k8s.interpreter.localRepo", getLocalRepoDir());
    k8sProperties.put("zeppelin.k8s.interpreter.rpc.portRange", getInterpreterPortRange());
    k8sProperties.put("zeppelin.k8s.server.rpc.service", intpEventServerHost);
    k8sProperties.put("zeppelin.k8s.server.rpc.portRange", intpEventServerPort);

    String serverNamespace = K8sUtils.getCurrentK8sNamespace();
    String interpreterNamespace = getInterpreterNamespace();
    //Set the owner reference (zeppelin-server pod) for garbage collection when zeppelin server and the zeppelin interpreter is in the same namespace (Kubernetes cannot specify an owner in different namespace).
    if (ownerUID() != null && ownerName() != null && StringUtils.equals(serverNamespace, interpreterNamespace)) {
      k8sProperties.put("zeppelin.k8s.server.uid", ownerUID());
      k8sProperties.put("zeppelin.k8s.server.pod.name", ownerName());
    }

    Map<String, String> k8sEnv = new HashMap<>(getEnv());
    // environment variables
    k8sEnv.put(ENV_SERVICE_DOMAIN, getEnv().getOrDefault(ENV_SERVICE_DOMAIN, System.getenv(ENV_SERVICE_DOMAIN) == null ? "local.zeppelin-project.org" : System.getenv(ENV_SERVICE_DOMAIN)));
    k8sEnv.put(ENV_ZEPPELIN_HOME, getEnv().getOrDefault(ENV_ZEPPELIN_HOME, System.getenv(ENV_ZEPPELIN_HOME)));

    if (isSpark()) {
      int webUiPort = 4040;
      k8sProperties.put("zeppelin.k8s.spark.container.image", sparkImage);
      if (isSparkOnKubernetes(properties)) {
        k8sEnv.put("SPARK_SUBMIT_OPTIONS",
            getEnv().getOrDefault("SPARK_SUBMIT_OPTIONS", "") + buildSparkSubmitOptions(userName));
      }
      k8sEnv.put("SPARK_HOME", getEnv().getOrDefault("SPARK_HOME", "/spark"));

      // configure interpreter property "zeppelin.spark.uiWebUrl" if not defined, to enable spark ui through reverse proxy
      String webUrl = (String) properties.get("zeppelin.spark.uiWebUrl");
      if (StringUtils.isBlank(webUrl)) {
        webUrl = "//{{PORT}}-{{SERVICE_NAME}}.{{SERVICE_DOMAIN}}";
      }
      properties.put("zeppelin.spark.uiWebUrl",
          sparkUiWebUrlFromTemplate(
              webUrl,
              webUiPort,
              getPodName(),
              k8sEnv.get(ENV_SERVICE_DOMAIN)
          ));

      // configure interpreter property "zeppelin.k8s.spark.ingress.host" if not defined, to enable spark ui through ingress
      String ingressHost = (String) properties.get("zeppelin.k8s.spark.ingress.host");
      if (StringUtils.isBlank(ingressHost)) {
        ingressHost = "{{PORT}}-{{SERVICE_NAME}}.{{SERVICE_DOMAIN}}";
      }
      properties.put("zeppelin.k8s.spark.ingress.host",
          sparkUiWebUrlFromTemplate(
              ingressHost,
              webUiPort,
              getPodName(),
              k8sEnv.get(ENV_SERVICE_DOMAIN)
          ));

      // Resources of Interpreter Pod
      if (properties.containsKey(SPARK_DRIVER_MEMORY)) {
        String memory;
        if (properties.containsKey(SPARK_DRIVER_MEMORY_OVERHEAD)) {
          memory = K8sUtils.calculateSparkMemory(properties.getProperty(SPARK_DRIVER_MEMORY),
                                                 properties.getProperty(SPARK_DRIVER_MEMORY_OVERHEAD));
        } else {
          memory = K8sUtils.calculateMemoryWithDefaultOverhead(properties.getProperty(SPARK_DRIVER_MEMORY));
        }
        k8sProperties.put("zeppelin.k8s.interpreter.memory", memory);
      }
      if (properties.containsKey(SPARK_DRIVER_CORES)) {
        k8sProperties.put("zeppelin.k8s.interpreter.cores", properties.getProperty(SPARK_DRIVER_CORES));
      }
    }

    k8sProperties.put("zeppelin.k8s.envs", k8sEnv);

    // interpreter properties overrides the values
    k8sProperties.putAll(Maps.fromProperties(properties));
    return k8sProperties;
  }

  @VisibleForTesting
  String sparkUiWebUrlFromTemplate(String templateString, int port, String serviceName, String serviceDomain) {
    ImmutableMap<String, Object> binding = ImmutableMap.of(
        "PORT", port,
        "SERVICE_NAME", serviceName,
        ENV_SERVICE_DOMAIN, serviceDomain
    );

    ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
      Jinjava jinja = new Jinjava();
      return jinja.render(templateString, binding);
    } finally {
      Thread.currentThread().setContextClassLoader(oldCl);
    }
  }

  @VisibleForTesting
  boolean isSpark() {
    return "spark".equalsIgnoreCase(interpreterGroupName);
  }

  boolean isSparkOnKubernetes(Properties interpreterProperties) {
    String propertySparkMaster = (String) interpreterProperties.getOrDefault("spark.master", "");
    return propertySparkMaster.startsWith("k8s://");
  }

  @VisibleForTesting
  String buildSparkSubmitOptions(String userName) {
    StringBuilder options = new StringBuilder();

    options.append(" --master k8s://https://kubernetes.default.svc");
    options.append(" --deploy-mode client");
    if (properties.containsKey(SPARK_DRIVER_MEMORY)) {
      options.append(" --driver-memory ").append(properties.get(SPARK_DRIVER_MEMORY));
    }
    if (isUserImpersonated() && !StringUtils.containsIgnoreCase(userName, "anonymous")) {
      options.append(" --proxy-user ").append(userName);
    }
    options.append(" --conf spark.kubernetes.namespace=").append(getInterpreterNamespace());
    options.append(" --conf spark.executor.instances=1");
    options.append(" --conf spark.kubernetes.driver.pod.name=").append(getPodName());
    String sparkContainerImage = properties.containsKey(SPARK_CONTAINER_IMAGE) ? properties.getProperty(SPARK_CONTAINER_IMAGE) : sparkImage;
    options.append(" --conf spark.kubernetes.container.image=").append(sparkContainerImage);
    options.append(" --conf spark.driver.bindAddress=0.0.0.0");
    options.append(" --conf spark.driver.host=").append(getInterpreterPodDnsName());
    options.append(" --conf spark.driver.port=").append(getSparkDriverPort());
    options.append(" --conf spark.blockManager.port=").append(getSparkBlockManagerPort());

    return options.toString();
  }

  private String getInterpreterPodDnsName() {
    return String.format("%s.%s.svc",
        getPodName(), // service name and pod name is the same
        getInterpreterNamespace());
  }

  /**
   * See xxx-interpreter-pod.yaml
   * @return SparkDriverPort
   */
  @VisibleForTesting
  int getSparkDriverPort() {
    return 22321;
  }

  /**
   * See xxx-interpreter-pod.yaml
   * @return Spark block manager port
   */
  @VisibleForTesting
  int getSparkBlockManagerPort() {
    return 22322;
  }


  /**
   * Get UID of owner (zeppelin-server pod) for garbage collection
   * https://kubernetes.io/docs/concepts/workloads/controllers/garbage-collection/
   */
  private String ownerUID() {
    return System.getenv("POD_UID");
  }

  private String ownerName() {
    return System.getenv("POD_NAME");
  }

  @Override
  public void processStarted(int port, String host) {
    if (portForward) {
      LOGGER.info("Starting Port Forwarding");
      try {
        int localForwardedPodPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
        localPortForward = client.pods().inNamespace(interpreterNamespace).withName(podName)
            .portForward(K8S_INTERPRETER_SERVICE_PORT, localForwardedPodPort);
        super.processStarted(localForwardedPodPort, "localhost");
      } catch (IOException e) {
        LOGGER.error("Unable to create a PortForward", e);
      }
    } else {
      super.processStarted(port, getInterpreterPodDnsName());
    }
    LOGGER.info("Interpreter pod created {}:{}", getHost(), getPort());
    synchronized (started) {
      started.set(true);
      started.notifyAll();
    }
  }

  @Override
  public String getErrorMessage() {
    return String.format("%s%n current PodPhase: %s", super.getErrorMessage(), getPodPhase());
  }
}
