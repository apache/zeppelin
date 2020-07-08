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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

import org.junit.Rule;
import org.junit.Test;

import io.fabric8.kubernetes.client.server.mock.KubernetesServer;

public class K8sRemoteInterpreterProcessTest {

  @Rule
  public KubernetesServer server = new KubernetesServer(true, true);

  @Test
  public void testGetHostPort() {
    // given
    Properties properties = new Properties();
    HashMap<String, String> envs = new HashMap<String, String>();

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
        false);

    // then
    assertEquals(String.format("%s.%s.svc", intp.getPodName(), "default"), intp.getHost());
    assertEquals(12321, intp.getPort());
  }

  @Test
  public void testPredefinedPortNumbers() {
    // given
    Properties properties = new Properties();
    HashMap<String, String> envs = new HashMap<String, String>();

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
        false);


    // following values are hardcoded in k8s/interpreter/100-interpreter.yaml.
    // when change those values, update the yaml file as well.
    assertEquals(12321, intp.getPort());
    assertEquals(22321, intp.getSparkDriverPort());
    assertEquals(22322, intp.getSparkBlockmanagerPort());
  }

  @Test
  public void testGetTemplateBindings() throws IOException {
    // given
    Properties properties = new Properties();
    properties.put("my.key1", "v1");
    HashMap<String, String> envs = new HashMap<String, String>();
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
        false);

    // when
    Properties p = intp.getTemplateBindings(null);

    // then
    assertEquals("default", p.get("zeppelin.k8s.namespace"));
    assertEquals(intp.getPodName(), p.get("zeppelin.k8s.interpreter.pod.name"));
    assertEquals("sh", p.get("zeppelin.k8s.interpreter.container.name"));
    assertEquals("interpreter-container:1.0", p.get("zeppelin.k8s.interpreter.container.image"));
    assertEquals("shared_process", p.get("zeppelin.k8s.interpreter.group.id"));
    assertEquals("sh", p.get("zeppelin.k8s.interpreter.group.name"));
    assertEquals("shell", p.get("zeppelin.k8s.interpreter.setting.name"));
    assertEquals(true , p.containsKey("zeppelin.k8s.interpreter.localRepo"));
    assertEquals("12321:12321" , p.get("zeppelin.k8s.interpreter.rpc.portRange"));
    assertEquals("zeppelin.server.service" , p.get("zeppelin.k8s.server.rpc.service"));
    assertEquals(12320 , p.get("zeppelin.k8s.server.rpc.portRange"));
    assertEquals("v1", p.get("my.key1"));
    assertEquals("V1", envs.get("MY_ENV1"));

    envs = (HashMap<String, String>) p.get("zeppelin.k8s.envs");
    assertTrue(envs.containsKey("SERVICE_DOMAIN"));
    assertTrue(envs.containsKey("ZEPPELIN_HOME"));
  }

  @Test
  public void testGetTemplateBindingsForSpark() throws IOException {
    // given
    Properties properties = new Properties();
    properties.put("my.key1", "v1");
    properties.put("spark.master", "k8s://http://api");
    HashMap<String, String> envs = new HashMap<String, String>();
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
    assertTrue(sparkSubmitOptions.contains("spark.driver.host=" + intp.getHost()));
    assertTrue(sparkSubmitOptions.contains("spark.driver.port=" + intp.getSparkDriverPort()));
    assertTrue(sparkSubmitOptions.contains("spark.blockManager.port=" + intp.getSparkBlockmanagerPort()));
    assertFalse(sparkSubmitOptions.contains("--proxy-user"));
    assertTrue(intp.isSpark());
  }

  @Test
  public void testGetTemplateBindingsForSparkWithProxyUser() throws IOException {
    // given
    Properties properties = new Properties();
    properties.put("my.key1", "v1");
    properties.put("spark.master", "k8s://http://api");
    HashMap<String, String> envs = new HashMap<String, String>();
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
        true);

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
    assertTrue(sparkSubmitOptions.contains("spark.driver.host=" + intp.getHost()));
    assertTrue(sparkSubmitOptions.contains("spark.driver.port=" + intp.getSparkDriverPort()));
    assertTrue(sparkSubmitOptions.contains("spark.blockManager.port=" + intp.getSparkBlockmanagerPort()));
    assertTrue(sparkSubmitOptions.contains("--proxy-user mytestUser"));
    assertTrue(intp.isSpark());
  }

  @Test
  public void testGetTemplateBindingsForSparkWithProxyUserAnonymous() throws IOException {
    // given
    Properties properties = new Properties();
    properties.put("my.key1", "v1");
    properties.put("spark.master", "k8s://http://api");
    HashMap<String, String> envs = new HashMap<String, String>();
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
        true);

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
    HashMap<String, String> envs = new HashMap<String, String>();
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
    HashMap<String, String> envs = new HashMap<String, String>();
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
    HashMap<String, String> envs = new HashMap<String, String>();
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
        false);

    // when
    Properties p = intp.getTemplateBindings(null);

    // then
    assertEquals("5", p.get("zeppelin.k8s.interpreter.cores"));
    assertEquals("1280Mi", p.get("zeppelin.k8s.interpreter.memory"));
  }

}
