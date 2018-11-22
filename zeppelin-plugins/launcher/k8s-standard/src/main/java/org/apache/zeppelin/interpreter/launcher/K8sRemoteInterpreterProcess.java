package org.apache.zeppelin.interpreter.launcher;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.zeppelin.interpreter.InterpreterUtils;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class K8sRemoteInterpreterProcess extends RemoteInterpreterProcess {
  private static final Logger logger = LoggerFactory.getLogger(K8sStandardInterpreterLauncher.class);
  private static final int K8S_INTERPRETER_SERVICE_PORT = 12321;
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
  private final int podCreateTimeoutSec = 180;

  private final Gson gson = new Gson();
  private final String podName;
  private final boolean portForward;
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
          int connectTimeout) {
    super(connectTimeout);
    this.kubectl = kubectl;
    this.specTempaltes = specTemplates;
    this.containerImage = containerImage;
    this.interpreterGroupId = interpreterGroupId;
    this.interpreterGroupName = interpreterGroupName;
    this.interpreterSettingName = interpreterSettingName;
    this.properties = properties;
    this.envs = envs;
    this.zeppelinServiceHost = zeppelinServiceHost;
    this.zeppelinServiceRpcPort = zeppelinServiceRpcPort;
    this.portForward = portForward;
    this.podName = interpreterGroupName.toLowerCase() + "-" + getRandomString(6);
  }


  /**
   * Get interpreter pod name
   * @return
   */
  private String getPodName() {
    return podName;
  }

  @Override
  public String getInterpreterSettingName() {
    return interpreterSettingName;
  }

  @Override
  public void start(String userName) throws IOException {
    // create new pod
    apply(specTempaltes, false);
    kubectl.wait(String.format("pod/%s", getPodName()), "condition=Ready", podCreateTimeoutSec);

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
          logger.error("Remote interpreter is not accessible");
        }
      }
    }

    if (!started.get()) {
      logger.info(
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
      apply(specTempaltes, true);
    } catch (IOException e) {
      logger.info("Error on removing interpreter pod", e);
    }

    try {
      kubectl.wait(String.format("pod/%s", getPodName()), "delete", 60);
    } catch (IOException e) {
      logger.debug("Error on waiting pod delete", e);
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
      logger.error("Can't get pod status", e);
      return false;
    }
  }

  /**
   * Apply spec file(s) in the path.
   * @param path
   */
  void apply(File path, boolean delete) throws IOException {
    if (path.getName().startsWith(".") || path.isHidden() || path.getName().endsWith("~")) {
      logger.info("Skip " + path.getAbsolutePath());
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
      logger.info("Apply " + path.getAbsolutePath());
      K8sSpecTemplate specTemplate = new K8sSpecTemplate();
      specTemplate.putAll(getTemplateBindings());

      String spec = specTemplate.render(path);
      if (delete) {
        kubectl.delete(spec);
      } else {
        kubectl.apply(spec);
      }
    } else {
      logger.error("Can't apply " + path.getAbsolutePath());
    }
  }

  Map<String, Object> getTemplateBindings() throws IOException {
    HashMap<String, Object> var = new HashMap<String, Object>();
    var.put("NAMESPACE", kubectl.getNamespace());
    var.put("POD_NAME", getPodName());
    var.put("CONTAINER_NAME", interpreterGroupName.toLowerCase());
    var.put("CONTAINER_IMAGE", containerImage);
    var.put("INTP_PORT", "12321");                                    // interpreter.sh -r
    var.put("INTP_ID", interpreterGroupId);                           // interpreter.sh -i
    var.put("INTP_NAME", interpreterGroupName);                       // interpreter.sh -d
    var.put("CALLBACK_HOST", zeppelinServiceHost);                    // interpreter.sh -c
    var.put("CALLBACK_PORT", zeppelinServiceRpcPort);                 // interpreter.sh -p
    var.put("INTP_SETTING", interpreterSettingName);                  // interpreter.sh -g
    var.put("INTP_REPO", "/tmp/local-repo");                          // interpreter.sh -l
    var.put("OWNER_UID", ownerUID());
    var.put("OWNER_NAME", ownerName());

    if (isSpark()) {
      var.put("SPARK_IMAGE", "spark:2.4.0");
      var.put("SPARK_SUBMIT_OPTIONS", buildSparkSubmitOptions());
    }

    var.putAll(Maps.fromProperties(properties));          // interpreter properties override template variables
    return var;
  }

  private boolean isSpark() {
    return "spark".equalsIgnoreCase(interpreterGroupName);
  }

  private String buildSparkSubmitOptions() {
    StringBuilder options = new StringBuilder();

    options.append("--master k8s://https://kubernetes.default.svc");
    options.append(" --deploy-mode client");
    options.append(" --conf spark.executor.instances=1");
    options.append(" --conf spark.driver.pod.name=" + getPodName());
    options.append(" --conf spark.kubernetes.container.image=spark:2.4.0");
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
  private int getSparkDriverPort() {
    return 22321;
  }

  /**
   * See xxx-interpreter-pod.yaml
   * @return
   */
  private int getSparkBlockmanagerPort() {
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
    Random random = new Random();
    for (int i = 0; i < length; i++) {
      char c = chars[random.nextInt(chars.length)];
      sb.append(c);
    }
    String randomStr = sb.toString();

    return randomStr;
  }

  @Override
  public void processStarted(int port, String host) {
    logger.info("Interpreter pod created {}:{}", host, port);
    synchronized (started) {
      started.set(true);
      started.notify();
    }
  }
}
