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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Interpreter Launcher which use shell script to launch the interpreter process.
 */
public class K8sStandardInterpreterLauncher extends InterpreterLauncher {

  private static final Logger LOGGER = LoggerFactory.getLogger(K8sStandardInterpreterLauncher.class);
  private final Kubectl kubectl;
  private InterpreterLaunchContext context;


  public K8sStandardInterpreterLauncher(ZeppelinConfiguration zConf, RecoveryStorage recoveryStorage) throws IOException {
    super(zConf, recoveryStorage);
    kubectl = new Kubectl(zConf.getK8sKubectlCmd());
    kubectl.setNamespace(Kubectl.getNamespaceFromContainer());
  }

  @VisibleForTesting
  K8sStandardInterpreterLauncher(ZeppelinConfiguration zConf, RecoveryStorage recoveryStorage, Kubectl kubectl) {
    super(zConf, recoveryStorage);
    this.kubectl = kubectl;
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
    if (new File("/var/run/secrets/kubernetes.io").exists()) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * get Zeppelin server host dns.
   * return <hostname>.<namespace>.svc.cluster.local
   * @throws IOException
   */
  private String getZeppelinServiceHost() throws IOException {
    if (isRunningOnKubernetes()) {
      String serviceName = System.getenv("SERVICE_NAME");
      if (StringUtils.isEmpty(serviceName)) {
        // if SERVICE_NAME env is not defined, try pod host name as a service name.
        serviceName = Kubectl.getHostname();
      }

      return String.format("%s.%s.svc.cluster.local",
              serviceName,
              Kubectl.getNamespaceFromContainer());
    } else {
      return context.getZeppelinServerHost();
    }
  }

  /**
   * get Zeppelin server rpc port
   * Read env variable "<HOSTNAME>_SERVICE_PORT_RPC"
   */
  private String getZeppelinServiceRpcPort() {
    String envServicePort = System.getenv(
            String.format("%s_SERVICE_PORT_RPC", Kubectl.getHostname().replaceAll("[-.]", "_").toUpperCase()));
    if (envServicePort != null) {
      return envServicePort;
    } else {
      return Integer.toString(context.getZeppelinServerRPCPort());
    }
  }

  @Override
  public InterpreterClient launch(InterpreterLaunchContext context) throws IOException {
    LOGGER.info("Launching Interpreter: " + context.getInterpreterSettingGroup());
    this.context = context;
    this.properties = context.getProperties();
    int connectTimeout = getConnectTimeout();

    return new K8sRemoteInterpreterProcess(
            kubectl,
            new File(zConf.getK8sTemplatesDir(), "interpreter"),
            zConf.getK8sContainerImage(),
            context.getInterpreterGroupId(),
            context.getInterpreterSettingGroup(),
            context.getInterpreterSettingName(),
            properties,
            buildEnvFromProperties(context),
            getZeppelinServiceHost(),
            getZeppelinServiceRpcPort(),
            zConf.getK8sPortForward(),
            zConf.getK8sSparkContainerImage(),
            connectTimeout);
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
}
