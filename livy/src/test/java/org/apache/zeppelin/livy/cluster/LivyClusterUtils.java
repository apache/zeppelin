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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LivyClusterUtils {

    public static Logger LOG = LoggerFactory.getLogger(LivyClusterUtils.class);

    private static Map<String, String> _config = null;

    public static Map<String, String> config() {
        if (_config == null) {
            String path = System.getProperties().getProperty("cluster.spec");
            if (path == null || "default".equals(path)) {
                _config = new HashMap<>();
            } else {
                try {
                    InputStream in = Optional
                            .ofNullable(Thread.currentThread().getContextClassLoader().getResourceAsStream(path))
                            .orElse(Files.newInputStream(Paths.get(path)));
                    _config = MiniClusterUtils.loadProperties(in);
                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                }

            }
        }
        return _config;
    }

    private static LivyCluster _livyCluster = null;

    private static LivyCluster cluster() {
        if (_livyCluster == null) {
            LivyCluster _cluster = null;
            try {
                _cluster = new MiniCluster(config());
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    LOG.info("Shutting down cluster pool.");
                    _cluster.cleanUp();
                }));
                _cluster.deploy();
            } catch (Throwable e) {
                LOG.error("Failed to initialize cluster.", e);
                if (_cluster != null) {
                    try {
                        _cluster.cleanUp();
                    } catch (Throwable t) {
                        LOG.error("Furthermore, failed to clean up cluster after failure.", t);
                    }
                }
                throw e;
            }
            _livyCluster = _cluster;
        }
        return _livyCluster;
    }

    public static LivyCluster get() {
        return cluster();
    }
}
