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

import java.io.{BufferedReader, PrintWriter}

import scala.Predef.{println => _, _}
import scala.tools.nsc.GenericRunnerSettings
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.shell.{ILoop, ShellConfig}
import scala.tools.nsc.util.stringFromStream
import scala.util.Properties.{javaVersion, javaVmName, versionString}

/**
 *  Copy from spark project with minor modification (Remove the SparkContext initialization)
 */
class SparkILoop(in0: BufferedReader, out: PrintWriter)
  extends ILoop(ShellConfig(new GenericRunnerSettings(_ => ())), in0, out) {
  def this() = this(null, new PrintWriter(Console.out, true))

  override protected def internalReplAutorunCode(): Seq[String] = Seq.empty

  /** Print a welcome message */
  override def printWelcome(): Unit = {
    import org.apache.spark.SPARK_VERSION
    echo("""Welcome to
      ____              __
     / __/__  ___ _____/ /__
    _\ \/ _ \/ _ `/ __/  '_/
   /___/ .__/\_,_/_/ /_/\_\   version %s
      /_/
         """.format(SPARK_VERSION))
    val welcomeMsg = "Using Scala %s (%s, Java %s)".format(
      versionString,
      javaVmName,
      javaVersion
    )
    echo(welcomeMsg)
    echo("Type in expressions to have them evaluated.")
    echo("Type :help for more information.")
  }

  /** Available commands */
  override def commands: List[LoopCommand] = standardCommands

  override def resetCommand(line: String): Unit = {
    super.resetCommand(line)
    echo(
      "Note that after :reset, state of SparkSession and SparkContext is unchanged."
    )
  }

  override def replay(): Unit = {
    super.replay()
  }

  /** Start an interpreter with the given settings.
   *  @return true if successful
   */
  override def run(interpreterSettings: Settings): Boolean = {
    createInterpreter(interpreterSettings)
    intp.reporter.withoutPrintingResults(intp.withSuppressedSettings {
      intp.initializeCompiler()
      if (intp.reporter.hasErrors) {
        echo("Interpreter encountered errors during initialization!")
        throw new InterruptedException
      }
    })
    true
  }
}

