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

package com.twitter.scalding


import java.io.{FileOutputStream, File}
import java.util.jar.{JarEntry, JarOutputStream}
import com.google.common.io.Files
import scala.tools.nsc.io._
import scala.tools.nsc.{GenericRunnerSettings, GenericRunnerCommand}
import scala.tools.nsc.interpreter._

/**
  * TBD
  */
object ZeppelinScaldingShell extends BaseScaldingShell {

  override def replState = ZeppelinReplState

  /**
    * An instance of the Scala REPL the user will interact with.
    */
  var zeppelinScaldingREPL: Option[ILoop] = None

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
    zeppelinScaldingREPL = Some(repl)
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

  /**
    * Creates a jar file in a temporary directory containing the code thus far compiled by the REPL.
    * Will use createReplCodeJar in base class once we are able to set scaldingREPL which is
    * currently private.
    * @return some file for the jar created, or `None` if the REPL is not running.
    */
  def createReplCodeJar1(): Option[File] = {
    zeppelinScaldingREPL.map { repl =>
      val virtualDirectory = repl.virtualDirectory
      val tempJar = new File(Files.createTempDir(),
        "scalding-repl-session-" + System.currentTimeMillis() + ".jar")
      createJar(virtualDirectory.asInstanceOf[VirtualDirectory], tempJar)
    }
  }

  /**
    * Creates a jar file from the classes contained in a virtual directory.
    *
    * @param virtualDirectory containing classes that should be added to the jar.
    * @param jarFile that will be written.
    * @return the jarFile specified and written.
    */
  private def createJar(virtualDirectory: VirtualDirectory, jarFile: File): File = {
    val jarStream = new JarOutputStream(new FileOutputStream(jarFile))
    try {
      addVirtualDirectoryToJar(virtualDirectory, "", jarStream)
    } finally {
      jarStream.close()
    }

    jarFile
  }

  /**
    * Add the contents of the specified virtual directory to a jar. This method will recursively
    * descend into subdirectories to add their contents.
    *
    * @param dir is a virtual directory whose contents should be added.
    * @param entryPath for classes found in the virtual directory.
    * @param jarStream for writing the jar file.
    */
  private def addVirtualDirectoryToJar(
                                        dir: VirtualDirectory,
                                        entryPath: String,
                                        jarStream: JarOutputStream) {
    dir.foreach { file =>
      if (file.isDirectory) {
        // Recursively descend into subdirectories, adjusting the package name as we do.
        val dirPath = entryPath + file.name + "/"
        val entry: JarEntry = new JarEntry(dirPath)
        jarStream.putNextEntry(entry)
        jarStream.closeEntry()
        addVirtualDirectoryToJar(file.asInstanceOf[VirtualDirectory], dirPath, jarStream)
      } else if (file.hasExtension("class")) {
        // Add class files as an entry in the jar file and write the class to the jar.
        val entry: JarEntry = new JarEntry(entryPath + file.name)
        jarStream.putNextEntry(entry)
        jarStream.write(file.toByteArray)
        jarStream.closeEntry()
      }
    }
  }

  /*
   * Only for testing
   */
  override def main(args: Array[String]) {
    val out = new JPrintWriter(Console.out, true)
    val repl = getRepl(args, out)
    val retval = repl.process(repl.settings)

    if (!retval) {
      sys.exit(1)
    }
  }
}