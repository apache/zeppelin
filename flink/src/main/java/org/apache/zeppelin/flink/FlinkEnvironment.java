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
 *
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

/*
  private File createJar() throws IOException {
    // create execution environment
    File jarFile = new File(System.getProperty("java.io.tmpdir")
        + "/ZeppelinFlinkJar_" + System.currentTimeMillis() + ".jar");


    File[] classFiles = classDir.listFiles();
    if (classFiles == null) {
      return null;
    }

    byte buffer[] = new byte[10240];
    // Open archive file
    FileOutputStream stream = new FileOutputStream(jarFile);
    JarOutputStream out = new JarOutputStream(stream, new Manifest());

    for (int i = 0; i < classFiles.length; i++) {
      File classFile = classFiles[i];
      if (classFiles == null || !classFile.exists()
          || classFile.isDirectory())
        continue;


      // Add class
      JarEntry jarAdd = new JarEntry(classFile.getName());
      jarAdd.setTime(classFile.lastModified());
      out.putNextEntry(jarAdd);
      logger.info("add class {} into jar", classFile);

      // Write file to archive
      FileInputStream in = new FileInputStream(classFile);
      while (true) {
        int nRead = in.read(buffer, 0, buffer.length);
        if (nRead <= 0)
          break;
        out.write(buffer, 0, nRead);
      }
      in.close();
    }

    out.close();
    stream.close();
    return jarFile;
  }
  */

}
