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

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.Properties;
import org.apache.commons.exec.*;
import org.apache.commons.io.IOUtils;

import java.io.*;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Kubectl {
  private final Logger LOGGER = LoggerFactory.getLogger(Kubectl.class);
  private final String kubectlCmd;
  private final Gson gson = new Gson();
  private final KubernetesClient client;
  private String namespace;

  public Kubectl(String kubectlCmd) {
    this.kubectlCmd = kubectlCmd;

    Config config = new ConfigBuilder().build();
    client = new DefaultKubernetesClient(config);
  }

  /**
   * Override namespace. Otherwise use namespace provided in schema
   * @param namespace
   */
  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getNamespace() {
    return namespace;
  }

  public String apply(String spec) throws IOException {
    return execAndGet(new String[]{"apply", "-f", "-"}, spec);
  }

  public String delete(String spec) throws IOException {
    return execAndGet(new String[]{"delete", "-f", "-"}, spec);
  }

  public String get(String resourceType, String resourceName) throws IOException {
    return execAndGet(new String[]{"get", resourceType, resourceName, "-o", "json"});
  }

  public String getByLabel(String resourceType, String labelExpr) throws IOException {
    return execAndGet(new String[]{"get", resourceType, "-l", labelExpr, "-o", "json"});
  }

  public void label(String resourceType, String resourceName, String key, String value) throws IOException {
    execAndGet(new String[]{"label", "--overwrite", resourceType, resourceName,
            String.format("%s=%s", key, value)}, "");
  }

  public String wait(String resource, String waitFor, int timeoutSec) throws IOException {
    try {
      return execAndGet(new String[]{
          "wait",
          resource,
          String.format("--for=%s", waitFor),
          String.format("--timeout=%ds", timeoutSec)});
    } catch (IOException e) {
      if ("delete".equals(waitFor) && e.getMessage().contains("NotFound")) {
        LOGGER.info("{} Not found. Maybe already deleted.", resource);
        return "";
      } else {
        throw e;
      }
    }
  }

  public ExecuteWatchdog portForward(String resource, String [] ports) throws IOException {
    DefaultExecutor executor = new DefaultExecutor();
    CommandLine cmd = new CommandLine(kubectlCmd);
    cmd.addArguments("port-forward");
    cmd.addArguments(resource);
    cmd.addArguments(ports);

    ExecuteWatchdog watchdog = new ExecuteWatchdog(-1);
    executor.setWatchdog(watchdog);

    executor.execute(cmd, new ExecuteResultHandler() {
      @Override
      public void onProcessComplete(int i) {
        LOGGER.info("Port-forward stopped");
      }

      @Override
      public void onProcessFailed(ExecuteException e) {
        LOGGER.debug("port-forward process exit", e);
      }
    });

    return watchdog;
  }

  String execAndGet(String [] args) throws IOException {
    return execAndGet(args, "");
  }

  @VisibleForTesting
  String execAndGet(String [] args, String stdin) throws IOException {
    InputStream ins = IOUtils.toInputStream(stdin);
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    ArrayList<String> argsToOverride = new ArrayList<>(Arrays.asList(args));

    // set namespace
    if (namespace != null) {
      argsToOverride.add("--namespace=" + namespace);
    }

    LOGGER.info("kubectl " + argsToOverride);
    LOGGER.debug(stdin);

    try {
      int exitCode = execute(
              argsToOverride.toArray(new String[0]),
              ins,
              stdout,
              stderr
      );

      if (exitCode == 0) {
        String output = new String(stdout.toByteArray());
        return output;
      } else {
        String output = new String(stderr.toByteArray());
        throw new IOException(String.format("non zero return code (%d). %s", exitCode, output));
      }
    } catch (Exception e) {
      String output = new String(stderr.toByteArray());
      throw new IOException(output, e);
    }
  }

  public int execute(String [] args, InputStream stdin, OutputStream stdout, OutputStream stderr) throws IOException {
    DefaultExecutor executor = new DefaultExecutor();
    CommandLine cmd = new CommandLine(kubectlCmd);
    cmd.addArguments(args);

    ExecuteWatchdog watchdog = new ExecuteWatchdog(60 * 1000);
    executor.setWatchdog(watchdog);

    PumpStreamHandler streamHandler = new PumpStreamHandler(stdout, stderr, stdin);
    executor.setStreamHandler(streamHandler);
    return executor.execute(cmd);
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
  public static boolean isRunningOnKubernetes() {
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
  public static String getNamespaceFromContainer() throws IOException {
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
  public static String getHostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return "localhost";
    }
  }


  static String readFile(String path, Charset encoding) throws IOException {
    byte[] encoded = Files.readAllBytes(Paths.get(path));
    return new String(encoded, encoding);
  }

  /**
   * Apply spec file(s) in the path.
   * @param path
   */
  public void apply(File path, Properties binding, boolean delete) throws IOException {
    if (path.getName().startsWith(".") || path.isHidden() || path.getName().endsWith("~")) {
      LOGGER.info("Skip " + path.getAbsolutePath());
    }

    if (path.isDirectory()) {
      File[] files = path.listFiles();
      Arrays.sort(files);
      if (delete) {
        ArrayUtils.reverse(files);
      }

      for (File f : files) {
        apply(f, binding, delete);
      }
    } else if (path.isFile()) {
      LOGGER.info("Apply " + path.getAbsolutePath());
      K8sSpecTemplate specTemplate = new K8sSpecTemplate();
      specTemplate.loadProperties(binding);

      String spec = specTemplate.render(path);
      if (delete) {
        delete(spec);
      } else {
        apply(spec);
      }
    } else {
      LOGGER.error("Can't apply " + path.getAbsolutePath());
    }
  }

  public void watchDeployments(Watcher<Deployment> watcher, String labelKey, String labelValue) {
    client.apps().deployments().inNamespace(getNamespace())
            .withLabel(labelKey, labelValue)
            .watch(watcher);
  }

  public void watchJobs(Watcher<Job> watcher, String labelKey, String labelValue) {
    client.batch().jobs().inNamespace(getNamespace())
            .withLabel(labelKey, labelValue)
            .watch(watcher);
  }
}
