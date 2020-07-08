package org.apache.zeppelin.interpreter.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
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

public class K8sRemoteInterpreterProcess extends RemoteInterpreterProcess {
  private static final Logger LOGGER = LoggerFactory.getLogger(K8sRemoteInterpreterProcess.class);
  private static final int K8S_INTERPRETER_SERVICE_PORT = 12321;
  private final KubernetesClient client;
  private final String namespace;
  private final String interpreterGroupId;
  private final String interpreterGroupName;
  private final String interpreterSettingName;
  private final File specTempaltes;
  private final String containerImage;
  private final Properties properties;
  private final Map<String, String> envs;

  private final String podName;
  private final boolean portForward;
  private final String sparkImage;
  private LocalPortForward localPortForward;
  private int podPort = K8S_INTERPRETER_SERVICE_PORT;

  private final boolean isUserImpersonatedForSpark;

  private AtomicBoolean started = new AtomicBoolean(false);
  private Random rand = new Random();

  private static final String SPARK_DRIVER_MEMROY = "spark.driver.memory";
  private static final String SPARK_DRIVER_MEMROY_OVERHEAD = "spark.driver.memoryOverhead";
  private static final String SPARK_DRIVER_CORES = "spark.driver.cores";
  private static final String ENV_SERVICE_DOMAIN = "SERVICE_DOMAIN";

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
          boolean isUserImpersonatedForSpark
  ) {
    super(connectTimeout, intpEventServerHost, intpEventServerPort);
    this.client = client;
    this.namespace = namespace;
    this.specTempaltes = specTemplates;
    this.containerImage = containerImage;
    this.interpreterGroupId = interpreterGroupId;
    this.interpreterGroupName = interpreterGroupName;
    this.interpreterSettingName = interpreterSettingName;
    this.properties = properties;
    this.envs = new HashMap<>(envs);
    this.portForward = portForward;
    this.sparkImage = sparkImage;
    this.podName = interpreterGroupName.toLowerCase() + "-" + getRandomString(6);
    this.isUserImpersonatedForSpark = isUserImpersonatedForSpark;
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
  public String getInterpreterGroupId() {
    return interpreterGroupId;
  }

  @Override
  public String getInterpreterSettingName() {
    return interpreterSettingName;
  }

  @Override
  public void start(String userName) throws IOException {

    Properties templateProperties = getTemplateBindings(userName);
    // create new pod
    apply(specTempaltes, false, templateProperties);

    if (portForward) {
      podPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
      localPortForward = client.pods().inNamespace(namespace).withName(podName).portForward(K8S_INTERPRETER_SERVICE_PORT, podPort);
    }

    long startTime = System.currentTimeMillis();
    long timeoutTime = startTime + getConnectTimeout();

    // wait until interpreter send started message through thrift rpc
    synchronized (started) {
      while (!started.get()) {
        long timetoTimeout = timeoutTime - System.currentTimeMillis();
        if (timetoTimeout <= 0) {
          stop();
          throw new IOException("Launching zeppelin interpreter on kubernetes is time out, kill it now");
        }
        try {
          started.wait(timetoTimeout);
        } catch (InterruptedException e) {
          LOGGER.error("Interrupt received. Try to stop the interpreter and interrupt the current thread.", e);
          stop();
          Thread.currentThread().interrupt();
        }
      }
    }

    // waits for interpreter thrift rpc server ready
    while (!RemoteInterpreterUtils.checkIfRemoteEndpointAccessible(getHost(), getPort())) {
      if (System.currentTimeMillis() - timeoutTime > 0) {
        stop();
        throw new IOException("Launching zeppelin interpreter on kubernetes is time out, kill it now");
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        LOGGER.error("Interrupt received. Try to stop the interpreter and interrupt the current thread.", e);
        stop();
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public void stop() {
    Properties templateProperties = getTemplateBindings(null);
    // delete pod
    try {
      apply(specTempaltes, true, templateProperties);
    } catch (IOException e) {
      LOGGER.info("Error on removing interpreter pod", e);
    }
    if (portForward) {
        try {
            localPortForward.close();
        } catch (IOException e) {
            LOGGER.info("Error on closing portforwarder", e);
        }
    }
    // Shutdown connection
    shutdown();
  }

  @Override
  public String getHost() {
    if (portForward) {
      return "localhost";
    } else {
      return getInterpreterPodDnsName();
    }
  }

  @Override
  public int getPort() {
    return podPort;
  }

  @Override
  public boolean isRunning() {
    try {
      if (RemoteInterpreterUtils.checkIfRemoteEndpointAccessible(getHost(), getPort())) {
        return true;
      }
      Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
      if (pod != null) {
        PodStatus status = pod.getStatus();
        if (status != null) {
          return "Running".equals(status.getPhase()) && started.get();
        }
      }
    } catch (Exception e) {
      LOGGER.error("Can't get pod status", e);
    }
    return false;
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
    k8sProperties.put("zeppelin.k8s.interpreter.group.id", interpreterGroupId);
    k8sProperties.put("zeppelin.k8s.interpreter.group.name", interpreterGroupName);
    k8sProperties.put("zeppelin.k8s.interpreter.setting.name", interpreterSettingName);
    k8sProperties.put("zeppelin.k8s.interpreter.localRepo", "/tmp/local-repo");
    k8sProperties.put("zeppelin.k8s.interpreter.rpc.portRange", String.format("%d:%d", getPort(), getPort()));
    k8sProperties.put("zeppelin.k8s.server.rpc.service", intpEventServerHost);
    k8sProperties.put("zeppelin.k8s.server.rpc.portRange", intpEventServerPort);
    if (ownerUID() != null && ownerName() != null) {
      k8sProperties.put("zeppelin.k8s.server.uid", ownerUID());
      k8sProperties.put("zeppelin.k8s.server.pod.name", ownerName());
    }

    // environment variables
    envs.put(ENV_SERVICE_DOMAIN, envs.getOrDefault(ENV_SERVICE_DOMAIN, System.getenv(ENV_SERVICE_DOMAIN)));
    envs.put("ZEPPELIN_HOME", envs.getOrDefault("ZEPPELIN_HOME", "/zeppelin"));

    if (isSpark()) {
      int webUiPort = 4040;
      k8sProperties.put("zeppelin.k8s.spark.container.image", sparkImage);
      if (isSparkOnKubernetes(properties)) {
        envs.put("SPARK_SUBMIT_OPTIONS", envs.getOrDefault("SPARK_SUBMIT_OPTIONS", "") + buildSparkSubmitOptions(userName));
      }
      envs.put("SPARK_HOME", envs.getOrDefault("SPARK_HOME", "/spark"));

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
              envs.get(ENV_SERVICE_DOMAIN)
          ));
      // Resources of Interpreter Pod
      if (properties.containsKey(SPARK_DRIVER_MEMROY)) {
        String memory;
        if (properties.containsKey(SPARK_DRIVER_MEMROY_OVERHEAD)) {
          memory = K8sUtils.calculateSparkMemory(properties.getProperty(SPARK_DRIVER_MEMROY),
                                                 properties.getProperty(SPARK_DRIVER_MEMROY_OVERHEAD));
        } else {
          memory = K8sUtils.calculateMemoryWithDefaultOverhead(properties.getProperty(SPARK_DRIVER_MEMROY));
        }
        k8sProperties.put("zeppelin.k8s.interpreter.memory", memory);
      }
      if (properties.containsKey(SPARK_DRIVER_CORES)) {
        k8sProperties.put("zeppelin.k8s.interpreter.cores", properties.getProperty(SPARK_DRIVER_CORES));
      }
    }

    k8sProperties.put("zeppelin.k8s.envs", envs);

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
    if (properties.containsKey(SPARK_DRIVER_MEMROY)) {
      options.append(" --driver-memory " + properties.get(SPARK_DRIVER_MEMROY));
    }
    if (isUserImpersonatedForSpark && !StringUtils.containsIgnoreCase(userName, "anonymous") && isSpark()) {
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

  private String getRandomString(int length) {
    char[] chars = "abcdefghijklmnopqrstuvwxyz".toCharArray();

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      char c = chars[rand.nextInt(chars.length)];
      sb.append(c);
    }
    return sb.toString();
  }

  @Override
  public void processStarted(int port, String host) {
    LOGGER.info("Interpreter pod created {}:{}", host, port);
    synchronized (started) {
      started.set(true);
      started.notifyAll();
    }
  }

  @Override
  public String getErrorMessage() {
    return null;
  }
}
