/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.flink.internal;


import org.apache.flink.configuration.ConfigUtils;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.PipelineOptions;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.graph.StreamGraph;
import org.apache.flink.util.JarUtils;
import org.apache.zeppelin.flink.FlinkVersion;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * This class is copied from flink project, the reason is that flink scala shell only supports
 * scala-2.11, we copied it here to support scala-2.12 as well.
 */
public class ScalaShellStreamEnvironment extends StreamExecutionEnvironment {

  /**
   * The jar files that need to be attached to each job.
   */
  private final List<URL> jarFiles;

  /**
   * reference to Scala Shell, for access to virtual directory.
   */
  private final FlinkILoop flinkILoop;

  private final FlinkVersion flinkVersion;


  public ScalaShellStreamEnvironment(
          final Configuration configuration,
          final FlinkILoop flinkILoop,
          final FlinkVersion flinkVersion,
          final String... jarFiles) {
    super(configuration);
    this.flinkILoop = checkNotNull(flinkILoop);
    this.flinkVersion = checkNotNull(flinkVersion);
    this.jarFiles = checkNotNull(JarUtils.getJarFiles(jarFiles));
  }

  @Override
  public JobClient executeAsync(StreamGraph streamGraph) throws Exception {
    updateDependencies();
    return super.executeAsync(streamGraph);
  }

  private void updateDependencies() throws Exception {
    final List<URL> updatedJarFiles = getUpdatedJarFiles();
    ConfigUtils.encodeCollectionToConfig(
            (Configuration) getFlinkConfiguration(), PipelineOptions.JARS, updatedJarFiles, URL::toString);
  }

  public Object getFlinkConfiguration() {
    if (flinkVersion.isAfterFlink114()) {
      // starting from Flink 1.14, getConfiguration() return the readonly copy of internal
      // configuration, so we need to get the internal configuration object via reflection.
      try {
        Field configurationField = StreamExecutionEnvironment.class.getDeclaredField("configuration");
        configurationField.setAccessible(true);
        return configurationField.get(this);
      } catch (Exception e) {
        throw new RuntimeException("Fail to get configuration from StreamExecutionEnvironment", e);
      }
    } else {
      return super.getConfiguration();
    }
  }


  private List<URL> getUpdatedJarFiles() throws MalformedURLException {
    final URL jarUrl = flinkILoop.writeFilesToDisk().getAbsoluteFile().toURI().toURL();
    final List<URL> allJarFiles = new ArrayList<>(jarFiles);
    allJarFiles.add(jarUrl);
    return allJarFiles;
  }

  public static void resetContextEnvironments() {
    StreamExecutionEnvironment.resetContextEnvironment();
  }
}
