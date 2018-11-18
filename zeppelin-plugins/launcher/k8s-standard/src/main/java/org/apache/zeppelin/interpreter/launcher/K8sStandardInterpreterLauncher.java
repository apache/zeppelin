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

import com.google.common.collect.Maps;
import com.google.gson.JsonSyntaxException;
import com.hubspot.jinjava.Jinjava;
import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Yaml;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.InterpreterRunner;
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
  private static final String pretty = "true";
  private static Integer apiTimeoutSec = new Integer(120);
  private InterpreterLaunchContext context;

  Jinjava jinja = new Jinjava();

  static ApiClient client;
  static {
    try {
      client = Config.defaultClient();
      Configuration.setDefaultApiClient(client);
    } catch (IOException e) {
      LOGGER.error("Can't get kubernetes client configuration", e);
    }
  }



  public K8sStandardInterpreterLauncher(ZeppelinConfiguration zConf, RecoveryStorage recoveryStorage) {
    super(zConf, recoveryStorage);
  }

  /**
   * Apply spec file(s) in the path.
   * @param path
   */
  void apply(File path) throws IOException {
    if (path.getName().startsWith(".") || path.isHidden()) {
      LOGGER.info("Skip {}", path.getAbsolutePath());
    }

    if (path.isDirectory()) {
      File[] files = path.listFiles();
      Arrays.sort(files);
      for (File f : files) {
        apply(f);
      }
    } else if (path.isFile()) {
      LOGGER.info("Apply {}", path.getAbsolutePath());
      List<Object> yamls = Yaml.loadAll(
              jinja.render(
                      readFile(path.getAbsolutePath(), Charset.defaultCharset()),
                      getTemplateBindings()));
      if (yamls != null) {
        for (Object spec : yamls) {
          try {
            applySpec(spec);
          } catch (ApiException e) {
            LOGGER.error(e.getResponseBody());
            throw new IOException(e);
          }
        }
      }
    } else {
      LOGGER.error("Can't apply {}", path.getAbsolutePath());
    }
  }


  Map<String, Object> getTemplateBindings() throws IOException {
    HashMap<String, Object> var = new HashMap<String, Object>();
    var.put("NAMESPACE", getNamespace());
    var.put("POD_NAME", context.getInterpreterGroupId().toLowerCase());
    var.put("CONTAINER_NAME", context.getInterpreterSettingGroup().toLowerCase());
    var.put("CONTAINER_IMAGE", "apache/zeppelin:0.8.0");
    var.put("INTP_PORT", "12321");                                    // interpreter.sh -r
    var.put("INTP_NAME", context.getInterpreterSettingGroup());       // interpreter.sh -d
    var.put("CALLBACK_HOST", getZeppelinServiceHost());               // interpreter.sh -c
    var.put("CALLBACK_PORT", getZeppelinServiceRpcPort());            // interpreter.sh -p
    var.put("INTP_SETTING", context.getInterpreterSettingName());     // interpreter.sh -g
    var.put("INTP_REPO", "/tmp/local-repo");                          // interpreter.sh -l
    var.putAll(Maps.fromProperties(properties));          // interpreter properties override template variables
    return var;
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

  AsyncWatcher<Map<String, Object>> w = null;
  String name;

  // apply single spec
  AsyncWatcher applySpec(Object spec) throws ApiException, IOException {
    if (spec instanceof V1Pod) {
      V1Pod pod = (V1Pod) spec;
      CoreV1Api api = new CoreV1Api();
      String namespace = pod.getMetadata().getNamespace();
      name = pod.getMetadata().getName();

      AsyncWatcher<Map<String, Object>> w =
              new AsyncWatcher<Map<String, Object>>(
                      new WatchCall() {
                        @Override
                        public Call list(String resourceVersion) throws ApiException {
                          return api.listNamespacedPodCall(
                                  namespace,
                                  pretty,
                                  null,
                                  String.format("metadata.name=%s", name),
                                  Boolean.TRUE,
                                  null,
                                  10,
                                  resourceVersion,
                                  apiTimeoutSec,
                                  true,
                                  null,
                                  null);
                        }
                      },
                      new WatchStopCondition<Map<String, Object>>() {
                        @Override
                        public boolean shouldStop(Watch.Response<Map<String, Object>> item) {
                          // pod phase https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-phase
                          Object status = item.object.get("status");
                          if (status == null) {
                            return false;
                          }
                          System.out.println("Status = " + status);
                          switch (((Map<String, String>)status).get("phase")) {
                            case "Succeeded":
                            case "Failed":
                            case "Running":
                              return true;
                            default:
                              return false;
                          }
                        }
                      },
                      apiTimeoutSec);

      api.createNamespacedPod(namespace, pod, pretty);
    } else if (spec instanceof V1Service) {
      V1Service service = (V1Service) spec;
      CoreV1Api api = new CoreV1Api();
      String namespace = service.getMetadata().getNamespace();
      String name = service.getMetadata().getName();

      AsyncWatcher<Map<String, Object>> w =
              new AsyncWatcher<Map<String, Object>>(
                      new WatchCall() {
                        @Override
                        public Call list(String resourceVersion) throws ApiException {
                          return api.listNamespacedServiceCall(
                                  namespace,
                                  pretty,
                                  null,
                                  String.format("metadata.name=%s", name),
                                  Boolean.TRUE,
                                  null,
                                  10,
                                  resourceVersion,
                                  apiTimeoutSec,
                                  true,
                                  null,
                                  null);
                        }
                      },
                      new WatchStopCondition<Map<String, Object>>() {
                        @Override
                        public boolean shouldStop(Watch.Response<Map<String, Object>> item) {
                          return "ADDED".equals(item.type);
                        }
                      },
                      apiTimeoutSec);

      try {
        api.createNamespacedService(namespace, service, pretty);
      } catch (JsonSyntaxException e) {
        // the API return is sometimes not a json message. That cause exception although creation of service actually success.
        // So need to ignore JsonSyntaxException here.
      }
    } else if (spec instanceof V1ConfigMap) {
      V1ConfigMap configMap = (V1ConfigMap) spec;
      CoreV1Api api = new CoreV1Api();
      String namespace = configMap.getMetadata().getNamespace();
      String name = configMap.getMetadata().getName();

      AsyncWatcher<Map<String, Object>> w =
              new AsyncWatcher<Map<String, Object>>(
                      new WatchCall() {
                        @Override
                        public Call list(String resourceVersion) throws ApiException {
                          return api.listNamespacedConfigMapCall(
                                  namespace,
                                  pretty,
                                  null,
                                  String.format("metadata.name=%s", name),
                                  Boolean.TRUE,
                                  null,
                                  10,
                                  resourceVersion,
                                  apiTimeoutSec,
                                  true,
                                  null,
                                  null);
                        }
                      },
                      new WatchStopCondition<Map<String, Object>>() {
                        @Override
                        public boolean shouldStop(Watch.Response<Map<String, Object>> item) {
                          return "ADDED".equals(item.type);
                        }
                      },
                      apiTimeoutSec);

      try {
        api.createNamespacedConfigMap(namespace, configMap, pretty);
      } catch (JsonSyntaxException e) {
        // the API return is sometimes not a json message. That cause exception although creation of service actually success.
        // So need to ignore JsonSyntaxException here.
      }
    }

    if (w != null) {
      w.await();

      if (!w.isStopConditionMatched()) {
        throw new IOException("Timeout on applying  " + name);
      }

      return w;
    } else {
      throw new IOException("Unsupported spec " + spec.getClass().getName());
    }
  }


  @Override
  public InterpreterClient launch(InterpreterLaunchContext context) throws IOException {
    LOGGER.info("Launching Interpreter: " + context.getInterpreterSettingGroup());
    this.context = context;
    this.properties = context.getProperties();
    InterpreterOption option = context.getOption();
    InterpreterRunner runner = context.getRunner();
    String groupName = context.getInterpreterSettingGroup();
    String name = context.getInterpreterSettingName();
    int connectTimeout = getConnectTimeout();

    // create new pod
    apply(new File(zConf.getK8sTemplatesDir(), "interpreter"));
    return null;
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
