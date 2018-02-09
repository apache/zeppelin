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
                               override val depFiles: java.util.List[String])
  extends BaseSparkScalaInterpreter(conf, depFiles) {

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

    val replOut = new JPrintWriter(interpreterOutput, true)
    sparkILoop = new ILoop(None, replOut)
    sparkILoop.settings = settings
    sparkILoop.createInterpreter()

    val in0 = getField(sparkILoop, "scala$tools$nsc$interpreter$ILoop$$in0").asInstanceOf[Option[BufferedReader]]
    val reader = in0.fold(sparkILoop.chooseReader(settings))(r => SimpleReader(r, replOut, interactive = true))

    sparkILoop.in = reader
    sparkILoop.initializeSynchronous()
    callMethod(sparkILoop, "scala$tools$nsc$interpreter$ILoop$$loopPostInit")
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

  protected override def interpret(code: String, context: InterpreterContext): InterpreterResult = {
    if (context != null) {
      interpreterOutput.setInterpreterOutput(context.out)
      context.out.clear()
    }

    Console.withOut(if (context != null) context.out else Console.out) {
      interpreterOutput.ignoreLeadingNewLinesFromScalaReporter()
      // add print("") at the end in case the last line is comment which lead to INCOMPLETE
      val lines = code.split("\\n") ++ List("print(\"\")")
      var incompleteCode = ""
      var lastStatus: InterpreterResult.Code = null
      for (line <- lines if !line.trim.isEmpty) {
        val nextLine = if (incompleteCode != "") {
          incompleteCode + "\n" + line
        } else {
          line
        }
        scalaInterpret(nextLine) match {
          case scala.tools.nsc.interpreter.IR.Success =>
            // continue the next line
            incompleteCode = ""
            lastStatus = InterpreterResult.Code.SUCCESS
          case error@scala.tools.nsc.interpreter.IR.Error =>
            return new InterpreterResult(InterpreterResult.Code.ERROR)
          case scala.tools.nsc.interpreter.IR.Incomplete =>
            // put this line into inCompleteCode for the next execution.
            incompleteCode = incompleteCode + "\n" + line
            lastStatus = InterpreterResult.Code.INCOMPLETE
        }
      }
      // flush all output before returning result to frontend
      Console.flush()
      interpreterOutput.setInterpreterOutput(null)
      return new InterpreterResult(lastStatus)
    }
  }

  def scalaInterpret(code: String): scala.tools.nsc.interpreter.IR.Result =
    sparkILoop.interpret(code)

}
