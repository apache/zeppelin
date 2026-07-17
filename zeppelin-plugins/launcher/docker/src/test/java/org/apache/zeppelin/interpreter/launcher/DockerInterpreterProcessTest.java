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

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.ContainerState;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DockerInterpreterProcessTest {

  protected static ZeppelinConfiguration zConf = spy(ZeppelinConfiguration.load());

  private DockerInterpreterProcess newProcess() {
    ZeppelinConfiguration conf = spy(ZeppelinConfiguration.load());
    Properties properties = new Properties();
    properties.setProperty(
        ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "5000");
    return new DockerInterpreterProcess(
        conf,
        "interpreter-container:1.0",
        "test_intp_group",
        "sh",
        "shell",
        properties,
        new HashMap<>(),
        "zeppelin.server.hostname",
        12320,
        5000, 10);
  }

  // Stubs docker.inspectContainer(...).state() and returns the ContainerState mock
  // so each test can set running/paused/oomKilled/exitCode as needed.
  private ContainerState stubContainerState(DockerClient mockDocker) throws Exception {
    ContainerInfo info = mock(ContainerInfo.class);
    ContainerState state = mock(ContainerState.class);
    when(mockDocker.inspectContainer(anyString())).thenReturn(info);
    when(info.state()).thenReturn(state);
    return state;
  }

  // #1: stop() must always close the DockerClient, even when killing the container
  // fails, so the underlying HTTP socket / file descriptors are never leaked.
  @Test
  void stop_alwaysClosesDockerClient_evenWhenKillContainerFails() throws Exception {
    DockerInterpreterProcess intp = newProcess();
    DockerClient mockDocker = mock(DockerClient.class);
    intp.docker = mockDocker;
    doThrow(new RuntimeException("unexpected")).when(mockDocker).killContainer(anyString());

    assertThrows(RuntimeException.class, intp::stop);

    verify(mockDocker, times(1)).close();
  }

  // #2: when start() fails after the container has been started, it must be rolled
  // back (kill + remove) instead of being left orphaned.
  @Test
  void start_removesContainer_whenContainerPreparationFails() throws Exception {
    DockerInterpreterProcess intp = spy(newProcess());
    DockerClient mockDocker = mock(DockerClient.class);
    doReturn(mockDocker).when(intp).createDockerClient(anyString());

    when(mockDocker.listContainers(any())).thenReturn(Collections.emptyList());
    when(mockDocker.createContainer(any(ContainerConfig.class), anyString()))
        .thenReturn(ContainerCreation.builder().id("test-container-id").build());
    // Container is created and started, then preparation (the first exec) fails.
    doThrow(new DockerException("exec failed"))
        .when(mockDocker).execCreate(anyString(), any(String[].class), any());

    assertThrows(IOException.class, () -> intp.start("user1"));

    verify(mockDocker).startContainer("test-container-id");
    verify(mockDocker).killContainer(anyString());
    verify(mockDocker).removeContainer(anyString());
  }

  // #3: isAlive() reflects the container's actual state from the Docker daemon,
  // not just whether the Thrift port is reachable.
  @Test
  void isAlive_trueWhenContainerRunning() throws Exception {
    DockerInterpreterProcess intp = newProcess();
    DockerClient mockDocker = mock(DockerClient.class);
    intp.docker = mockDocker;
    ContainerState state = stubContainerState(mockDocker);
    when(state.running()).thenReturn(true);

    assertTrue(intp.isAlive());
  }

  @Test
  void isAlive_falseWhenContainerExitedOrOomKilled() throws Exception {
    DockerInterpreterProcess intp = newProcess();
    DockerClient mockDocker = mock(DockerClient.class);
    intp.docker = mockDocker;
    ContainerState state = stubContainerState(mockDocker);
    when(state.running()).thenReturn(false);   // exited / OOMKilled -> running == false

    assertFalse(intp.isAlive());
  }

  // #3: getErrorMessage() surfaces the failure reason from the container state.
  @Test
  void getErrorMessage_reportsOomKilled() throws Exception {
    DockerInterpreterProcess intp = newProcess();
    DockerClient mockDocker = mock(DockerClient.class);
    intp.docker = mockDocker;
    ContainerState state = stubContainerState(mockDocker);
    when(state.oomKilled()).thenReturn(true);
    when(state.exitCode()).thenReturn(137L);

    assertTrue(intp.getErrorMessage().contains("OOMKilled"));
  }

  @Test
  void testCreateIntpProcess() throws IOException {
    DockerInterpreterLauncher launcher
        = new DockerInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "5000");
    InterpreterOption option = new InterpreterOption();
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null,
        "user1", "intpGroupId", "groupId",
        "groupName", "name", 0, "host");
    InterpreterClient client = launcher.launch(context);

    assertTrue(client instanceof DockerInterpreterProcess);
    DockerInterpreterProcess interpreterProcess = (DockerInterpreterProcess) client;
    assertEquals("name", interpreterProcess.getInterpreterSettingName());

    assertEquals("/opt/spark", interpreterProcess.containerSparkHome);
    assertTrue(interpreterProcess.uploadLocalLibToContainter);
    assertNotEquals("http://my-docker-host:2375", interpreterProcess.dockerHost);
  }

  @Test
  void testEnv() throws IOException {
    when(zConf.getString(ConfVars.ZEPPELIN_DOCKER_CONTAINER_SPARK_HOME))
        .thenReturn("my-spark-home");
    when(zConf.getBoolean(ConfVars.ZEPPELIN_DOCKER_UPLOAD_LOCAL_LIB_TO_CONTAINTER))
        .thenReturn(false);
    when(zConf.getString(ConfVars.ZEPPELIN_DOCKER_HOST))
        .thenReturn("http://my-docker-host:2375");

    Properties properties = new Properties();
    properties.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "5000");
    HashMap<String, String> envs = new HashMap<String, String>();
    envs.put("MY_ENV1", "V1");

    DockerInterpreterProcess intp = spy(new DockerInterpreterProcess(
        zConf,
        "interpreter-container:1.0",
        "shared_process",
        "sh",
        "shell",
        properties,
        envs,
        "zeppelin.server.hostname",
        12320,
        5000, 10));

    assertEquals("my-spark-home", intp.containerSparkHome);
    assertFalse(intp.uploadLocalLibToContainter);
    assertEquals("http://my-docker-host:2375", intp.dockerHost);
  }

  @Test
  void testTemplateBindings() throws IOException {
    Properties properties = new Properties();
    properties.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "5000");

    HashMap<String, String> envs = new HashMap<String, String>();
    envs.put("MY_ENV1", "V1");

    DockerInterpreterProcess intp = new DockerInterpreterProcess(
        zConf,
        "interpreter-container:1.0",
        "shared_process",
        "sh",
        "shell",
        properties,
        envs,
        "zeppelin.server.hostname",
        12320,
        5000, 10);

    Properties dockerProperties = intp.getTemplateBindings();
    assertEquals(10, dockerProperties.size());

    assertNotNull(dockerProperties.get("CONTAINER_ZEPPELIN_HOME"));
    assertNotNull(dockerProperties.get("zeppelin.interpreter.container.image"));
    assertNotNull(dockerProperties.get("zeppelin.interpreter.group.id"));
    assertNotNull(dockerProperties.get("zeppelin.interpreter.group.name"));
    assertNotNull(dockerProperties.get("zeppelin.interpreter.setting.name"));
    assertNotNull(dockerProperties.get("zeppelin.interpreter.localRepo"));
    assertNotNull(dockerProperties.get("zeppelin.interpreter.rpc.portRange"));
    assertNotNull(dockerProperties.get("zeppelin.server.rpc.host"));
    assertNotNull(dockerProperties.get("zeppelin.server.rpc.portRange"));
    assertNotNull(dockerProperties.get("zeppelin.interpreter.connect.timeout"));

    List<String> listEnvs = intp.getListEnvs();
    assertEquals(6, listEnvs.size());
    Map<String, String> mapEnv = new HashMap<>();
    for (int i = 0; i < listEnvs.size(); i++) {
      String env = listEnvs.get(i);
      String kv[] = env.split("=");
      mapEnv.put(kv[0], kv[1]);
    }
    assertEquals(6, mapEnv.size());
    assertTrue(mapEnv.containsKey("ZEPPELIN_HOME"));
    assertTrue(mapEnv.containsKey("ZEPPELIN_CONF_DIR"));
    assertTrue(mapEnv.containsKey("ZEPPELIN_FORCE_STOP"));
    assertTrue(mapEnv.containsKey("SPARK_HOME"));
    assertTrue(mapEnv.containsKey("MY_ENV1"));
  }
}
