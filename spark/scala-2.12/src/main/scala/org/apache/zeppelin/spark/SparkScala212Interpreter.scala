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

package org.apache.zeppelin.spark

import java.io.{BufferedReader, File}
import java.net.URLClassLoader
import java.nio.file.{Files, Paths}
import java.util.Properties

import org.apache.spark.SparkConf
import org.apache.spark.repl.SparkILoop
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream
import org.apache.zeppelin.interpreter.{InterpreterContext, InterpreterGroup}
import org.slf4j.LoggerFactory
import org.slf4j.Logger

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._

/**
  * SparkInterpreter for scala-2.12
  */
class SparkScala212Interpreter(override val conf: SparkConf,
                               override val depFiles: java.util.List[String],
                               override val properties: Properties,
                               override val interpreterGroup: InterpreterGroup,
                               override val sparkInterpreterClassLoader: URLClassLoader)
  extends BaseSparkScalaInterpreter(conf, depFiles, properties, interpreterGroup, sparkInterpreterClassLoader) {

  lazy override val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  private var sparkILoop: SparkILoop = _

  override val interpreterOutput = new InterpreterOutputStream(LOGGER)

  override def open(): Unit = {
    super.open()
    if (conf.get("spark.master", "local") == "yarn-client") {
      System.setProperty("SPARK_YARN_MODE", "true")
    }
    // Only Spark1 requires to create http server, Spark2 removes HttpServer class.
    val rootDir = conf.get("spark.repl.classdir", System.getProperty("java.io.tmpdir"))
    this.outputDir = Files.createTempDirectory(Paths.get(rootDir), "spark").toFile
    LOGGER.info("Scala shell repl output dir: " + outputDir.getAbsolutePath)
    outputDir.deleteOnExit()
    conf.set("spark.repl.class.outputDir", outputDir.getAbsolutePath)

    val settings = new Settings()
    settings.processArguments(List("-Yrepl-class-based",
      "-Yrepl-outdir", s"${outputDir.getAbsolutePath}"), true)
    settings.embeddedDefaults(sparkInterpreterClassLoader)
    settings.usejavacp.value = true
    this.userJars = getUserJars()
    LOGGER.info("UserJars: " + userJars.mkString(File.pathSeparator))
    settings.classpath.value = userJars.mkString(File.pathSeparator)

    val printReplOutput = properties.getProperty("zeppelin.spark.printREPLOutput", "true").toBoolean
    val replOut = if (printReplOutput) {
      new JPrintWriter(interpreterOutput, true)
    } else {
      new JPrintWriter(Console.out, true)
    }
    sparkILoop = new SparkILoop(None, replOut)
    sparkILoop.settings = settings
    sparkILoop.createInterpreter()
    val in0 = getDeclareField(sparkILoop, "in0").asInstanceOf[Option[BufferedReader]]
    val reader = in0.fold(sparkILoop.chooseReader(settings))(r => SimpleReader(r, replOut, interactive = true))

    sparkILoop.in = reader
    sparkILoop.initializeSynchronous()
    sparkILoop.in.postInit()
    this.scalaCompletion = reader.completion

    createSparkContext()
    createZeppelinContext()
  }

  protected override def completion(buf: String,
                           cursor: Int,
                           context: InterpreterContext): java.util.List[InterpreterCompletion] = {
    val completions = scalaCompletion.complete(buf.substring(0, cursor), cursor).candidates
      .map(e => new InterpreterCompletion(e, e, null))
    scala.collection.JavaConversions.seqAsJavaList(completions)
  }

  protected def bind(name: String, tpe: String, value: Object, modifier: List[String]): Unit = {
    sparkILoop.beQuietDuring {
      val result = sparkILoop.bind(name, tpe, value, modifier)
      if (result != IR.Success) {
        throw new RuntimeException("Fail to bind variable: " + name)
      }
    }
  }


  override def close(): Unit = {
    super.close()
    if (sparkILoop != null) {
      sparkILoop.closeInterpreter()
    }
  }

  def scalaInterpret(code: String): scala.tools.nsc.interpreter.IR.Result =
    sparkILoop.interpret(code)

  override def getScalaShellClassLoader: ClassLoader = {
    sparkILoop.classLoader
  }

}
