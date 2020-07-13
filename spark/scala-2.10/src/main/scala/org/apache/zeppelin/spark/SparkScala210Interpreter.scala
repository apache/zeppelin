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

import java.io.File
import java.net.URLClassLoader
import java.nio.file.{Files, Paths}
import java.util.Properties

import org.apache.spark.SparkConf
import org.apache.spark.repl.SparkILoop
import org.apache.spark.repl.SparkILoop._
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream
import org.apache.zeppelin.interpreter.{InterpreterContext, InterpreterGroup}
import org.slf4j.{Logger, LoggerFactory}

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._

/**
  * SparkInterpreter for scala-2.10
  */
class SparkScala210Interpreter(override val conf: SparkConf,
                               override val depFiles: java.util.List[String],
                               override val properties: Properties,
                               override val interpreterGroup: InterpreterGroup,
                               override val sparkInterpreterClassLoader: URLClassLoader)
  extends BaseSparkScalaInterpreter(conf, depFiles, properties, interpreterGroup, sparkInterpreterClassLoader) {

  lazy override val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  private var sparkILoop: SparkILoop = _

  override val interpreterOutput =
    new InterpreterOutputStream(LoggerFactory.getLogger(classOf[SparkScala210Interpreter]))

  override def open(): Unit = {
    super.open()
    // redirect the output of open to InterpreterOutputStream, so that user can have more
    // diagnose info in frontend
    if (InterpreterContext.get() != null) {
      interpreterOutput.setInterpreterOutput(InterpreterContext.get().out)
    }
    val rootDir = conf.get("spark.repl.classdir", System.getProperty("java.io.tmpdir"))
    this.outputDir = Files.createTempDirectory(Paths.get(rootDir), "spark").toFile
    outputDir.deleteOnExit()
    LOGGER.info("Scala shell repl output dir: " + outputDir.getAbsolutePath)
    conf.set("spark.repl.class.outputDir", outputDir.getAbsolutePath)
    // Only Spark1 requires to create http server, Spark2 removes HttpServer class.
    startHttpServer(outputDir).foreach { case (server, uri) =>
      sparkHttpServer = server
      conf.set("spark.repl.class.uri", uri)
    }
    val target = conf.get("spark.repl.target", "jvm-1.8")

    val settings = new Settings()
    settings.embeddedDefaults(sparkInterpreterClassLoader)
    settings.usejavacp.value = true
    settings.target.value = target

    this.userJars = getUserJars()
    LOGGER.info("UserJars: " + userJars.mkString(File.pathSeparator))
    settings.classpath.value = userJars.mkString(File.pathSeparator)
    if (properties.getProperty("zeppelin.spark.printREPLOutput", "true").toBoolean) {
      Console.setOut(interpreterOutput)
    }
    sparkILoop = new SparkILoop()

    setDeclaredField(sparkILoop, "settings", settings)
    callMethod(sparkILoop, "createInterpreter")
    sparkILoop.initializeSynchronous()
    callMethod(sparkILoop, "postInitialization")
    val reader = callMethod(sparkILoop,
      "org$apache$spark$repl$SparkILoop$$chooseReader",
      Array(settings.getClass), Array(settings)).asInstanceOf[InteractiveReader]
    setDeclaredField(sparkILoop, "org$apache$spark$repl$SparkILoop$$in", reader)
    this.scalaCompletion = reader.completion

    createSparkContext()
    createZeppelinContext()
  }

  protected def completion(buf: String,
                                    cursor: Int,
                                    context: InterpreterContext): java.util.List[InterpreterCompletion] = {
    val completions = scalaCompletion.completer().complete(buf.substring(0, cursor), cursor).candidates
      .map(e => new InterpreterCompletion(e, e, null))
    scala.collection.JavaConversions.seqAsJavaList(completions)
  }

  def scalaInterpret(code: String): scala.tools.nsc.interpreter.IR.Result =
    sparkILoop.interpret(code)

  protected def bind(name: String, tpe: String, value: Object, modifier: List[String]): Unit = {
    sparkILoop.beQuietDuring {
      sparkILoop.bind(name, tpe, value, modifier)
    }
  }

  override def getScalaShellClassLoader: ClassLoader = {
    val sparkIMain = sparkILoop.interpreter
    callMethod(sparkIMain, "classLoader").asInstanceOf[ClassLoader]
  }
}
