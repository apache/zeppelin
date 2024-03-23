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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class MiniLivyMain extends MiniClusterBase {

    public static MiniLivyMain INSTANCE = new MiniLivyMain();

    public static Logger LOG = LoggerFactory.getLogger(MiniLivyMain.class);

    protected Map<String, String> baseLivyConf(String configPath) {
        Map<String, String> conf = new HashMap<>();
        //conf.put("livy.spark.master", "yarn");
        //conf.put("livy.spark.deploy-mode", "cluster");
        conf.put("livy.server.heartbeat-watchdog.interval", "1s");
       // conf.put("livy.server.yarn.poll-interval", "500ms");
        //conf.put("livy.server.recovery.mode", "recovery");
        //conf.put("livy.server.recovery.state-store", "filesystem");
        //conf.put("livy.server.recovery.state-store.url", "file://" + configPath + "/state-store");
        return conf;
    }

    @Override
    protected void start(MiniClusterConfig config, String configPath) {
        try {
            String workingDir = Paths.get(".").toAbsolutePath().toString();
            File outputFile = new File(workingDir, "output.log");
            FileUtils.deleteQuietly(outputFile);
            outputFile.createNewFile();

            Map<String, String> livyConf = baseLivyConf(configPath);
            // if (Cluster.isRunningOnTravis) {
            //   livyConf.put("livy.server.yarn.app-lookup-timeout", "2m");
            // }
            livyConf.put("livy.repl.enable-hive-context", "false");
            MiniClusterUtils.saveProperties(livyConf, new File(configPath + "/livy.conf"));

            String livyServerScript = Paths.get(System.getenv("LIVY_HOME"))
                    .resolve("bin/livy-server")
                    .toAbsolutePath().toString();

            ProcessBuilder livyPb = new ProcessBuilder(livyServerScript);
            livyPb.environment().put("LIVY_CONF_DIR", configPath);
            livyPb.environment().put("LIVY_LOG_DIR", workingDir);
            livyPb.redirectErrorStream(true);
            livyPb.redirectOutput(outputFile);
            Process livyProcess = livyPb.start();

            // TODO run in new thread and add 30s timeout check
            try (Stream<String> lines = Files.lines(outputFile.toPath(), StandardCharsets.UTF_8)) {
                // An example of bootstrap log:
                //   23/10/28 12:28:34 INFO WebServer: Starting server on http://my-laptop:8998
                lines.filter(line -> line.contains("Starting server on")).findFirst().ifPresent(log -> {
                            String serverUrl = StringUtils.substringAfter(log, "Starting server on").trim();
                            // Write a serverUrl.conf file to the conf directory with the location of the Livy
                            // server. Do it atomically since it's used by MiniCluster to detect when the Livy server
                            // is up and ready.
                            Map<String, String> serverUrlConf = new HashMap<>();
                            serverUrlConf.put("livy.server.server-url", serverUrl);
                            MiniClusterUtils.saveProperties(serverUrlConf, new File(configPath + "/serverUrl.conf"));
                        }
                );
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }
}
