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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Properties;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterManagedProcess;
import org.junit.Before;
import org.junit.Test;

public class StandardInterpreterLauncherTest {
  @Before
  public void setUp() {
    for (final ZeppelinConfiguration.ConfVars confVar : ZeppelinConfiguration.ConfVars.values()) {
      System.clearProperty(confVar.getVarName());
    }
  }

  @Test
  public void testLauncher() throws IOException {
    ZeppelinConfiguration zConf = new ZeppelinConfiguration();
    StandardInterpreterLauncher launcher = new StandardInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("ENV_1", "VALUE_1");
    properties.setProperty("property_1", "value_1");
    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    InterpreterLaunchContext context =
        new InterpreterLaunchContext(
            properties,
            option,
            null,
            "user1",
            "intpGroupId",
            "groupId",
            "groupName",
            "name",
            0,
            "host");
    InterpreterClient client = launcher.launch(context);
    assertTrue(client instanceof RemoteInterpreterManagedProcess);
    RemoteInterpreterManagedProcess interpreterProcess = (RemoteInterpreterManagedProcess) client;
    assertEquals("name", interpreterProcess.getInterpreterSettingName());
    assertEquals(".//interpreter/groupName", interpreterProcess.getInterpreterDir());
    assertEquals(".//local-repo/groupId", interpreterProcess.getLocalRepoDir());
    assertEquals(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getIntValue(),
        interpreterProcess.getConnectTimeout());
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertEquals(1, interpreterProcess.getEnv().size());
    assertEquals("VALUE_1", interpreterProcess.getEnv().get("ENV_1"));
    assertEquals(true, interpreterProcess.isUserImpersonated());
  }

  @Test
  public void testConnectTimeOut() throws IOException {
    ZeppelinConfiguration zConf = new ZeppelinConfiguration();
    StandardInterpreterLauncher launcher = new StandardInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty(
        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT.getVarName(), "10000");
    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    InterpreterLaunchContext context =
        new InterpreterLaunchContext(
            properties,
            option,
            null,
            "user1",
            "intpGroupId",
            "groupId",
            "groupName",
            "name",
            0,
            "host");
    InterpreterClient client = launcher.launch(context);
    assertTrue(client instanceof RemoteInterpreterManagedProcess);
    RemoteInterpreterManagedProcess interpreterProcess = (RemoteInterpreterManagedProcess) client;
    assertEquals("name", interpreterProcess.getInterpreterSettingName());
    assertEquals(".//interpreter/groupName", interpreterProcess.getInterpreterDir());
    assertEquals(".//local-repo/groupId", interpreterProcess.getLocalRepoDir());
    assertEquals(10000, interpreterProcess.getConnectTimeout());
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertEquals(0, interpreterProcess.getEnv().size());
    assertEquals(true, interpreterProcess.isUserImpersonated());
  }
}
