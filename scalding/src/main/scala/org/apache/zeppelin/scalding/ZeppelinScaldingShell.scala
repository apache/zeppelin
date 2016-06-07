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

package org.apache.zeppelin.scalding

import com.twitter.scalding._
import com.twitter.scalding.typed.TypedPipe
import scala.tools.nsc.{GenericRunnerCommand}
import scala.tools.nsc.interpreter._

/**
  * TBD
  */
object ZeppelinScaldingShell extends BaseScaldingShell {

  override def replState = ZeppelinReplState

  def getRepl(args: Array[String], out: JPrintWriter): ScaldingILoop = {

    val argsExpanded = ExpandLibJarsGlobs(args)
    val ShellArgs(cfg, mode, cmdArgs) = parseModeArgs(argsExpanded)

    // Process command line arguments into a settings object, and use that to start the REPL.
    // We ignore params we don't care about - hence error function is empty
    val command = new GenericRunnerCommand(cmdArgs, _ => ())

    // inherit defaults for embedded interpretter (needed for running with SBT)
    // (TypedPipe chosen arbitrarily, just needs to be something representative)
    command.settings.embeddedDefaults[TypedPipe[String]]

    // if running from the assembly, need to explicitly tell it to use java classpath
    if (args.contains("--repl")) command.settings.usejavacp.value = true

    command.settings.classpath.append(System.getProperty("java.class.path"))

    // Force the repl to be synchronous, so all cmds are executed in the same thread
    command.settings.Yreplsync.value = true

    val repl = new ZeppelinScaldingILoop(None, out)
    scaldingREPL = Some(repl)
    replState.mode = mode
    replState.customConfig = replState.customConfig ++ (mode match {
      case _: HadoopMode => cfg
      case _ => Config.empty
    })

    // if in Hdfs mode, store the mode to enable switching between Local and Hdfs
    mode match {
      case m @ Hdfs(_, _) => replState.storedHdfsMode = Some(m)
      case _ => ()
    }

    repl.settings = command.settings
    return repl;

  }

}
