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

package org.apache.zeppelin.flink;

import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.configuration.ConfigUtils;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.DeploymentOptions;
import org.apache.flink.configuration.PipelineOptions;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.core.execution.PipelineExecutorServiceLoader;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.graph.StreamGraph;
import org.apache.zeppelin.flink.internal.FlinkILoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.apache.flink.util.Preconditions.checkState;


/**
 * StreamExecutionEnvironment used for application mode.
 * Need to add jars of scala shell before submitting jobs.
 */
public class ApplicationModeStreamEnvironment extends StreamExecutionEnvironment {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationModeStreamEnvironment.class);

  private FlinkILoop flinkILoop;
  private FlinkScalaInterpreter flinkScalaInterpreter;

  public ApplicationModeStreamEnvironment(PipelineExecutorServiceLoader executorServiceLoader,
                                          Configuration configuration,
                                          ClassLoader userClassloader,
                                          FlinkILoop flinkILoop,
                                          FlinkScalaInterpreter flinkScalaInterpreter) {
    super(executorServiceLoader,configuration,userClassloader);
    this.flinkILoop = flinkILoop;
    this.flinkScalaInterpreter = flinkScalaInterpreter;
  }

  @Override
  public JobExecutionResult execute() throws Exception {
    updateDependencies();
    return super.execute();
  }

  @Override
  public JobClient executeAsync(String jobName) throws Exception {
    updateDependencies();
    return super.executeAsync(jobName);
  }

  @Override
  public JobExecutionResult execute(StreamGraph streamGraph) throws Exception {
    updateDependencies();
    return super.execute(streamGraph);
  }

  @Override
  public JobClient executeAsync(StreamGraph streamGraph) throws Exception {
    updateDependencies();
    return super.executeAsync(streamGraph);
  }

  private void updateDependencies() throws Exception {
    final Configuration configuration = (Configuration) getFlinkConfiguration();
    checkState(
            configuration.getBoolean(DeploymentOptions.ATTACHED),
            "Only ATTACHED mode is supported by the scala shell.");

    final List<URL> updatedJarFiles = getUpdatedJarFiles();
    ConfigUtils.encodeCollectionToConfig(
            configuration, PipelineOptions.JARS, updatedJarFiles, URL::toString);
  }

  public Object getFlinkConfiguration() {
    if (flinkScalaInterpreter.getFlinkVersion().isAfterFlink114()) {
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
    final List<URL> allJarFiles = new ArrayList<>();
    allJarFiles.add(jarUrl);
    for (String jar : flinkScalaInterpreter.getUserJars()) {
      allJarFiles.add(new File(jar).toURI().toURL());
    }
    return allJarFiles;
  }
}
