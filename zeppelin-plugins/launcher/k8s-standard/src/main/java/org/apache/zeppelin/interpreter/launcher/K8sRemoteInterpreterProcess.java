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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterManagedProcess;
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
import io.fabric8.kubernetes.client.dsl.ParameterNamespaceListVisitFromServerGetDeleteRecreateWaitApplicable;

public class K8sRemoteInterpreterProcess extends RemoteInterpreterManagedProcess {
  private static final Logger LOGGER = LoggerFactory.getLogger(K8sRemoteInterpreterProcess.class);
  private static final int K8S_INTERPRETER_SERVICE_PORT = 12321;
  private final KubernetesClient client;
  private final String namespace;
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

  private AtomicBoolean started = new AtomicBoolean(false);

  private static final String SPARK_DRIVER_MEMORY = "spark.driver.memory";
  private static final String SPARK_DRIVER_MEMORY_OVERHEAD = "spark.driver.memoryOverhead";
  private static final String SPARK_DRIVER_CORES = "spark.driver.cores";
  private static final String ENV_SERVICE_DOMAIN = "SERVICE_DOMAIN";
  private static final String ENV_ZEPPELIN_HOME = "ZEPPELIN_HOME";

  public K8sRemoteInterpreterProcess(
          KubernetesClient client,
          String namespace,
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
    this.namespace = namespace;
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
   * Get interpreter pod name
   * @return
   */
  public String getPodName() {
    return podName;
  }

  /**
   * Get namespace
   * @return
   */
  public String getNamespace() {
    return namespace;
  }

  @Override
  public void start(String userName) throws IOException {

    Properties templateProperties = getTemplateBindings(userName);
    // create new pod
    apply(specTemplates, false, templateProperties);

    // special handling if we doesn't want timeout the process during lifecycle phase pending
    if (!timeoutDuringPending) {
      while (!StringUtils.equalsAnyIgnoreCase(getPodPhase(), "Succeeded", "Failed", "Running")
          && !Thread.currentThread().isInterrupted()) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          LOGGER.error("Interrupt received during pending phase. Try to stop the interpreter and interrupt the current thread.", e);
          processStopped("Start process was interrupted during the pending phase");
          stop();
          Thread.currentThread().interrupt();
        }
      }
    }

    long startTime = System.currentTimeMillis();
    long timeoutTime = startTime + getConnectTimeout();

    // wait until interpreter send started message through thrift rpc
    synchronized (started) {
      while (!started.get() && !Thread.currentThread().isInterrupted()) {
        long timetoTimeout = timeoutTime - System.currentTimeMillis();
        if (timetoTimeout <= 0) {
          processStopped("The start process was aborted while waiting for the interpreter to start. PodPhase before stop: " + getPodPhase());
          stop();
          throw new IOException("Launching zeppelin interpreter on kubernetes is time out, kill it now");
        }
        try {
          started.wait(timetoTimeout);
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
        LOGGER.info("Error on closing portforwarder", e);
      }
    }
  }

  @Override
  public boolean isRunning() {
    return "Running".equalsIgnoreCase(getPodPhase()) && started.get();
  }

  public String getPodPhase() {
    try {
      Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
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
   * @param path
   */
  void apply(File path, boolean delete, Properties templateProperties) throws IOException {
    if (path.getName().startsWith(".") || path.isHidden() || path.getName().endsWith("~")) {
      LOGGER.info("Skip {}", path.getAbsolutePath());
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
      ParameterNamespaceListVisitFromServerGetDeleteRecreateWaitApplicable<HasMetadata, Boolean> k8sObjects = client.load(IOUtils.toInputStream(template, StandardCharsets.UTF_8));
      LOGGER.info("Apply {} with {} K8s Objects", path.getAbsolutePath(), k8sObjects.get().size());
      LOGGER.debug(template);
      if (delete) {
        k8sObjects.inNamespace(namespace).delete();
      } else {
        k8sObjects.inNamespace(namespace).createOrReplace();
      }
    } else {
      LOGGER.error("Can't apply {}", path.getAbsolutePath());
    }
  }

  @VisibleForTesting
  Properties getTemplateBindings(String userName) {
    Properties k8sProperties = new Properties();

    // k8s template properties
    k8sProperties.put("zeppelin.k8s.namespace", getNamespace());
    k8sProperties.put("zeppelin.k8s.interpreter.pod.name", getPodName());
    k8sProperties.put("zeppelin.k8s.interpreter.container.name", interpreterGroupName.toLowerCase());
    k8sProperties.put("zeppelin.k8s.interpreter.container.image", containerImage);
    k8sProperties.put("zeppelin.k8s.interpreter.group.id", getInterpreterGroupId());
    k8sProperties.put("zeppelin.k8s.interpreter.group.name", interpreterGroupName);
    k8sProperties.put("zeppelin.k8s.interpreter.setting.name", getInterpreterSettingName());
    k8sProperties.put("zeppelin.k8s.interpreter.localRepo", getLocalRepoDir());
    k8sProperties.put("zeppelin.k8s.interpreter.rpc.portRange", getInterpreterPortRange());
    k8sProperties.put("zeppelin.k8s.server.rpc.service", intpEventServerHost);
    k8sProperties.put("zeppelin.k8s.server.rpc.portRange", intpEventServerPort);
    if (ownerUID() != null && ownerName() != null) {
      k8sProperties.put("zeppelin.k8s.server.uid", ownerUID());
      k8sProperties.put("zeppelin.k8s.server.pod.name", ownerName());
    }
    Map<String, String> k8sEnv = new HashMap<>(getEnv());
    // environment variables
    k8sEnv.put(ENV_SERVICE_DOMAIN, getEnv().getOrDefault(ENV_SERVICE_DOMAIN, System.getenv(ENV_SERVICE_DOMAIN)));
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

  boolean isSparkOnKubernetes(Properties interpreteProperties) {
    String propertySparkMaster = (String) interpreteProperties.getOrDefault("spark.master", "");
    return propertySparkMaster.startsWith("k8s://");
  }

  @VisibleForTesting
  String buildSparkSubmitOptions(String userName) {
    StringBuilder options = new StringBuilder();

    options.append(" --master k8s://https://kubernetes.default.svc");
    options.append(" --deploy-mode client");
    if (properties.containsKey(SPARK_DRIVER_MEMORY)) {
      options.append(" --driver-memory " + properties.get(SPARK_DRIVER_MEMORY));
    }
    if (isUserImpersonated() && !StringUtils.containsIgnoreCase(userName, "anonymous")) {
      options.append(" --proxy-user " + userName);
    }
    options.append(" --conf spark.kubernetes.namespace=" + getNamespace());
    options.append(" --conf spark.executor.instances=1");
    options.append(" --conf spark.kubernetes.driver.pod.name=" + getPodName());
    options.append(" --conf spark.kubernetes.container.image=" + sparkImage);
    options.append(" --conf spark.driver.bindAddress=0.0.0.0");
    options.append(" --conf spark.driver.host=" + getInterpreterPodDnsName());
    options.append(" --conf spark.driver.port=" + String.format("%d", getSparkDriverPort()));
    options.append(" --conf spark.blockManager.port=" + String.format("%d", getSparkBlockmanagerPort()));

    return options.toString();
  }

  private String getInterpreterPodDnsName() {
    return String.format("%s.%s.svc",
        getPodName(), // service name and pod name is the same
        getNamespace());
  }

  /**
   * See xxx-interpreter-pod.yaml
   * @return
   */
  @VisibleForTesting
  int getSparkDriverPort() {
    return 22321;
  }

  /**
   * See xxx-interpreter-pod.yaml
   * @return
   */
  @VisibleForTesting
  int getSparkBlockmanagerPort() {
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
        int localforwardedPodPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
        localPortForward = client.pods().inNamespace(namespace).withName(podName)
            .portForward(K8S_INTERPRETER_SERVICE_PORT, localforwardedPodPort);
        super.processStarted(localforwardedPodPort, "localhost");
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
    return String.format("%s%ncurrent PodPhase: %s", super.getErrorMessage(), getPodPhase());
  }
}
