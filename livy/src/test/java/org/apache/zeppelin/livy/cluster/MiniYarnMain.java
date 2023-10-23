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

package org.apache.zeppelin.livy.cluster;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.MiniYARNCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class MiniYarnMain extends MiniClusterBase {

    public static MiniYarnMain INSTANCE = new MiniYarnMain();

    public static Logger LOG = LoggerFactory.getLogger(MiniYarnMain.class);

    @Override
    protected void start(MiniClusterConfig config, String configPath) {
        YarnConfiguration baseConfig = new YarnConfiguration();
        MiniYARNCluster yarnCluster = new MiniYARNCluster(getClass().getName(), config.nmCount,
                config.localDirCount, config.logDirCount);
        yarnCluster.init(baseConfig);

        // This allows applications run by YARN during the integration tests to find PIP modules
        // installed in the user's home directory (instead of just the global ones).
        baseConfig.set(YarnConfiguration.NM_USER_HOME_DIR, System.getenv("HOME"));

        // Install a shutdown hook for stop the service and kill all running applications.
        Runtime.getRuntime().addShutdownHook(new Thread(yarnCluster::stop));

        yarnCluster.start();

        // Workaround for YARN-2642, which is unresolved until now.
        Configuration yarnConfig = yarnCluster.getConfig();
//            eventually(timeout(30 seconds), interval(100 millis)) {
//                assert(yarnConfig.get(YarnConfiguration.RM_ADDRESS).split(":")(1) != "0",
//                        "RM not up yes.");
//            }

        LOG.info("RM address in configuration is {}", yarnConfig.get(YarnConfiguration.RM_ADDRESS));
        MiniClusterUtils.saveConfig(yarnConfig, new File(configPath + "/yarn-site.xml"));
    }
}
