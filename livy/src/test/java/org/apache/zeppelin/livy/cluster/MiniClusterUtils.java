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

import java.io.*;

import org.apache.hadoop.conf.Configuration;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MiniClusterUtils {

    protected static void saveProperties(Map<String, String> props, File dest) {
        try {
            Properties newProps = new Properties();
            newProps.putAll(props);
            File tempFile = new File(dest.getAbsolutePath() + ".tmp");
            try (Writer out = new OutputStreamWriter(Files.newOutputStream(tempFile.toPath()), UTF_8)) {
                newProps.store(out, "Configuration");
            }
            tempFile.renameTo(dest);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    protected static Map<String, String> loadProperties(InputStream input) {
        try {
            Properties props = new Properties();
            try (Reader in = new InputStreamReader(input, UTF_8)) {
                props.load(in);
            }

            Map<String, String> newProps = new HashMap<>();
            for (Map.Entry<Object, Object> entry : props.entrySet()) {
                newProps.put(((String) entry.getKey()), ((String) entry.getValue()));
            }
            return newProps;
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    protected static Map<String, String> loadProperties(File file) {
        try {
            Properties props = new Properties();
            try (Reader in = new InputStreamReader(Files.newInputStream(file.toPath()), UTF_8)) {
                props.load(in);
            }

            Map<String, String> newProps = new HashMap<>();
            for (Map.Entry<Object, Object> entry : props.entrySet()) {
                newProps.put(((String) entry.getKey()), ((String) entry.getValue()));
            }
            return newProps;
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    protected static void saveConfig(Configuration conf, File dest) {
        try {
            Configuration redacted = new Configuration(conf);
            // This setting references a test class that is not available when using a real Spark
            // installation, so remove it from client configs.
            redacted.unset("net.topology.node.switch.mapping.impl");

            try (OutputStream out = Files.newOutputStream(dest.toPath())) {
                redacted.writeXml(out);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }
}
