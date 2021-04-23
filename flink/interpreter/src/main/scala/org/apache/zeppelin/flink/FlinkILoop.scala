/*
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

package org.apache.zeppelin.flink

import java.io.{BufferedReader, File}

import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.api.java.{ScalaShellEnvironment, ScalaShellStreamEnvironment, ExecutionEnvironment => JExecutionEnvironment}
import org.apache.flink.configuration.Configuration
import org.apache.flink.core.execution.PipelineExecutorServiceLoader
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.api.environment.{StreamExecutionEnvironment => JStreamExecutionEnvironment}
import org.apache.zeppelin.flink.FlinkShell.ExecutionMode

import scala.tools.nsc.interpreter._


class FlinkILoop(
    override val flinkConfig: Configuration,
    override val externalJars: Option[Array[String]],
    in0: Option[BufferedReader],
    out0: JPrintWriter,
    mode: ExecutionMode.Value,
    jenv: JExecutionEnvironment,
    jsenv: JStreamExecutionEnvironment) extends org.apache.flink.api.scala.FlinkILoop(flinkConfig, externalJars, in0, out0) {


  override def writeFilesToDisk(): File = {
    if (mode == ExecutionMode.YARN_APPLICATION) {
      // write jars to current working directory in yarn application mode.
      val tmpDirShellField = classOf[org.apache.flink.api.scala.FlinkILoop]
        .getDeclaredField("org$apache$flink$api$scala$FlinkILoop$$tmpDirShell")
      tmpDirShellField.setAccessible(true)
      tmpDirShellField.set(this, new File("scala_shell_commands"));

      val tmpJarShellField = classOf[org.apache.flink.api.scala.FlinkILoop].getDeclaredField("tmpJarShell")
      tmpJarShellField.setAccessible(true)
      tmpJarShellField.set(this, new File("scala_shell_commands.jar"));
    } else {
      // create tmpDirBase again in case it is deleted by system, because it is in the system temp folder.
      val field = classOf[org.apache.flink.api.scala.FlinkILoop].getDeclaredField("tmpDirBase")
      field.setAccessible(true)
      val tmpDir = field.get(this).asInstanceOf[File]
      if (!tmpDir.exists()) {
        tmpDir.mkdir()
      }
    }

    super.writeFilesToDisk()
  }

  override val (
    scalaBenv: ExecutionEnvironment,
    scalaSenv: StreamExecutionEnvironment
    ) = {
    if (mode == ExecutionMode.YARN_APPLICATION) {
      // For yarn application mode, ExecutionEnvironment & StreamExecutionEnvironment has already been created
      // by flink itself, we here just try get them via reflection and reconstruct them.
      val scalaBenv = new ExecutionEnvironment(new YarnApplicationExecutionEnvironment(
        getExecutionEnvironmentField(jenv, "executorServiceLoader").asInstanceOf[PipelineExecutorServiceLoader],
        getExecutionEnvironmentField(jenv, "configuration").asInstanceOf[Configuration],
        getExecutionEnvironmentField(jenv, "userClassloader").asInstanceOf[ClassLoader],
        this
      ))
      val scalaSenv = new StreamExecutionEnvironment(new YarnApplicationStreamEnvironment(
        getStreamExecutionEnvironmentField(jsenv, "executorServiceLoader").asInstanceOf[PipelineExecutorServiceLoader],
        getStreamExecutionEnvironmentField(jsenv, "configuration").asInstanceOf[Configuration],
        getStreamExecutionEnvironmentField(jsenv, "userClassloader").asInstanceOf[ClassLoader],
        this
      ))
      (scalaBenv, scalaSenv)
    } else {
      val scalaBenv = new ExecutionEnvironment(
        getSuperFlinkILoopField("remoteBenv").asInstanceOf[ScalaShellEnvironment])
      val scalaSenv = new StreamExecutionEnvironment(
        getSuperFlinkILoopField("remoteSenv").asInstanceOf[ScalaShellStreamEnvironment])
      (scalaBenv, scalaSenv)
    }
  }

  private def getExecutionEnvironmentField(obj: Object, name: String): Object = {
    val field = classOf[JExecutionEnvironment].getDeclaredField(name)
    field.setAccessible(true)
    field.get(obj)
  }

  private def getStreamExecutionEnvironmentField(obj: Object, name: String): Object = {
    val field = classOf[JStreamExecutionEnvironment].getDeclaredField(name)
    field.setAccessible(true)
    field.get(obj)
  }

  private def getSuperFlinkILoopField(name: String): Object = {
    val field = classOf[org.apache.flink.api.scala.FlinkILoop].getDeclaredField(name)
    field.setAccessible(true)
    field.get(this)
  }
}
