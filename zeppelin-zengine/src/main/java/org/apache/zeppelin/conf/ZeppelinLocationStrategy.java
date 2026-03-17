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
package org.apache.zeppelin.conf;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

import org.apache.commons.configuration2.io.FileLocationStrategy;
import org.apache.commons.configuration2.io.FileLocator;
import org.apache.commons.configuration2.io.FileSystem;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZeppelinLocationStrategy implements FileLocationStrategy {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinLocationStrategy.class);

  @Override
  public URL locate(FileSystem fileSystem, FileLocator locator) {
    try {
      Map<String, String> procEnv = EnvironmentUtils.getProcEnvironment();
      if (procEnv.containsKey("ZEPPELIN_HOME")) {
        String zconfDir = procEnv.get("ZEPPELIN_HOME");
        File file = new File(zconfDir + File.separator
            + "conf" + File.separator + locator.getFileName());
        if (file.isFile()) {
          LOGGER.info("Load configuration from {}", file);
          return file.toURI().toURL();
        }
      }

      if (procEnv.containsKey("ZEPPELIN_CONF_DIR")) {
        String zconfDir = procEnv.get("ZEPPELIN_CONF_DIR");
        File file = new File(zconfDir + File.separator + locator.getFileName());
        if (file.isFile()) {
          LOGGER.info("Load configuration from {}", file);
          return file.toURI().toURL();
        }
      }
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }
    return null;
  }
}
