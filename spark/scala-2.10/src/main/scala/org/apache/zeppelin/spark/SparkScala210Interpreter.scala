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
import java.nio.file.{Files, Paths}

import org.apache.spark.SparkConf
import org.apache.spark.repl.SparkILoop
import org.apache.spark.repl.SparkILoop._
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream
import org.apache.zeppelin.interpreter.{InterpreterContext, InterpreterResult}
import org.slf4j.{Logger, LoggerFactory}

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._

/**
  * SparkInterpreter for scala-2.10
  */
class SparkScala210Interpreter(override val conf: SparkConf,
                               override val depFiles: java.util.List[String])
  extends BaseSparkScalaInterpreter(conf, depFiles) {

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
    val outputDir = Files.createTempDirectory(Paths.get(rootDir), "spark").toFile
    outputDir.deleteOnExit()
    conf.set("spark.repl.class.outputDir", outputDir.getAbsolutePath)
    // Only Spark1 requires to create http server, Spark2 removes HttpServer class.
    startHttpServer(outputDir).foreach { case (server, uri) =>
      sparkHttpServer = server
      conf.set("spark.repl.class.uri", uri)
    }

    val settings = new Settings()
    settings.embeddedDefaults(Thread.currentThread().getContextClassLoader())
    settings.usejavacp.value = true
    settings.classpath.value = getUserJars.mkString(File.pathSeparator)
    Console.setOut(interpreterOutput)
    sparkILoop = new SparkILoop()

    setDeclaredField(sparkILoop, "settings", settings)
    callMethod(sparkILoop, "createInterpreter")
    sparkILoop.initializeSynchronous()
    callMethod(sparkILoop, "postInitialization")
    val reader = callMethod(sparkILoop,
      "org$apache$spark$repl$SparkILoop$$chooseReader",
      Array(settings.getClass), Array(settings)).asInstanceOf[InteractiveReader]
    setDeclaredField(sparkILoop, "org$apache$spark$repl$SparkILoop$$in", reader)
    scalaCompleter = reader.completion.completer()

    createSparkContext()
  }

  override def close(): Unit = {
    super.close()
    if (sparkILoop != null) {
      callMethod(sparkILoop, "org$apache$spark$repl$SparkILoop$$closeInterpreter")
    }
  }

  protected override def interpret(code: String, context: InterpreterContext): InterpreterResult = {
    if (context != null) {
      interpreterOutput.setInterpreterOutput(context.out)
      context.out.clear()
    } else {
      interpreterOutput.setInterpreterOutput(null)
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

  protected def bind(name: String, tpe: String, value: Object, modifier: List[String]): Unit = {
    sparkILoop.beQuietDuring {
      sparkILoop.bind(name, tpe, value, modifier)
    }
  }

}
