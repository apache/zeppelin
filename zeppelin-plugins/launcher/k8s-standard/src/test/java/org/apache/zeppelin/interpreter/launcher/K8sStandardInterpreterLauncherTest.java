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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Properties;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.junit.Before;
import org.junit.Test;

/**
 * In the future, test may use minikube on travis for end-to-end test
 * https://github.com/LiliC/travis-minikube
 * https://blog.travis-ci.com/2017-10-26-running-kubernetes-on-travis-ci-with-minikube
 */
public class K8sStandardInterpreterLauncherTest {
  @Before
  public void setUp() {
    for (final ZeppelinConfiguration.ConfVars confVar : ZeppelinConfiguration.ConfVars.values()) {
      System.clearProperty(confVar.getVarName());
    }
  }

  @Test
  public void testK8sLauncher() throws IOException {
    // given
    ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
    K8sStandardInterpreterLauncher launcher = new K8sStandardInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("ENV_1", "VALUE_1");
    properties.setProperty("property_1", "value_1");
    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    InterpreterLaunchContext context = new InterpreterLaunchContext(
            properties,
            option,
            null,
            "user1",
            "intpGroupId",
            "groupId",
            "sh",
            "name",
            0,
            "host");

    // when
    InterpreterClient client = launcher.launch(context);

    // then
    assertTrue(client instanceof K8sRemoteInterpreterProcess);
  }

  @Test
  public void testK8sLauncherWithSparkAndUserImpersonate() throws IOException {
    // given
    ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
    K8sStandardInterpreterLauncher launcher = new K8sStandardInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("ENV_1", "VALUE_1");
    properties.setProperty("property_1", "value_1");
    properties.setProperty("SERVICE_DOMAIN", "example.com");
    properties.setProperty("zeppelin.interpreter.connect.timeout", "60");
    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(true);
    InterpreterLaunchContext context = new InterpreterLaunchContext(
            properties,
            option,
            null,
            "user1", // username
            "spark-user1", //interpretergroupId
            "dummy", // interpreterSettingId
            "spark", // interpreterSettingGroup
            "spark", // interpreterSettingName
            0,
            "host");
    // when
    InterpreterClient client = launcher.launch(context);

    // then
    assertTrue(client instanceof K8sRemoteInterpreterProcess);
    K8sRemoteInterpreterProcess process = (K8sRemoteInterpreterProcess) client;
    assertTrue(process.isSpark());
    assertTrue(process.buildSparkSubmitOptions(context.getUserName()).contains("--proxy-user user1"));
  }

  @Test
  public void testK8sLauncherWithSparkAndWithoutUserImpersonate() throws IOException {
    // given
    ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
    K8sStandardInterpreterLauncher launcher = new K8sStandardInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("ENV_1", "VALUE_1");
    properties.setProperty("property_1", "value_1");
    properties.setProperty("SERVICE_DOMAIN", "example.com");
    properties.setProperty("zeppelin.interpreter.connect.timeout", "60");
    InterpreterOption option = new InterpreterOption();
    option.setUserImpersonate(false);
    InterpreterLaunchContext context = new InterpreterLaunchContext(
            properties,
            option,
            null,
            "user1", // username
            "spark-user1", //interpretergroupId
            "dummy", // interpreterSettingId
            "spark", // interpreterSettingGroup
            "spark", // interpreterSettingName
            0,
            "host");
    // when
    InterpreterClient client = launcher.launch(context);

    // then
    assertTrue(client instanceof K8sRemoteInterpreterProcess);
    K8sRemoteInterpreterProcess process = (K8sRemoteInterpreterProcess) client;
    assertTrue(process.isSpark());
    assertFalse(process.buildSparkSubmitOptions(context.getUserName()).contains("--proxy-user user1"));
  }
}
