package org.apache.zeppelin.interpreter.launcher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hubspot.jinjava.Jinjava;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class K8sRemoteInterpreterProcess extends RemoteInterpreterProcess {
  private static final Logger LOGGER = LoggerFactory.getLogger(K8sRemoteInterpreterProcess.class);
  private static final int K8S_INTERPRETER_SERVICE_PORT = 12321;
  private final Kubectl kubectl;
  private final String interpreterGroupId;
  private final String interpreterGroupName;
  private final String interpreterSettingName;
  private final File specTempaltes;
  private final String containerImage;
  private final Properties properties;
  private final Map<String, String> envs;

  private final Gson gson = new Gson();
  private final String podName;
  private final boolean portForward;
  private final String sparkImage;
  private ExecuteWatchdog portForwardWatchdog;
  private int podPort = K8S_INTERPRETER_SERVICE_PORT;

  private final boolean isUserImpersonatedForSpark;
  private String userName;

  private AtomicBoolean started = new AtomicBoolean(false);
  private Random rand = new Random();

  private static final String SPARK_DRIVER_MEMROY = "spark.driver.memory";
  private static final String SPARK_DRIVER_MEMROY_OVERHEAD = "spark.driver.memoryOverhead";
  private static final String SPARK_DRIVER_CORES = "spark.driver.cores";
  private static final String ENV_SERVICE_DOMAIN = "SERVICE_DOMAIN";

  public K8sRemoteInterpreterProcess(
          Kubectl kubectl,
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
    this.kubectl = kubectl;
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
  @VisibleForTesting
  String getPodName() {
    return podName;
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
    /**
     * If a spark interpreter process is running, userName is set in preparation for --proxy-user
     */
    if (isUserImpersonatedForSpark && !StringUtils.containsIgnoreCase(userName, "anonymous") && isSpark()) {
      this.userName = userName;
    } else {
      this.userName = null;
    }
    // create new pod
    apply(specTempaltes, false);
    kubectl.wait(String.format("pod/%s", getPodName()), "condition=Ready", getConnectTimeout()/1000);

    if (portForward) {
      podPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
      portForwardWatchdog = kubectl.portForward(
          String.format("pod/%s", getPodName()),
          new String[] {
              String.format("%s:%s", podPort, K8S_INTERPRETER_SERVICE_PORT)
          });
    }

    long startTime = System.currentTimeMillis();

    // wait until interpreter send started message through thrift rpc
    synchronized (started) {
      if (!started.get()) {
        try {
          started.wait(getConnectTimeout());
        } catch (InterruptedException e) {
          LOGGER.error("Remote interpreter is not accessible");
        }
      }
    }

    if (!started.get()) {
      LOGGER.info("Interpreter pod creation is time out in {} seconds", getConnectTimeout()/1000);
    }

    // waits for interpreter thrift rpc server ready
    while (System.currentTimeMillis() - startTime < getConnectTimeout()) {
      if (RemoteInterpreterUtils.checkIfRemoteEndpointAccessible(getHost(), getPort())) {
        break;
      } else {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
      }
    }
  }

  @Override
  public void stop() {
    // delete pod
    try {
      apply(specTempaltes, true);
    } catch (IOException e) {
      LOGGER.info("Error on removing interpreter pod", e);
    }

    try {
      kubectl.wait(String.format("pod/%s", getPodName()), "delete", 60);
    } catch (IOException e) {
      LOGGER.debug("Error on waiting pod delete", e);
    }


    if (portForwardWatchdog != null) {
      portForwardWatchdog.destroyProcess();
    }
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

      String ret = kubectl.execAndGet(new String[]{
              "get",
              String.format("pods/%s", getPodName()),
              "-o",
              "json"
      });

      if (ret == null) {
        return false;
      }

      Map<String, Object> pod = gson.fromJson(ret, new TypeToken<Map<String, Object>>() {}.getType());
      if (pod == null || !pod.containsKey("status")) {
        return false;
      }

      Map<String, Object> status = (Map<String, Object>) pod.get("status");
      if (status == null || !status.containsKey("phase")) {
        return false;
      }

      return "Running".equals(status.get("phase")) && started.get();
    } catch (Exception e) {
      LOGGER.error("Can't get pod status", e);
      return false;
    }
  }

  /**
   * Apply spec file(s) in the path.
   * @param path
   */
  void apply(File path, boolean delete) throws IOException {
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
        apply(f, delete);
      }
    } else if (path.isFile()) {
      LOGGER.info("Apply {}", path.getAbsolutePath());
      K8sSpecTemplate specTemplate = new K8sSpecTemplate();
      specTemplate.loadProperties(getTemplateBindings());

      String spec = specTemplate.render(path);
      if (delete) {
        kubectl.delete(spec);
      } else {
        kubectl.apply(spec);
      }
    } else {
      LOGGER.error("Can't apply {}", path.getAbsolutePath());
    }
  }

  @VisibleForTesting
  Properties getTemplateBindings() {
    Properties k8sProperties = new Properties();

    // k8s template properties
    k8sProperties.put("zeppelin.k8s.namespace", kubectl.getNamespace());
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
        envs.put("SPARK_SUBMIT_OPTIONS", envs.getOrDefault("SPARK_SUBMIT_OPTIONS", "") + buildSparkSubmitOptions());
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
    String propertySparkMaster = (String) interpreteProperties.getOrDefault("master", "");
    return propertySparkMaster.startsWith("k8s://");
  }

  @VisibleForTesting
  String buildSparkSubmitOptions() {
    StringBuilder options = new StringBuilder();

    options.append(" --master k8s://https://kubernetes.default.svc");
    options.append(" --deploy-mode client");
    if (properties.containsKey(SPARK_DRIVER_MEMROY)) {
      options.append(" --driver-memory " + properties.get(SPARK_DRIVER_MEMROY));
    }
    if (userName != null) {
      options.append(" --proxy-user " + userName);
    }
    options.append(" --conf spark.kubernetes.namespace=" + kubectl.getNamespace());
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
        kubectl.getNamespace());
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
      started.notify();
    }
  }

  @Override
  public String getErrorMessage() {
    return null;
  }
}
