/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Interpreter Launcher which use shell script to launch the interpreter process.
 */
public class K8sStandardInterpreterLauncher extends InterpreterLauncher {

  private static final Logger LOGGER = LoggerFactory.getLogger(K8sStandardInterpreterLauncher.class);
  private InterpreterLaunchContext context;
  private final KubernetesClient client;

  public K8sStandardInterpreterLauncher(ZeppelinConfiguration zConf, RecoveryStorage recoveryStorage) throws IOException {
    super(zConf, recoveryStorage);
    client = new DefaultKubernetesClient();
  }

  /**
   * Check if i'm running inside of kubernetes or not.
   * It should return truth regardless of ZeppelinConfiguration.getRunMode().
   *
   * Normally, unless Zeppelin is running on Kubernetes, K8sStandardInterpreterLauncher shouldn't even have initialized.
   * However, when ZeppelinConfiguration.getRunMode() is force 'k8s', InterpreterSetting.getLauncherPlugin() will try
   * to use K8sStandardInterpreterLauncher. This is useful for development. It allows Zeppelin server running on your
   * IDE and creates your interpreters in Kubernetes. So any code changes on Zeppelin server or kubernetes yaml spec
   * can be applied without re-building docker image.
   * @return
   */
  boolean isRunningOnKubernetes() {
    return new File("/var/run/secrets/kubernetes.io").exists();
  }

  /**
   * Get current namespace
   * @throws IOException
   */
  String getNamespace() throws IOException {
    if (isRunningOnKubernetes()) {
      return readFile(Config.KUBERNETES_NAMESPACE_PATH, Charset.defaultCharset()).trim();
    } else {
      return zConf.getK8sNamepsace();
    }
  }

  /**
   * Get hostname. It should be the same to Service name (and Pod name) of the Kubernetes
   * @return
   */
  String getHostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return "localhost";
    }
  }

  /**
   * get Zeppelin service.
   * return <service-name>.<namespace>.svc
   * @throws IOException
   */
  private String getZeppelinService() throws IOException {
    if (isRunningOnKubernetes()) {
      return String.format("%s.%s.svc",
              zConf.getK8sServiceName(),
              getNamespace());
    } else {
      return context.getIntpEventServerHost();
    }
  }

  /**
   * get Zeppelin server rpc port
   * Read env variable "<HOSTNAME>_SERVICE_PORT_RPC"
   */
  private int getZeppelinServiceRpcPort() {
    String envServicePort = System.getenv(
            String.format("%s_SERVICE_PORT_RPC", getHostname().replaceAll("[-.]", "_").toUpperCase()));
    if (envServicePort != null) {
      return Integer.parseInt(envServicePort);
    } else {
      return context.getIntpEventServerPort();
    }
  }

  /**
   * Interpreter Process will run in K8s. There is no point in changing the user after starting the container.
   * Switching to an other user (non-privileged) should be done during the image creation process.
   *
   * Only if a spark interpreter process is running, userImpersonatation should be possible for --proxy-user
   */
  private boolean isUserImpersonateForSparkInterpreter(InterpreterLaunchContext context) {
      return zConf.getZeppelinImpersonateSparkProxyUser() &&
          context.getOption().isUserImpersonate() &&
          "spark".equalsIgnoreCase(context.getInterpreterSettingGroup());
  }

  @Override
  public InterpreterClient launchDirectly(InterpreterLaunchContext context) throws IOException {
    LOGGER.info("Launching Interpreter: {}", context.getInterpreterSettingGroup());
    this.context = context;
    this.properties = context.getProperties();

    return new K8sRemoteInterpreterProcess(
            client,
            getNamespace(),
            new File(zConf.getK8sTemplatesDir(), "interpreter"),
            zConf.getK8sContainerImage(),
            context.getInterpreterGroupId(),
            context.getInterpreterSettingGroup(),
            context.getInterpreterSettingName(),
            properties,
            buildEnvFromProperties(context),
            getZeppelinService(),
            getZeppelinServiceRpcPort(),
            zConf.getK8sPortForward(),
            zConf.getK8sSparkContainerImage(),
            getConnectTimeout(),
            isUserImpersonateForSparkInterpreter(context));
  }

  protected Map<String, String> buildEnvFromProperties(InterpreterLaunchContext context) {
    Map<String, String> env = new HashMap<>();
    for (Object key : context.getProperties().keySet()) {
      if (RemoteInterpreterUtils.isEnvString((String) key)) {
        env.put((String) key, context.getProperties().getProperty((String) key));
      }
      // TODO(zjffdu) move this to FlinkInterpreterLauncher
      if (key.toString().equals("FLINK_HOME")) {
        String flinkHome = context.getProperties().get(key).toString();
        env.put("FLINK_CONF_DIR", flinkHome + "/conf");
        env.put("FLINK_LIB_DIR", flinkHome + "/lib");
      }
    }
    env.put("INTERPRETER_GROUP_ID", context.getInterpreterGroupId());
    return env;
  }

  String readFile(String path, Charset encoding) throws IOException {
    byte[] encoded = Files.readAllBytes(Paths.get(path));
    return new String(encoded, encoding);
  }
}
