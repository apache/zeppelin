/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.flink;

import java.io.File;

import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.PlanExecutor;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.operators.translation.JavaPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class override execute() method to create an PlanExecutor with
 * jar file that packages classes from scala compiler.
 */
public class FlinkEnvironment extends ExecutionEnvironment {
  Logger logger = LoggerFactory.getLogger(FlinkEnvironment.class);

  private String host;
  private int port;

  private FlinkIMain imain;

  public FlinkEnvironment(String host, int port, FlinkIMain imain) {
    this.host = host;
    this.port = port;
    this.imain = imain;

    logger.info("jobManager host={}, port={}", host, port);
  }

  @Override
  public JobExecutionResult execute(String jobName) throws Exception {
    JavaPlan plan = createProgramPlan(jobName);

    File jarFile = imain.jar();
    PlanExecutor executor = PlanExecutor.createRemoteExecutor(host, port,
        jarFile.getAbsolutePath());

    JobExecutionResult result = executor.executePlan(plan);

    if (jarFile.isFile()) {
      jarFile.delete();
    }

    return result;
  }

  @Override
  public String getExecutionPlan() throws Exception {
    JavaPlan plan = createProgramPlan("unnamed", false);
    plan.setDefaultParallelism(getParallelism());
    registerCachedFilesWithPlan(plan);

    File jarFile = imain.jar();
    PlanExecutor executor = PlanExecutor.createRemoteExecutor(host, port,
        jarFile.getAbsolutePath());
    String jsonPlan = executor.getOptimizerPlanAsJSON(plan);

    if (jarFile != null && jarFile.isFile()) {
      jarFile.delete();
    }

    return jsonPlan;
  }
}
