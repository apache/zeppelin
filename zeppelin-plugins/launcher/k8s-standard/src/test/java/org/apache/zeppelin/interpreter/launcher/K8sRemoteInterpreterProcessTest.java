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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.zeppelin.interpreter.remote.RemoteInterpreterManagedProcess;
import org.junit.Rule;
import org.junit.Test;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;

public class K8sRemoteInterpreterProcessTest {

  @Rule
  public KubernetesServer server = new KubernetesServer(false, true);

  @Test
  public void testPredefinedPortNumbers() {
    // given
    Properties properties = new Properties();
    Map<String, String> envs = new HashMap<>();

    K8sRemoteInterpreterProcess intp = new K8sRemoteInterpreterProcess(
        server.getClient(),
        "default",
        new File(".skip"),
        "interpreter-container:1.0",
        "shared_process",
        "sh",
        "shell",
        properties,
        envs,
        "zeppelin.server.hostname",
        12320,
        false,
        "spark-container:1.0",
        10,
        10,
        false,
        false);


    // following values are hardcoded in k8s/interpreter/100-interpreter.yaml.
    // when change those values, update the yaml file as well.
    assertEquals("12321:12321", intp.getInterpreterPortRange());
    assertEquals(22321, intp.getSparkDriverPort());
    assertEquals(22322, intp.getSparkBlockManagerPort());
  }

  @Test
  public void testGetTemplateBindings() {
    // given
    Properties properties = new Properties();
    properties.put("my.key1", "v1");
    Map<String, String> envs = new HashMap<>();
    envs.put("MY_ENV1", "V1");

    K8sRemoteInterpreterProcess intp = new K8sRemoteInterpreterProcess(
        server.getClient(),
        "default",
        new File(".skip"),
        "interpreter-container:1.0",
        "shared_process",
        "sh",
        "shell",
        properties,
        envs,
        "zeppelin.server.service",
        12320,
        false,
        "spark-container:1.0",
        10,
        10,
        false,
        false);

    // when
    Properties p = intp.getTemplateBindings(null);

    // then
    assertEquals("default", p.get("zeppelin.k8s.interpreter.namespace"));
    assertEquals(intp.getPodName(), p.get("zeppelin.k8s.interpreter.pod.name"));
    assertEquals("sh", p.get("zeppelin.k8s.interpreter.container.name"));
    assertEquals("interpreter-container:1.0", p.get("zeppelin.k8s.interpreter.container.image"));
    assertEquals("shared_process", p.get("zeppelin.k8s.interpreter.group.id"));
    assertEquals("sh", p.get("zeppelin.k8s.interpreter.group.name"));
    assertEquals("shell", p.get("zeppelin.k8s.interpreter.setting.name"));
    assertTrue(p.containsKey("zeppelin.k8s.interpreter.localRepo"));
    assertEquals("12321:12321" , p.get("zeppelin.k8s.interpreter.rpc.portRange"));
    assertEquals("zeppelin.server.service" , p.get("zeppelin.k8s.server.rpc.service"));
    assertEquals(12320 , p.get("zeppelin.k8s.server.rpc.portRange"));
    assertEquals("null", p.get("zeppelin.k8s.interpreter.user"));
    assertEquals("v1", p.get("my.key1"));
    assertEquals("V1", envs.get("MY_ENV1"));

    envs = (HashMap<String, String>) p.get("zeppelin.k8s.envs");
    assertTrue(envs.containsKey("SERVICE_DOMAIN"));
    assertTrue(envs.containsKey("ZEPPELIN_HOME"));
  }

  @Test
  public void testGetTemplateBindingsForSpark() {
    // given
    Properties properties = new Properties();
    properties.put("my.key1", "v1");
    properties.put("spark.master", "k8s://http://api");
    Map<String, String> envs = new HashMap<>();
    envs.put("MY_ENV1", "V1");
    envs.put("SPARK_SUBMIT_OPTIONS", "my options");
    envs.put("SERVICE_DOMAIN", "mydomain");

    K8sRemoteInterpreterProcess intp = new K8sRemoteInterpreterProcess(
        server.getClient(),
        "default",
        new File(".skip"),
        "interpreter-container:1.0",
        "shared_process",
        "spark",
        "myspark",
        properties,
        envs,
        "zeppelin.server.service",
        12320,
        false,
        "spark-container:1.0",
        10,
        10,
        false,
        false);

    // when
    Properties p = intp.getTemplateBindings("mytestUser");

    // then
    assertEquals("spark-container:1.0", p.get("zeppelin.k8s.spark.container.image"));
    assertEquals(String.format("//4040-%s.%s", intp.getPodName(), "mydomain"), p.get("zeppelin.spark.uiWebUrl"));

    envs = (HashMap<String, String>) p.get("zeppelin.k8s.envs");
    assertTrue( envs.containsKey("SPARK_HOME"));

    String sparkSubmitOptions = envs.get("SPARK_SUBMIT_OPTIONS");
    assertTrue(sparkSubmitOptions.startsWith("my options "));
    assertTrue(sparkSubmitOptions.contains("spark.kubernetes.namespace=default"));
    assertTrue(sparkSubmitOptions.contains("spark.kubernetes.driver.pod.name=" + intp.getPodName()));
    assertTrue(sparkSubmitOptions.contains("spark.kubernetes.container.image=spark-container:1.0"));
    assertTrue(sparkSubmitOptions.contains("spark.driver.host=" + intp.getPodName() + ".default.svc"));
    assertTrue(sparkSubmitOptions.contains("spark.driver.port=" + intp.getSparkDriverPort()));
    assertTrue(sparkSubmitOptions.contains("spark.blockManager.port=" + intp.getSparkBlockManagerPort()));
    assertFalse(sparkSubmitOptions.contains("--proxy-user"));
    assertTrue(intp.isSpark());
  }

  @Test
  public void testGetTemplateBindingsForSparkWithProxyUser() {
    // given
    Properties properties = new Properties();
    properties.put("my.key1", "v1");
    properties.put("spark.master", "k8s://http://api");
    Map<String, String> envs = new HashMap<>();
    envs.put("MY_ENV1", "V1");
    envs.put("SPARK_SUBMIT_OPTIONS", "my options");
    envs.put("SERVICE_DOMAIN", "mydomain");

    K8sRemoteInterpreterProcess intp = new K8sRemoteInterpreterProcess(
        server.getClient(),
        "default",
        new File(".skip"),
        "interpreter-container:1.0",
        "shared_process",
        "spark",
        "myspark",
        properties,
        envs,
        "zeppelin.server.service",
        12320,
        false,
        "spark-container:1.0",
        10,
        10,
        true,
        false);

    // when
    Properties p = intp.getTemplateBindings("mytestUser");
    // then
    assertEquals("spark-container:1.0", p.get("zeppelin.k8s.spark.container.image"));
    assertEquals(String.format("//4040-%s.%s", intp.getPodName(), "mydomain"), p.get("zeppelin.spark.uiWebUrl"));
    assertEquals("mytestUser", p.get("zeppelin.k8s.interpreter.user"));

    envs = (HashMap<String, String>) p.get("zeppelin.k8s.envs");
    assertTrue( envs.containsKey("SPARK_HOME"));

    String sparkSubmitOptions = envs.get("SPARK_SUBMIT_OPTIONS");
    assertTrue(sparkSubmitOptions.startsWith("my options "));
    assertTrue(sparkSubmitOptions.contains("spark.kubernetes.namespace=default"));
    assertTrue(sparkSubmitOptions.contains("spark.kubernetes.driver.pod.name=" + intp.getPodName()));
    assertTrue(sparkSubmitOptions.contains("spark.kubernetes.container.image=spark-container:1.0"));
    assertTrue(sparkSubmitOptions.contains("spark.driver.host=" + intp.getPodName() + ".default.svc"));
    assertTrue(sparkSubmitOptions.contains("spark.driver.port=" + intp.getSparkDriverPort()));
    assertTrue(sparkSubmitOptions.contains("spark.blockManager.port=" + intp.getSparkBlockManagerPort()));
    assertTrue(sparkSubmitOptions.contains("--proxy-user mytestUser"));
    assertTrue(intp.isSpark());
  }

  @Test
  public void testGetTemplateBindingsForSparkWithProxyUserAnonymous() {
    // given
    Properties properties = new Properties();
    properties.put("my.key1", "v1");
    properties.put("spark.master", "k8s://http://api");
    Map<String, String> envs = new HashMap<>();
    envs.put("MY_ENV1", "V1");
    envs.put("SPARK_SUBMIT_OPTIONS", "my options");
    envs.put("SERVICE_DOMAIN", "mydomain");

    K8sRemoteInterpreterProcess intp = new K8sRemoteInterpreterProcess(
        server.getClient(),
        "default",
        new File(".skip"),
        "interpreter-container:1.0",
        "shared_process",
        "spark",
        "myspark",
        properties,
        envs,
        "zeppelin.server.service",
        12320,
        false,
        "spark-container:1.0",
        10,
        10,
        true,
        false);

    // when
    Properties p = intp.getTemplateBindings("anonymous");
    // then
    assertEquals("spark-container:1.0", p.get("zeppelin.k8s.spark.container.image"));
    assertEquals(String.format("//4040-%s.%s", intp.getPodName(), "mydomain"), p.get("zeppelin.spark.uiWebUrl"));

    envs = (HashMap<String, String>) p.get("zeppelin.k8s.envs");
    assertTrue( envs.containsKey("SPARK_HOME"));

    String sparkSubmitOptions = envs.get("SPARK_SUBMIT_OPTIONS");
    assertFalse(sparkSubmitOptions.contains("--proxy-user"));
    assertTrue(intp.isSpark());
  }

  @Test
  public void testSparkUiWebUrlTemplate() {
    // given
    Properties properties = new Properties();
    Map<String, String> envs = new HashMap<>();
    envs.put("SERVICE_DOMAIN", "mydomain");

    K8sRemoteInterpreterProcess intp = new K8sRemoteInterpreterProcess(
        server.getClient(),
        "default",
        new File(".skip"),
        "interpreter-container:1.0",
        "shared_process",
        "spark",
        "myspark",
        properties,
        envs,
        "zeppelin.server.service",
        12320,
        false,
        "spark-container:1.0",
        10,
        10,
        false,
        false);

    // when non template url
    assertEquals("static.url",
        intp.sparkUiWebUrlFromTemplate(
            "static.url",
            4040,
            "zeppelin-server",
            "my.domain.com"));

    // when template url
    assertEquals("//4040-zeppelin-server.my.domain.com",
        intp.sparkUiWebUrlFromTemplate(
            "//{{PORT}}-{{SERVICE_NAME}}.{{SERVICE_DOMAIN}}",
            4040,
            "zeppelin-server",
            "my.domain.com"));
  }

  @Test
  public void testSparkPodResources() {
    // given
    Properties properties = new Properties();
    properties.put("spark.driver.memory", "1g");
    properties.put("spark.driver.cores", "1");
    Map<String, String> envs = new HashMap<>();
    envs.put("SERVICE_DOMAIN", "mydomain");

    K8sRemoteInterpreterProcess intp = new K8sRemoteInterpreterProcess(
        server.getClient(),
        "default",
        new File(".skip"),
        "interpreter-container:1.0",
        "shared_process",
        "spark",
        "myspark",
        properties,
        envs,
        "zeppelin.server.service",
        12320,
        false,
        "spark-container:1.0",
        10,
        10,
        false,
        false);

    // when
    Properties p = intp.getTemplateBindings(null);

    // then
    assertEquals("1", p.get("zeppelin.k8s.interpreter.cores"));
    assertEquals("1408Mi", p.get("zeppelin.k8s.interpreter.memory"));
  }

  @Test
  public void testSparkPodResourcesMemoryOverhead() {
    // given
    Properties properties = new Properties();
    properties.put("spark.driver.memory", "1g");
    properties.put("spark.driver.memoryOverhead", "256m");
    properties.put("spark.driver.cores", "5");
    Map<String, String> envs = new HashMap<>();
    envs.put("SERVICE_DOMAIN", "mydomain");

    K8sRemoteInterpreterProcess intp = new K8sRemoteInterpreterProcess(
        server.getClient(),
        "default",
        new File(".skip"),
        "interpreter-container:1.0",
        "shared_process",
        "spark",
        "myspark",
        properties,
        envs,
        "zeppelin.server.service",
        12320,
        false,
        "spark-container:1.0",
        10,
        10,
        false,
        false);

    // when
    Properties p = intp.getTemplateBindings(null);

    // then
    assertEquals("5", p.get("zeppelin.k8s.interpreter.cores"));
    assertEquals("1280Mi", p.get("zeppelin.k8s.interpreter.memory"));
  }

  @Test
  public void testK8sStartSuccessful() throws IOException {
    // given
    Properties properties = new Properties();
    Map<String, String> envs = new HashMap<>();
    envs.put("SERVICE_DOMAIN", "mydomain");
    URL url = Thread.currentThread().getContextClassLoader()
        .getResource("k8s-specs/interpreter-spec.yaml");
    File file = new File(url.getPath());

    K8sRemoteInterpreterProcess intp = new K8sRemoteInterpreterProcess(
        server.getClient(),
        "default",
        file,
        "interpreter-container:1.0",
        "shared_process",
        "spark",
        "myspark",
        properties,
        envs,
        "zeppelin.server.service",
        12320,
        false,
        "spark-container:1.0",
        10000,
        10,
        false,
        true);
    ExecutorService service = Executors.newFixedThreadPool(1);
    service
        .submit(new PodStatusSimulator(server.getClient(), intp.getInterpreterNamespace(), intp.getPodName(), intp));
    intp.start("TestUser");
    // then
    assertEquals("Running", intp.getPodPhase());
  }

  @Test
  public void testK8sStartFailed() {
    // given
    Properties properties = new Properties();
    Map<String, String> envs = new HashMap<>();
    envs.put("SERVICE_DOMAIN", "mydomain");
    URL url = Thread.currentThread().getContextClassLoader()
        .getResource("k8s-specs/interpreter-spec.yaml");
    File file = new File(url.getPath());

    K8sRemoteInterpreterProcess intp = new K8sRemoteInterpreterProcess(
        server.getClient(),
        "default",
        file,
        "interpreter-container:1.0",
        "shared_process",
        "spark",
        "myspark",
        properties,
        envs,
        "zeppelin.server.service",
        12320,
        false,
        "spark-container:1.0",
        3000,
        10,
        false,
        true);
    PodStatusSimulator podStatusSimulator = new PodStatusSimulator(server.getClient(), intp.getInterpreterNamespace(), intp.getPodName(), intp);
    podStatusSimulator.setSecondPhase("Failed");
    podStatusSimulator.setSuccessfulStart(false);
    ExecutorService service = Executors.newFixedThreadPool(1);
    service
        .submit(podStatusSimulator);
    // should throw an IOException
    try {
      intp.start("TestUser");
      fail("We excepting an IOException");
    } catch (IOException e) {
      assertNotNull(e);
      // Check that the Pod is deleted
      assertNull(
          server.getClient().pods().inNamespace(intp.getInterpreterNamespace()).withName(intp.getPodName())
              .get());
    }
  }

  @Test
  public void testK8sStartTimeoutPending() throws InterruptedException {
    // given
    Properties properties = new Properties();
    Map<String, String> envs = new HashMap<>();
    envs.put("SERVICE_DOMAIN", "mydomain");
    URL url = Thread.currentThread().getContextClassLoader()
        .getResource("k8s-specs/interpreter-spec.yaml");
    File file = new File(url.getPath());

    K8sRemoteInterpreterProcess intp = new K8sRemoteInterpreterProcess(
        server.getClient(),
        "default",
        file,
        "interpreter-container:1.0",
        "shared_process",
        "spark",
        "myspark",
        properties,
        envs,
        "zeppelin.server.service",
        12320,
        false,
        "spark-container:1.0",
        3000,
        10,
        false,
        false);
    PodStatusSimulator podStatusSimulator = new PodStatusSimulator(server.getClient(), intp.getInterpreterNamespace(), intp.getPodName(), intp);
    podStatusSimulator.setFirstPhase("Pending");
    podStatusSimulator.setSecondPhase("Pending");
    podStatusSimulator.setSuccessfulStart(false);
    ExecutorService service = Executors.newFixedThreadPool(2);
    service
        .submit(podStatusSimulator);
    service.submit(() -> {
      try {
        intp.start("TestUser");
        fail("We interrupt, this line of code should not be executed.");
      } catch (IOException e) {
        fail("We interrupt, this line of code should not be executed.");
      }
    });
    // wait a little bit
    TimeUnit.SECONDS.sleep(5);
    service.shutdownNow();
    // wait for a shutdown
    service.awaitTermination(10, TimeUnit.SECONDS);
    // Check that the Pod is deleted
    assertNull(server.getClient().pods().inNamespace(intp.getInterpreterNamespace())
        .withName(intp.getPodName()).get());

  }

  class PodStatusSimulator implements Runnable {

    private final KubernetesClient client;
    private final String namespace;
    private final String podName;
    private final RemoteInterpreterManagedProcess process;

    private String firstPhase = "Pending";
    private String secondPhase = "Running";
    private boolean successfulStart = true;

    public PodStatusSimulator(
        KubernetesClient client,
        String namespace,
        String podName,
        RemoteInterpreterManagedProcess process) {
      this.client = client;
      this.namespace = namespace;
      this.podName = podName;
      this.process = process;
    }

    public void setFirstPhase(String phase) {
      this.firstPhase = phase;
    }
    public void setSecondPhase(String phase) {
      this.secondPhase = phase;
    }
    public void setSuccessfulStart(boolean successful) {
      this.successfulStart = successful;
    }

    @Override
    public void run() {
      try {
        Instant timeoutTime = Instant.now().plusSeconds(10);
        while (timeoutTime.isAfter(Instant.now())) {
          Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
          if (pod != null) {
            TimeUnit.SECONDS.sleep(1);
            // Update Pod to "pending" phase
            pod.setStatus(new PodStatus(null, null, null, null, null, null, null, firstPhase,
                null,
                null, null, null, null));
            client.pods().inNamespace(namespace).updateStatus(pod);
            // Update Pod to "Running" phase
            pod.setStatus(new PodStatus(null, null, null, null, null, null, null, secondPhase,
                null,
                null, null, null, null));
            client.pods().inNamespace(namespace).updateStatus(pod);
            TimeUnit.SECONDS.sleep(1);
            if (successfulStart) {
              process.processStarted(12320, "testing");
            }
            break;
          } else {
            TimeUnit.MILLISECONDS.sleep(100);
          }
        }
      } catch (InterruptedException e) {
        // Do nothing
      }
    }
  }
}
