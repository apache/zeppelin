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

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class FlinkInterpreterLauncher extends StandardInterpreterLauncher {

  public FlinkInterpreterLauncher(ZeppelinConfiguration zConf, RecoveryStorage recoveryStorage) {
    super(zConf, recoveryStorage);
  }

  @Override
  public Map<String, String> buildEnvFromProperties(InterpreterLaunchContext context)
          throws IOException {
    Map<String, String> envs = super.buildEnvFromProperties(context);
    String flinkHome = context.getProperties().getProperty("FLINK_HOME", envs.get("FLINK_HOME"));
    if (StringUtils.isBlank(flinkHome)) {
      throw new IOException("FLINK_HOME is not specified");
    }
    File flinkHomeFile = new File(flinkHome);
    if (!flinkHomeFile.exists()) {
      throw new IOException(String.format("FLINK_HOME '%s' doesn't exist", flinkHome));
    }
    if (!flinkHomeFile.isDirectory()) {
      throw new IOException(String.format("FLINK_HOME '%s' is a file, but should be directory",
              flinkHome));
    }
    envs.put("FLINK_CONF_DIR", flinkHome + "/conf");
    envs.put("FLINK_LIB_DIR", flinkHome + "/lib");
    envs.put("FLINK_PLUGINS_DIR", flinkHome + "/plugins");
    return envs;
  }
}
