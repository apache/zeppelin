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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest({System.class, DockerInterpreterProcess.class})
@PowerMockIgnore( {"javax.management.*"})
public class DockerInterpreterProcessTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(DockerInterpreterProcessTest.class);

  protected static ZeppelinConfiguration zconf = ZeppelinConfiguration.create();

  @Test
  public void testCreateIntpProcess() throws IOException {
    DockerInterpreterLauncher launcher
        = new DockerInterpreterLauncher(zconf, null);
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

    assertEquals(interpreterProcess.CONTAINER_SPARK_HOME, "/spark");
    assertEquals(interpreterProcess.uploadLocalLibToContainter, true);
    assertNotEquals(interpreterProcess.DOCKER_HOST, "http://my-docker-host:2375");
  }

  @Test
  public void testEnv() throws IOException {
    PowerMockito.mockStatic(System.class);
    PowerMockito.when(System.getenv("CONTAINER_SPARK_HOME")).thenReturn("my-spark-home");
    PowerMockito.when(System.getenv("UPLOAD_LOCAL_LIB_TO_CONTAINTER")).thenReturn("false");
    PowerMockito.when(System.getenv("DOCKER_HOST")).thenReturn("http://my-docker-host:2375");

    Properties properties = new Properties();
    properties.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "5000");

    HashMap<String, String> envs = new HashMap<String, String>();
    envs.put("MY_ENV1", "V1");

    DockerInterpreterProcess intp = new DockerInterpreterProcess(
        zconf,
        "interpreter-container:1.0",
        "shared_process",
        "sh",
        "shell",
        properties,
        envs,
        "zeppelin.server.hostname",
        12320,
        5000);

    assertEquals(intp.CONTAINER_SPARK_HOME, "my-spark-home");
    assertEquals(intp.uploadLocalLibToContainter, false);
    assertEquals(intp.DOCKER_HOST, "http://my-docker-host:2375");
  }

  @Test
  public void testTemplateBindings() throws IOException {
    Properties properties = new Properties();
    properties.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "5000");

    HashMap<String, String> envs = new HashMap<String, String>();
    envs.put("MY_ENV1", "V1");

    DockerInterpreterProcess intp = new DockerInterpreterProcess(
        zconf,
        "interpreter-container:1.0",
        "shared_process",
        "sh",
        "shell",
        properties,
        envs,
        "zeppelin.server.hostname",
        12320,
        5000);

    Properties dockerProperties = intp.getTemplateBindings();
    assertEquals(dockerProperties.size(), 10);

    assertTrue(null != dockerProperties.get("CONTAINER_ZEPPELIN_HOME"));
    assertTrue(null != dockerProperties.get("zeppelin.interpreter.container.image"));
    assertTrue(null != dockerProperties.get("zeppelin.interpreter.group.id"));
    assertTrue(null != dockerProperties.get("zeppelin.interpreter.group.name"));
    assertTrue(null != dockerProperties.get("zeppelin.interpreter.setting.name"));
    assertTrue(null != dockerProperties.get("zeppelin.interpreter.localRepo"));
    assertTrue(null != dockerProperties.get("zeppelin.interpreter.rpc.portRange"));
    assertTrue(null != dockerProperties.get("zeppelin.server.rpc.host"));
    assertTrue(null != dockerProperties.get("zeppelin.server.rpc.portRange"));
    assertTrue(null != dockerProperties.get("zeppelin.interpreter.connect.timeout"));

    List<String> listEnvs = intp.getListEnvs();
    assertEquals(listEnvs.size(), 6);
    Map<String, String> mapEnv = new HashMap<>();
    for (int i = 0; i < listEnvs.size(); i++) {
      String env = listEnvs.get(i);
      String kv[] = env.split("=");
      mapEnv.put(kv[0], kv[1]);
    }
    assertEquals(mapEnv.size(), 6);
    assertTrue(mapEnv.containsKey("ZEPPELIN_HOME"));
    assertTrue(mapEnv.containsKey("ZEPPELIN_CONF_DIR"));
    assertTrue(mapEnv.containsKey("ZEPPELIN_FORCE_STOP"));
    assertTrue(mapEnv.containsKey("SPARK_HOME"));
    assertTrue(mapEnv.containsKey("MY_ENV1"));
  }
}
