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
    kubectl.setNamespace(getNamespace());
  }


  /**
   * Check if i'm running inside of kubernetes or not.
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
   * Get current namespace
   * @throws IOException
   */
  String getNamespace() throws IOException {
    if (isRunningOnKubernetes()) {
      return readFile("/var/run/secrets/kubernetes.io/serviceaccount/namespace", Charset.defaultCharset()).trim();
    } else {
      return "default";
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
   * get Zeppelin server host dns.
   * return <hostname>.<namespace>.svc.cluster.local
   * @throws IOException
   */
  private String getZeppelinServiceHost() throws IOException {
    if (isRunningOnKubernetes()) {
      return String.format("%s.%s.svc.cluster.local",
              getHostname(), // service name and pod name should be the same
              getNamespace());
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
            String.format("%s_SERVICE_PORT_RPC", getHostname().replaceAll("[-.]", "_").toUpperCase()));
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
            context.getInterpreterGroupId(),
            context.getInterpreterSettingGroup(),
            context.getInterpreterSettingName(),
            properties,
            buildEnvFromProperties(context),
            getZeppelinServiceHost(),
            getZeppelinServiceRpcPort(),
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

  String readFile(String path, Charset encoding) throws IOException {
    byte[] encoded = Files.readAllBytes(Paths.get(path));
    return new String(encoded, encoding);
  }
}
