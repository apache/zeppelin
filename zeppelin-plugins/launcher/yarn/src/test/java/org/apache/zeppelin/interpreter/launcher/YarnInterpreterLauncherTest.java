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
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YarnInterpreterLauncherTest {

  @Test
  void passesCallbackCredentialToYarnLauncherEnvironment() {
    YarnInterpreterLauncher launcher = new YarnInterpreterLauncher(
        ZeppelinConfiguration.load(), null);
    InterpreterLaunchContext context = new InterpreterLaunchContext(
        new Properties(), new InterpreterOption(), null, "user", "group-id",
        "setting-id", "group", "name", 0, "host");
    context.setIntpEventCallbackToken("callback-token");

    Map<String, String> environment = launcher.buildEnvFromProperties(context);

    assertEquals("callback-token",
        environment.get(RemoteInterpreterEventClient.CALLBACK_TOKEN_ENV));
  }
}
