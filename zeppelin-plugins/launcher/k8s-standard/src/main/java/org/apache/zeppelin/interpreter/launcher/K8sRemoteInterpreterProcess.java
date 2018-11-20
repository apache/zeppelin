package org.apache.zeppelin.interpreter.launcher;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class K8sRemoteInterpreterProcess extends RemoteInterpreterProcess {
  private static final Logger logger = LoggerFactory.getLogger(K8sStandardInterpreterLauncher.class);
  private final Kubectl kubectl;
  private final String interpreterGroupId;
  private final String interpreterGroupName;
  private final String interpreterSettingName;
  private final File specTempaltes;
  private final Properties properties;
  private final Map<String, String> envs;
  private final String zeppelinServiceHost;
  private final String zeppelinServiceRpcPort;
  private final int podCreateTimeoutSec = 180;

  private final Gson gson = new Gson();

  public K8sRemoteInterpreterProcess(
          Kubectl kubectl,
          File specTemplates,
          String interpreterGroupId,
          String interpreterGroupName,
          String interpreterSettingName,
          Properties properties,
          Map<String, String> envs,
          String zeppelinServiceHost,
          String zeppelinServiceRpcPort,
          int connectTimeout) {
    super(connectTimeout);
    this.kubectl = kubectl;
    this.specTempaltes = specTemplates;
    this.interpreterGroupId = interpreterGroupId;
    this.interpreterGroupName = interpreterGroupName;
    this.interpreterSettingName = interpreterSettingName;
    this.properties = properties;
    this.envs = envs;
    this.zeppelinServiceHost = zeppelinServiceHost;
    this.zeppelinServiceRpcPort = zeppelinServiceRpcPort;
  }


  /**
   * Get interpreter pod name
   * @return
   */
  private String getPodName() {
    return interpreterGroupId.toLowerCase();
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
  }

  @Override
  public void stop() {
    // delete pod
    try {
      apply(specTempaltes, true);
      kubectl.wait(String.format("pod/%s", getPodName()), "delete", 60);
    } catch (IOException e) {
      logger.error("Error on removing interpreter pod", e);
    }
  }

  @Override
  public String getHost() {
    return String.format("%s.%s.svc.cluster.local",
            getPodName(), // service name and pod name is the same
            kubectl.getNamespace());
  }

  @Override
  public int getPort() {
    return 12321;
  }

  @Override
  public boolean isRunning() {
    try {
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

      return "Running".equals(status.get("phase"));
    } catch (IOException e) {
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
    var.put("CONTAINER_IMAGE", "apache/zeppelin:0.8.0");
    var.put("INTP_PORT", "12321");                                    // interpreter.sh -r
    var.put("INTP_ID", interpreterGroupId);                           // interpreter.sh -i
    var.put("INTP_NAME", interpreterGroupName);                       // interpreter.sh -d
    var.put("CALLBACK_HOST", zeppelinServiceHost);                    // interpreter.sh -c
    var.put("CALLBACK_PORT", zeppelinServiceRpcPort);                 // interpreter.sh -p
    var.put("INTP_SETTING", interpreterSettingName);                  // interpreter.sh -g
    var.put("INTP_REPO", "/tmp/local-repo");                          // interpreter.sh -l
    var.putAll(Maps.fromProperties(properties));          // interpreter properties override template variables
    return var;
  }
}
