package org.apache.zeppelin.interpreter.launcher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.apache.zeppelin.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class K8sRemoteInterpreterProcess extends RemoteInterpreterProcess {
  private static final Logger LOGGER = LoggerFactory.getLogger(K8sStandardInterpreterLauncher.class);
  private static final int K8S_INTERPRETER_SERVICE_PORT = 12321;
  private static final int K8S_INTERPRETER_RESTAPISERVER_PORT = 8090;
  private final Kubectl kubectl;
  private final String interpreterGroupId;
  private final String interpreterGroupName;
  private final String interpreterSettingName;
  private final File specTempaltes;
  private final String containerImage;
  private final Properties properties;
  private final Map<String, String> envs;
  private final String zeppelinServiceHost;
  private final String zeppelinServiceRpcPort;

  private final Gson gson = new Gson();
  private final String podName;
  private final boolean portForward;
  private final String sparkImage;
  private ExecuteWatchdog portForwardWatchdog;
  private int podPort = K8S_INTERPRETER_SERVICE_PORT;

  private AtomicBoolean started = new AtomicBoolean(false);

  public K8sRemoteInterpreterProcess(
          Kubectl kubectl,
          File specTemplates,
          String containerImage,
          String interpreterGroupId,
          String interpreterGroupName,
          String interpreterSettingName,
          Properties properties,
          Map<String, String> envs,
          String zeppelinServiceHost,
          String zeppelinServiceRpcPort,
          boolean portForward,
          String sparkImage,
          int connectTimeout
  ) {
    super(connectTimeout);
    this.kubectl = kubectl;
    this.specTempaltes = specTemplates;
    this.containerImage = containerImage;
    this.interpreterGroupId = interpreterGroupId;
    this.interpreterGroupName = interpreterGroupName;
    this.interpreterSettingName = interpreterSettingName;
    this.properties = properties;
    this.envs = new HashMap(envs);
    this.zeppelinServiceHost = zeppelinServiceHost;
    this.zeppelinServiceRpcPort = zeppelinServiceRpcPort;
    this.portForward = portForward;
    this.sparkImage = sparkImage;
    this.podName = interpreterGroupName.toLowerCase() + "-" + Util.getRandomString(6);
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
  public String getInterpreterSettingName() {
    return interpreterSettingName;
  }

  @Override
  public void start(String userName) throws IOException {
    // create new pod
    kubectl.apply(specTempaltes, getTemplateBindings(), false);
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
      LOGGER.info(
          String.format("Interpreter pod creation is time out in %d seconds",
              getConnectTimeout()/1000));
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
      kubectl.apply(specTempaltes, getTemplateBindings(), true);
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

  @VisibleForTesting
  Properties getTemplateBindings() throws IOException {
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
    k8sProperties.put("zeppelin.k8s.server.rpc.host", zeppelinServiceHost);
    k8sProperties.put("zeppelin.k8s.server.rpc.portRange", zeppelinServiceRpcPort);
    if (ownerUID() != null && ownerName() != null) {
      k8sProperties.put("zeppelin.k8s.server.uid", ownerUID());
      k8sProperties.put("zeppelin.k8s.server.pod.name", ownerName());
    }

    // environment variables
    envs.put("SERVICE_DOMAIN", envs.getOrDefault("SERVICE_DOMAIN", System.getenv("SERVICE_DOMAIN")));
    envs.put("SERVICE_NAME", envs.getOrDefault("SERVICE_NAME", System.getenv("SERVICE_NAME")));
    envs.put(ZeppelinConfiguration.ConfVars.ZEPPELIN_HOME.name(),
            envs.getOrDefault("ZEPPELIN_HOME", "/zeppelin"));
    envs.put(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_RESTAPI_PORT.name(),
            String.valueOf(K8S_INTERPRETER_RESTAPISERVER_PORT));

    // pass all env variables starts with ZEPPELIN_INTERPRETER
    for (String envName : System.getenv().keySet()) {
      if (envName.startsWith("ZEPPELIN_INTERPRETER")) {
        envs.put(envName, System.getenv(envName));
      }
    }

    if (isSpark()) {
      int webUiPort = 4040;
      k8sProperties.put("zeppelin.k8s.spark.container.image", sparkImage);
      if (isSparkOnKubernetes(properties)) {
        envs.put("SPARK_SUBMIT_OPTIONS", envs.getOrDefault("SPARK_SUBMIT_OPTIONS", "") + buildSparkSubmitOptions());
      }
      envs.put("SPARK_HOME", envs.getOrDefault("SPARK_HOME", "/spark"));

      // configure interpreter property "zeppelin.spark.uiWebUrl" if not defined, to enable spark ui through reverse proxy
      String webUrl = (String) properties.get("zeppelin.spark.uiWebUrl");
      if (webUrl == null || webUrl.trim().isEmpty()) {
        properties.put("zeppelin.spark.uiWebUrl",
            String.format("//%d-%s.%s", webUiPort, getPodName(), envs.get("SERVICE_DOMAIN")));
      }
    }

    k8sProperties.put("zeppelin.k8s.envs", envs);

    // interpreter properties overrides the values
    k8sProperties.putAll(Maps.fromProperties(properties));
    return k8sProperties;
  }

  @VisibleForTesting
  boolean isSpark() {
    return "spark".equalsIgnoreCase(interpreterGroupName);
  }

  boolean isSparkOnKubernetes(Properties interpreteProperties) {
    String propertySparkMaster = (String) interpreteProperties.getOrDefault("master", "");
    if (propertySparkMaster.startsWith("k8s://")) {
      return true;
    } else {
      return false;
    }
  }

  @VisibleForTesting
  String buildSparkSubmitOptions() {
    StringBuilder options = new StringBuilder();

    options.append(" --master k8s://https://kubernetes.default.svc");
    options.append(" --deploy-mode client");
    if (properties.containsKey("spark.driver.memory")) {
      options.append(" --driver-memory " + properties.get("spark.driver.memory"));
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
    return String.format("%s.%s.svc.cluster.local",
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
