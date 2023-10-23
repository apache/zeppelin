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
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.MiniYARNCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class MiniLivyMain extends MiniClusterBase {

    public static MiniLivyMain INSTANCE = new MiniLivyMain();

    public static Logger LOG = LoggerFactory.getLogger(MiniLivyMain.class);

    protected Map<String, String> baseLivyConf(String configPath) {
        Map<String, String> conf = new HashMap<>();
        conf.put("livy.spark.master", "yarn");
        conf.put("livy.spark.deploy-mode", "cluster");
        conf.put("livy.server.heartbeat-watchdog.interval", "1s");
        conf.put("livy.server.yarn.poll-interval", "500ms");
        conf.put("livy.server.recovery.mode", "recovery");
        conf.put("livy.server.recovery.state-store", "filesystem");
        conf.put("livy.server.recovery.state-store.url", "file://" + configPath + "/state-store");
        return conf;
    }

    @Override
    protected void start(MiniClusterConfig config, String configPath) {
        try {
            Map<String, String> livyConf = baseLivyConf(configPath);
            // if (Cluster.isRunningOnTravis) {
            //   livyConf.put("livy.server.yarn.app-lookup-timeout", "2m");
            // }
            livyConf.put("livy.repl.enable-hive-context", "false");
            MiniClusterUtils.saveProperties(livyConf, new File(configPath + "/livy.conf"));

            String livyServerScript = Paths.get(System.getenv("LIVY_HOME"))
                    .resolve("bin/livy-server")
                    .toAbsolutePath().toString();

            ProcessBuilder livyServerPb = new ProcessBuilder(livyServerScript);
            livyServerPb.environment().put("LIVY_CONF_DIR", configPath);
            Process livyServerProcess = livyServerPb.start();


            // Write a serverUrl.conf file to the conf directory with the location of the Livy
            // server. Do it atomically since it's used by MiniCluster to detect when the Livy server
            // is up and ready.
            eventually(timeout(30 seconds), interval(1 second)) {
                var serverUrlConf = Map("livy.server.server-url" -> server.serverUrl())
                MiniClusterUtils.saveProperties(serverUrlConf, new File(configPath + "/serverUrl.conf"));
            }
        }catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }
}
