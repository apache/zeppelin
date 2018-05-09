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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.remote.SparkK8SRemoteInterpreterManagedProcess;
import org.junit.Test;

import java.util.Properties;

public class SparkK8SInterpreterLauncherTest {

  @Test
  public void testLauncher() {
    ZeppelinConfiguration zConf = new ZeppelinConfiguration();
    SparkK8SInterpreterLauncher launcher = new SparkK8SInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("ENV_1", "VALUE_1");
    properties.setProperty("property_1", "value_1");
    InterpreterOption option = new InterpreterOption();
    InterpreterLaunchContext context = new InterpreterLaunchContext(properties, option, null,
      "user1", "settingsId", "settingsGroupId",
      "settingsGroupName", "settingsName");
    InterpreterClient client = launcher.launch(context);
    assertTrue( client instanceof SparkK8SRemoteInterpreterManagedProcess);
    SparkK8SRemoteInterpreterManagedProcess interpreterProcess =
      (SparkK8SRemoteInterpreterManagedProcess) client;
    assertEquals(".//interpreter/settingsGroupName", interpreterProcess.getInterpreterDir());
    assertEquals(".//local-repo/settingsGroupId", interpreterProcess.getLocalRepoDir());
    assertEquals(zConf.getInterpreterRemoteRunnerPath(), interpreterProcess.getInterpreterRunner());
    assertEquals(3, interpreterProcess.getEnv().size());
    assertEquals(Boolean.TRUE.toString(), interpreterProcess.getEnv().get("ZEPPELIN_SPARK_K8_CLUSTER"));
  }
}
