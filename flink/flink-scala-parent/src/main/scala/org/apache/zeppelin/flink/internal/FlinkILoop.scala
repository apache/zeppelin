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

package org.apache.zeppelin.flink.internal


import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.util.AbstractID
import java.io.{BufferedReader, File, FileOutputStream, IOException}
import java.net.URL

import org.apache.flink.streaming.api.environment.{StreamExecutionEnvironment => JStreamExecutionEnvironment}
import org.apache.flink.api.java.{ExecutionEnvironment => JExecutionEnvironment}
import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.core.execution.PipelineExecutorServiceLoader
import org.apache.zeppelin.flink.{FlinkScalaInterpreter, YarnApplicationExecutionEnvironment, YarnApplicationStreamEnvironment}
import FlinkShell.ExecutionMode

import scala.tools.nsc.interpreter._


class FlinkILoop(
                  val flinkConfig: Configuration,
                  val externalJars: Option[Array[String]],
                  in0: Option[BufferedReader],
                  out0: JPrintWriter,
                  mode: ExecutionMode.Value,
                  jenv: JExecutionEnvironment,
                  jsenv: JStreamExecutionEnvironment,
                  flinkScalaInterpreter: FlinkScalaInterpreter)
  extends ILoop(in0, out0) {


  // remote environment
  private val (remoteBenv: ScalaShellEnvironment,
  remoteSenv: ScalaShellStreamEnvironment) = {
    ScalaShellEnvironment.resetContextEnvironments()
    ScalaShellStreamEnvironment.resetContextEnvironments()
    // create our environment that submits against the cluster (local or remote)
    val remoteBenv = new ScalaShellEnvironment(
      flinkConfig,
      this,
      this.getExternalJars(): _*)
    val remoteSenv = new ScalaShellStreamEnvironment(
      flinkConfig,
      this,
      getExternalJars(): _*)

    (remoteBenv,remoteSenv)
  }

  // local environment
  val (
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
        this,
        flinkScalaInterpreter
      ))
      val scalaSenv = new StreamExecutionEnvironment(new YarnApplicationStreamEnvironment(
        getStreamExecutionEnvironmentField(jsenv, "executorServiceLoader").asInstanceOf[PipelineExecutorServiceLoader],
        getStreamExecutionEnvironmentField(jsenv, "configuration").asInstanceOf[Configuration],
        getStreamExecutionEnvironmentField(jsenv, "userClassloader").asInstanceOf[ClassLoader],
        this,
        flinkScalaInterpreter
      ))
      (scalaBenv, scalaSenv)
    } else {
      val scalaBenv = new ExecutionEnvironment(remoteBenv)
      val scalaSenv = new StreamExecutionEnvironment(remoteSenv)
      (scalaBenv, scalaSenv)
    }
  }

  /**
   * creates a temporary directory to store compiled console files
   */
  private val tmpDirBase: File = {
    // get unique temporary folder:
    val abstractID: String = new AbstractID().toString
    val tmpDir: File = new File(
      System.getProperty("java.io.tmpdir"),
      "scala_shell_tmp-" + abstractID)
    if (!tmpDir.exists) {
      tmpDir.mkdir
    }
    tmpDir
  }

  // scala_shell commands
  private val tmpDirShell: File = {
    if (mode == ExecutionMode.YARN_APPLICATION) {
      new File(".", "scala_shell_commands")
    } else {
      new File(tmpDirBase, "scala_shell_commands")
    }
  }

  // scala shell jar file name
  private val tmpJarShell: File = {
    new File(tmpDirBase, "scala_shell_commands.jar")
  }

  private val packageImports = Seq[String](
    "org.apache.flink.core.fs._",
    "org.apache.flink.core.fs.local._",
    "org.apache.flink.api.common.io._",
    "org.apache.flink.api.common.aggregators._",
    "org.apache.flink.api.common.accumulators._",
    "org.apache.flink.api.common.distributions._",
    "org.apache.flink.api.common.operators._",
    "org.apache.flink.api.common.operators.base.JoinOperatorBase.JoinHint",
    "org.apache.flink.api.common.functions._",
    "org.apache.flink.api.java.io._",
    "org.apache.flink.api.java.aggregation._",
    "org.apache.flink.api.java.functions._",
    "org.apache.flink.api.java.operators._",
    "org.apache.flink.api.java.sampling._",
    "org.apache.flink.api.scala._",
    "org.apache.flink.api.scala.utils._",
    "org.apache.flink.streaming.api.scala._",
    "org.apache.flink.streaming.api.windowing.time._",
    "org.apache.flink.table.api._",
    "org.apache.flink.table.api.bridge.scala._",
    "org.apache.flink.types.Row"
  )

  override def createInterpreter(): Unit = {
    super.createInterpreter()

    intp.beQuietDuring {
      // import dependencies
      intp.interpret("import " + packageImports.mkString(", "))

      // set execution environment
      intp.bind("benv", this.scalaBenv)
      intp.bind("senv", this.scalaSenv)
    }
  }

  /**
   * Packages the compiled classes of the current shell session into a Jar file for execution
   * on a Flink cluster.
   *
   * @return The path of the created Jar file
   */
  def writeFilesToDisk(): File = {
    if (!tmpDirShell.exists()) {
      if (!tmpDirShell.mkdirs()) {
        throw new IOException("Fail to create tmp dir: " +
          tmpDirShell.getAbsolutePath + " for scala shell")
      }
    }
    val vd = intp.virtualDirectory

    val vdIt = vd.iterator

    for (fi <- vdIt) {
      if (fi.isDirectory) {

        val fiIt = fi.iterator

        for (f <- fiIt) {

          // directory for compiled line
          val lineDir = new File(tmpDirShell.getAbsolutePath, fi.name)
          lineDir.mkdirs()

          // compiled classes for commands from shell
          val writeFile = new File(lineDir.getAbsolutePath, f.name)
          val outputStream = new FileOutputStream(writeFile)
          val inputStream = f.input

          // copy file contents
          org.apache.commons.io.IOUtils.copy(inputStream, outputStream)

          inputStream.close()
          outputStream.close()
        }
      }
    }

    val compiledClasses = new File(tmpDirShell.getAbsolutePath)

    val jarFilePath = new File(tmpJarShell.getAbsolutePath)

    val jh: JarHelper = new JarHelper
    jh.jarDir(compiledClasses, jarFilePath)

    jarFilePath
  }

  def getExternalJars(): Array[String] = externalJars.getOrElse(Array.empty[String])

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
}

