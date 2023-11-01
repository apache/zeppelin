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

import java.io.File;
import java.util.Map;

public abstract class MiniClusterBase {

    private static final Logger LOG = LoggerFactory.getLogger(MiniClusterBase.class);

    public void main(String[] args) {
        String klass = getClass().getSimpleName();
        LOG.info("{} is starting up.", klass);

        String configPath = args[0];

        File file = new File(configPath + "/cluster.conf");
        Map<String, String> props = MiniClusterUtils.loadProperties(file);
        MiniClusterConfig config = new MiniClusterConfig(props);

        start(config, configPath);
        LOG.info("{} is running.", klass);
        while (true) synchronized (this) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected abstract void start(MiniClusterConfig config, String configPath);
}
