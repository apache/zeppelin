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

import org.apache.zeppelin.plugin.ExtensionWithPluginManager;
import org.apache.zeppelin.plugin.IPluginManager;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Interpreter Launcher which use shell script to launch the interpreter process.
 */
@Extension
public class DockerInterpreterLauncher extends InterpreterLauncher implements ExtensionWithPluginManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(DockerInterpreterLauncher.class);

  private InterpreterLaunchContext context;
  private IPluginManager pluginManager;

  @Override
  public void setPluginManager(IPluginManager pluginManager) {
    this.pluginManager = pluginManager;
  }

  @Override
  public InterpreterClient launchDirectly(InterpreterLaunchContext context) throws IOException {
    LOGGER.info("Launching Interpreter: {}", context.getInterpreterSettingGroup());
    this.context = context;
    this.properties = context.getProperties();
    int connectTimeout = getConnectTimeout();
    if (connectTimeout < 200000) {
      // DockerInterpreterLauncher needs to pull the image and create the container,
      // it takes a long time, so the force is set to 200 seconds.
      LOGGER.warn("DockerInterpreterLauncher needs to pull the image and create the container, " +
          "it takes a long time, If the creation of the interpreter on docker fails, " +
          "please increase the value of `zeppelin.interpreter.connect.timeout` " +
          "in `zeppelin-site.xml`, recommend 200 seconds.");
    }

    StandardInterpreterLauncher interpreterLauncher = null;
    if (isSpark()) {
      interpreterLauncher = (SparkInterpreterLauncher) pluginManager.createInterpreterLauncher("SparkInterpreterLauncher", recoveryStorage);
    } else if (isFlink()) {
      interpreterLauncher = (FlinkInterpreterLauncher) pluginManager.createInterpreterLauncher("FlinkInterpreterLauncher", recoveryStorage);
    } else {
      interpreterLauncher = (StandardInterpreterLauncher) pluginManager.createInterpreterLauncher("StandardInterpreterLauncher", recoveryStorage);
    }
    interpreterLauncher.setProperties(context.getProperties());
    Map<String, String> env = interpreterLauncher.buildEnvFromProperties(context);

    return new DockerInterpreterProcess(
        zConf,
        zConf.getDockerContainerImage(),
        context.getInterpreterGroupId(),
        context.getInterpreterSettingGroup(),
        context.getInterpreterSettingName(),
        properties,
        env,
        context.getIntpEventServerHost(),
        context.getIntpEventServerPort(),
        connectTimeout, 10);
  }

  boolean isSpark() {
    return "spark".equalsIgnoreCase(context.getInterpreterSettingName());
  }

  boolean isFlink() {
    return "flink".equalsIgnoreCase(context.getInterpreterSettingName());
  }
}
