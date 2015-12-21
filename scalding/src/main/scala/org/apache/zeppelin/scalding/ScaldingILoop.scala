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

package org.apache.zeppelin.scalding;

import java.io.{BufferedReader, File, FileReader}

import scala.tools.nsc.GenericRunnerSettings
import scala.tools.nsc.interpreter.{ILoop, IR, JPrintWriter}


/**
 * A class providing Scalding specific commands for inclusion in the Scalding REPL.
 * This is currently forked from Scalding, but should eventually make it into Scalding itself:
 * https://github.com/twitter/scalding/blob/develop/scalding-repl/src/main/scala/com/twitter/scalding/ScaldingILoop.scala
 */
 class ScaldingILoop(in0: Option[BufferedReader], out: JPrintWriter)
    extends ILoop(in0, out) {
  // def this(in0: BufferedReader, out: JPrintWriter) = this(Some(in0), out)
  // def this() = this(None, new JPrintWriter(Console.out, true))

  settings = new GenericRunnerSettings({ s => echo(s) })

  override def printWelcome() {
    val fc = Console.YELLOW
    val wc = Console.RED
    def wrapFlames(s: String) = s.replaceAll("[()]+", fc + "$0" + wc)
    echo(fc +
      " (                                           \n" +
      " )\\ )            (   (                       \n" +
      "(()/(         )  )\\  )\\ )  (          (  (   \n" +
      " /(_)) (   ( /( ((_)(()/( )\\   (     )\\))(  \n" +
      "(_))   )\\  )( )) _   ((_)(( )  )\\ ) (( ))\\  \n".replaceAll("_", wc + "_" + fc) + wc +
      wrapFlames("/ __|((_) ((_)_ | |  _| | (_) _(_(( (_()_) \n") +
      wrapFlames("\\__ \\/ _| / _` || |/ _` | | || ' \\))/ _` \\  \n") +
      "|___/\\__| \\__,_||_|\\__,_| |_||_||_| \\__, |  \n" +
      "                                    |___/   ")
  }

  /**
   * Commands specific to the Scalding REPL. To define a new command use one of the following
   * factory methods:
   * - `LoopCommand.nullary` for commands that take no arguments
   * - `LoopCommand.cmd` for commands that take one string argument
   * - `LoopCommand.varargs` for commands that take multiple string arguments
   */
  private val scaldingCommands: List[LoopCommand] = List()

  /**
   * Change the shell prompt to read scalding&gt;
   *
   * @return a prompt string to use for this REPL.
   */
  override def prompt: String = Console.BLUE + "\nscalding> " + Console.RESET

  private[this] def addImports(ids: String*): IR.Result =
    if (ids.isEmpty) IR.Success
    else intp.interpret("import " + ids.mkString(", "))

  /**
   * Search for files with the given name in all directories from current directory
   * up to root.
   */
  private def findAllUpPath(filename: String): List[File] =
    Iterator.iterate(System.getProperty("user.dir"))(new File(_).getParent)
      .takeWhile(_ != "/")
      .flatMap(new File(_).listFiles.filter(_.toString.endsWith(filename)))
      .toList

  /**
   * Gets the list of commands that this REPL supports.
   *
   * @return a list of the command supported by this REPL.
   */
  override def commands: List[LoopCommand] = super.commands ++ scaldingCommands

  protected def imports: List[String] = List(
    "com.twitter.scalding._",
    "com.twitter.scalding.ReplImplicits._",
    "com.twitter.scalding.ReplImplicitContext._",
    "com.twitter.scalding.ReplState._")

  override def createInterpreter() {
    super.createInterpreter()
    intp.beQuietDuring {
      addImports(imports: _*)

      settings match {
        case s: GenericRunnerSettings =>
          findAllUpPath(".scalding_repl").reverse.foreach {
            f => s.loadfiles.appendToValue(f.toString)
          }
        case _ => ()
      }
    }
  }
}
