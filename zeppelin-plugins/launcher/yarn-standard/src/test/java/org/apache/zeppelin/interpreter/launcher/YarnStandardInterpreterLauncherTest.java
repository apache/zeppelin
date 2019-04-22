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
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertTrue;

public class YarnStandardInterpreterLauncherTest {
  @Before
  public void setUp() {
    for (final ZeppelinConfiguration.ConfVars confVar : ZeppelinConfiguration.ConfVars.values()) {
      System.clearProperty(confVar.getVarName());
    }
  }

  @Test
  public void testYarnLauncher() throws IOException {
    ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
    zConf.setServerKerberosKeytab("keytab");
    zConf.setServerKerberosPrincipal("Principal");
    zConf.setYarnWebappAddress("http://127.0.0.1");

    YarnStandardInterpreterLauncher.setTest(true);
    YarnStandardInterpreterLauncher launcher = new YarnStandardInterpreterLauncher(zConf, null);
    Properties properties = new Properties();
    properties.setProperty("zeppelin.python.useIPython", "false");
    properties.setProperty("zeppelin.python.gatewayserver_address", "127.0.0.1");
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
    assertTrue(client instanceof YarnRemoteInterpreterProcess);
  }
}
