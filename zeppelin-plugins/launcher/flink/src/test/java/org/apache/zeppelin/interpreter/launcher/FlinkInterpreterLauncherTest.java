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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlinkInterpreterLauncherTest {

  @Test
  void passesCallbackCredentialToApplicationModeMaster(@TempDir Path flinkHome) throws Exception {
    Properties properties = new Properties();
    properties.setProperty("FLINK_HOME", flinkHome.toString());
    properties.setProperty("flink.execution.mode", "kubernetes-application");
    properties.setProperty("flink.app.jar", "/tmp/flink-app.jar");
    InterpreterLaunchContext context = new InterpreterLaunchContext(
        properties, new InterpreterOption(), null, "user", "group-id",
        "setting-id", "flink", "flink", 0, "host");
    context.setIntpEventCallbackToken("callback-token");
    FlinkInterpreterLauncher launcher = new FlinkInterpreterLauncher(
        ZeppelinConfiguration.load(), null);

    Map<String, String> environment = launcher.buildEnvFromProperties(context);

    assertEquals("callback-token",
        environment.get(RemoteInterpreterEventClient.CALLBACK_TOKEN_ENV));
    assertTrue(environment.get("ZEPPELIN_FLINK_APPLICATION_MODE_CONF").contains(
        "containerized.master.env." + RemoteInterpreterEventClient.CALLBACK_TOKEN_ENV
            + "=callback-token"));
  }
}
