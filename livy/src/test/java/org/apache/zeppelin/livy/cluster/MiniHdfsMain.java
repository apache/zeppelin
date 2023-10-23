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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class MiniHdfsMain extends MiniClusterBase {

    public static MiniHdfsMain INSTANCE = new MiniHdfsMain();

    @Override
    protected void start(MiniClusterConfig config, String configPath) {
        try {
            Configuration hadoopConf = new Configuration();
            MiniDFSCluster hdfsCluster = new MiniDFSCluster.Builder(hadoopConf)
                    .numDataNodes(config.dnCount)
                    .format(true)
                    .waitSafeMode(true)
                    .build();

            hdfsCluster.waitActive();

            MiniClusterUtils.saveConfig(hadoopConf, new File(configPath + "/core-site.xml"));
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }
}
