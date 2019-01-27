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

import org.apache.spark.SparkConf
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream
import org.apache.zeppelin.interpreter.{InterpreterContext, InterpreterResult}
import org.slf4j.LoggerFactory
import org.slf4j.Logger

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._

/**
  * SparkInterpreter for scala-2.11
  */
class SparkScala211Interpreter(override val conf: SparkConf,
                               override val depFiles: java.util.List[String],
                               override val printReplOutput: java.lang.Boolean)
  extends BaseSparkScalaInterpreter(conf, depFiles, printReplOutput) {

  import SparkScala211Interpreter._

  lazy override val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  private var sparkILoop: ILoop = _

  override val interpreterOutput = new InterpreterOutputStream(LOGGER)

  override def open(): Unit = {
    super.open()
    if (conf.get("spark.master", "local") == "yarn-client") {
      System.setProperty("SPARK_YARN_MODE", "true")
    }
    // Only Spark1 requires to create http server, Spark2 removes HttpServer class.
    val rootDir = conf.get("spark.repl.classdir", System.getProperty("java.io.tmpdir"))
    val outputDir = Files.createTempDirectory(Paths.get(rootDir), "spark").toFile
    outputDir.deleteOnExit()
    conf.set("spark.repl.class.outputDir", outputDir.getAbsolutePath)
    startHttpServer(outputDir).foreach { case (server, uri) =>
      sparkHttpServer = server
      conf.set("spark.repl.class.uri", uri)
    }

    val settings = new Settings()
    settings.processArguments(List("-Yrepl-class-based",
      "-Yrepl-outdir", s"${outputDir.getAbsolutePath}"), true)
    settings.embeddedDefaults(Thread.currentThread().getContextClassLoader())
    settings.usejavacp.value = true
    settings.classpath.value = getUserJars.mkString(File.pathSeparator)

    val replOut = if (printReplOutput) {
      new JPrintWriter(interpreterOutput, true)
    } else {
      new JPrintWriter(Console.out, true)
    }
    sparkILoop = new ILoop(None, replOut)
    sparkILoop.settings = settings
    sparkILoop.createInterpreter()

    val in0 = getField(sparkILoop, "scala$tools$nsc$interpreter$ILoop$$in0").asInstanceOf[Option[BufferedReader]]
    val reader = in0.fold(sparkILoop.chooseReader(settings))(r => SimpleReader(r, replOut, interactive = true))

    sparkILoop.in = reader
    sparkILoop.initializeSynchronous()
    loopPostInit(this)
    this.scalaCompleter = reader.completion.completer()

    createSparkContext()
  }

  protected def bind(name: String, tpe: String, value: Object, modifier: List[String]): Unit = {
    sparkILoop.beQuietDuring {
      sparkILoop.bind(name, tpe, value, modifier)
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

}

private object SparkScala211Interpreter {

  /**
    * This is a hack to call `loopPostInit` at `ILoop`. At higher version of Scala such
    * as 2.11.12, `loopPostInit` became a nested function which is inaccessible. Here,
    * we redefine `loopPostInit` at Scala's 2.11.8 side and ignore `loadInitFiles` being called at
    * Scala 2.11.12 since here we do not have to load files.
    *
    * Both methods `loopPostInit` and `unleashAndSetPhase` are redefined, and `phaseCommand` and
    * `asyncMessage` are being called via reflection since both exist in Scala 2.11.8 and 2.11.12.
    *
    * Please see the codes below:
    * https://github.com/scala/scala/blob/v2.11.8/src/repl/scala/tools/nsc/interpreter/ILoop.scala
    * https://github.com/scala/scala/blob/v2.11.12/src/repl/scala/tools/nsc/interpreter/ILoop.scala
    *
    * See also ZEPPELIN-3810.
    */
  private def loopPostInit(interpreter: SparkScala211Interpreter): Unit = {
    import StdReplTags._
    import scala.reflect.classTag
    import scala.reflect.io

    val sparkILoop = interpreter.sparkILoop
    val intp = sparkILoop.intp
    val power = sparkILoop.power
    val in = sparkILoop.in

    def loopPostInit() {
      // Bind intp somewhere out of the regular namespace where
      // we can get at it in generated code.
      intp.quietBind(NamedParam[IMain]("$intp", intp)(tagOfIMain, classTag[IMain]))
      // Auto-run code via some setting.
      (replProps.replAutorunCode.option
        flatMap (f => io.File(f).safeSlurp())
        foreach (intp quietRun _)
        )
      // classloader and power mode setup
      intp.setContextClassLoader()
      if (isReplPower) {
        replProps.power setValue true
        unleashAndSetPhase()
        asyncMessage(power.banner)
      }
      // SI-7418 Now, and only now, can we enable TAB completion.
      in.postInit()
    }

    def unleashAndSetPhase() = if (isReplPower) {
      power.unleash()
      intp beSilentDuring phaseCommand("typer") // Set the phase to "typer"
    }

    def phaseCommand(name: String): Results.Result = {
      interpreter.callMethod(
        sparkILoop,
        "scala$tools$nsc$interpreter$ILoop$$phaseCommand",
        Array(classOf[String]),
        Array(name)).asInstanceOf[Results.Result]
    }

    def asyncMessage(msg: String): Unit = {
      interpreter.callMethod(
        sparkILoop, "asyncMessage", Array(classOf[String]), Array(msg))
    }

    loopPostInit()
  }
}
