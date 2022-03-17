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
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.configuration.ConfigUtils;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.DeploymentOptions;
import org.apache.flink.configuration.PipelineOptions;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.core.execution.PipelineExecutorServiceLoader;
import org.apache.flink.streaming.api.graph.StreamGraph;
import org.apache.zeppelin.flink.internal.FlinkILoop;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.apache.flink.util.Preconditions.checkState;


/**
 * ExecutionEnvironment used for application mode.
 * Need to add jars of scala shell before submitting jobs.
 */
public class ApplicationModeExecutionEnvironment extends ExecutionEnvironment {

  private FlinkILoop flinkILoop;
  private FlinkScalaInterpreter flinkScalaInterpreter;

  public ApplicationModeExecutionEnvironment(PipelineExecutorServiceLoader executorServiceLoader,
                                             Configuration configuration,
                                             ClassLoader userClassloader,
                                             FlinkILoop flinkILoop,
                                             FlinkScalaInterpreter flinkScalaInterpreter) {
    super(executorServiceLoader,configuration,userClassloader);
    this.flinkILoop = flinkILoop;
    this.flinkScalaInterpreter = flinkScalaInterpreter;
  }

  @Override
  public JobClient executeAsync(String jobName) throws Exception {
    updateDependencies();
    return super.executeAsync(jobName);
  }

  @Override
  public JobExecutionResult execute() throws Exception {
    updateDependencies();
    return super.execute();
  }

  @Override
  public JobExecutionResult execute(String jobName) throws Exception {
    updateDependencies();
    return super.execute(jobName);
  }

  private void updateDependencies() throws Exception {
    final Configuration configuration = getConfiguration();
    checkState(
            configuration.getBoolean(DeploymentOptions.ATTACHED),
            "Only ATTACHED mode is supported by the scala shell.");

    final List<URL> updatedJarFiles = getUpdatedJarFiles();
    ConfigUtils.encodeCollectionToConfig(
            configuration, PipelineOptions.JARS, updatedJarFiles, URL::toString);
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
