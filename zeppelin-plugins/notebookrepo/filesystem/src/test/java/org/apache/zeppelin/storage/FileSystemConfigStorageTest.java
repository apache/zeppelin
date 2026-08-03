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

package org.apache.zeppelin.storage;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.healthcheck.HealthChecks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemConfigStorageTest {

  @TempDir
  Path configDir;

  @AfterEach
  void removeHealthCheck() {
    HealthChecks.getHealthCheckLivenessRegistry().unregister(
        ConfigStorage.STORAGE_HEALTHCHECK_NAME);
  }

  @Test
  void persistsCredentialsAndRegistersAHealthyFileSystem() throws Exception {
    ZeppelinConfiguration zConf = ZeppelinConfiguration.load();
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONFIG_FS_DIR.getVarName(),
        configDir.toString());
    zConf.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_CONFIG_STORAGE_CLASS.getVarName(),
        FileSystemConfigStorage.class.getName());

    FileSystemConfigStorage storage;
    Thread thread = Thread.currentThread();
    ClassLoader previousClassLoader = thread.getContextClassLoader();
    try (URLClassLoader emptyClassLoader = new URLClassLoader(new URL[0], null)) {
      thread.setContextClassLoader(emptyClassLoader);
      storage = new FileSystemConfigStorage(zConf);
      storage.saveCredentials("secret");
    } finally {
      thread.setContextClassLoader(previousClassLoader);
    }

    assertEquals("secret", storage.loadCredentials());
    assertTrue(HealthChecks.getHealthCheckLivenessRegistry()
        .runHealthCheck(ConfigStorage.STORAGE_HEALTHCHECK_NAME).isHealthy());
  }
}
