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

package org.apache.zeppelin.scio

import java.io.PrintStream
import java.util
import java.util.Properties

import com.google.cloud.dataflow.sdk.runners.inprocess.InProcessPipelineRunner
import com.spotify.scio.repl.{ScioILoop, ScioReplClassLoader}
import org.apache.zeppelin.interpreter.Interpreter.FormType
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream
import org.apache.zeppelin.interpreter.{Interpreter, InterpreterContext, InterpreterResult}
import org.slf4j.LoggerFactory

import scala.reflect.io.File
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.JPrintWriter
import scala.tools.nsc.util.ClassPath

class ScioInterpreter(property: Properties) extends Interpreter(property) {
  private val logger = LoggerFactory.getLogger(classOf[ScioInterpreter])
  private var REPL: ScioILoop = null

  val innerOut = new InterpreterOutputStream(logger)

  override def open(): Unit = {
    val args: List[String] = Option(getProperty("args"))
      .getOrElse(s"--runner=${classOf[InProcessPipelineRunner].getSimpleName}")
      .split(" ")
      .map(_.trim)
      .toList

    val settings = new Settings()

    // For scala 2.10 - usejavacp
    if (scala.util.Properties.versionString.contains("2.10.")) {
      settings.classpath.append(System.getProperty("java.class.path"))
      settings.usejavacp.value = true
    }

    def classLoaderURLs(cl: ClassLoader): Array[java.net.URL] = cl match {
      case null => Array()
      case u: java.net.URLClassLoader => u.getURLs ++ classLoaderURLs(cl.getParent)
      case _ => classLoaderURLs(cl.getParent)
    }

    classLoaderURLs(Thread.currentThread().getContextClassLoader)
      .foreach(u => settings.classpath.append(u.getPath))

    // We have to make sure that scala macros are expandable. paradise plugin has to be added to
    // -Xplugin paths. In case of assembly - paradise is included in assembly jar - thus we add
    // itself to -Xplugin. If shell is started from sbt or classpath, paradise jar has to be in
    // classpath, we find it and add it to -Xplugin.

    // Repl assembly includes paradise's scalac-plugin.xml - required for BigQuery macro
    // There should be no harm if we keep this for sbt launch.
    val thisJar = this.getClass.getProtectionDomain.getCodeSource.getLocation.getPath
    // In some cases this may be `target/classes`
    if(thisJar.endsWith(".jar")) {
      settings.plugin.appendToValue(thisJar)
    }

    ClassPath.split(settings.classpath.value)
      .find(File(_).name.startsWith("paradise_"))
      .foreach(settings.plugin.appendToValue)

    // Force the repl to be synchronous, so all cmds are executed in the same thread
    settings.Yreplsync.value = true

    val scioClassLoader = new ScioReplClassLoader(
      ClassPath.toURLs(settings.classpath.value).toArray ++
        classLoaderURLs(Thread.currentThread().getContextClassLoader),
      null,
      Thread.currentThread.getContextClassLoader)

    REPL = new ScioILoop(scioClassLoader, args, None, new JPrintWriter(innerOut))
    scioClassLoader.setRepl(REPL)

    // Set classloader chain - expose top level abstract class loader down
    // the chain to allow for readObject and latestUserDefinedLoader
    // See https://gist.github.com/harrah/404272
    settings.embeddedDefaults(scioClassLoader)

    // No need for bigquery dumps
    sys.props("bigquery.plugin.disable.dump") = "true"

    REPL.settings_=(settings)
    REPL.createInterpreter()
  }

  override def close(): Unit = {
    logger.info("Closing Scio interpreter!")
    REPL.closeInterpreter()
  }

  override def interpret(code: String, context: InterpreterContext): InterpreterResult = {
    val paragraphId = context.getParagraphId

    val consoleOut = new PrintStream(innerOut)
    System.setOut(consoleOut)
    innerOut.setInterpreterOutput(context.out)

    try {
      import tools.nsc.interpreter.Results._
      REPL.interpret(code) match {
        case Success => {
          logger.debug(s"Successfully executed `$code` in $paragraphId")
          new InterpreterResult(InterpreterResult.Code.SUCCESS)
        }
        case Error => {
          logger.error(s"Error executing `$code` in $paragraphId")
          new InterpreterResult(InterpreterResult.Code.ERROR)
        }
        case Incomplete => {
          logger.warn(s"Code `$code` not complete in $paragraphId")
          new InterpreterResult(InterpreterResult.Code.INCOMPLETE, "Incomplete expression")
        }
      }
    } catch {
      case e: Exception =>
        logger.info("Interpreter exception", e)
        new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage)
    } finally {
      innerOut.flush()
      innerOut.setInterpreterOutput(null)
      consoleOut.flush()
    }
  }

  override def cancel(context: InterpreterContext): Unit = {
    // not implemented
  }

  override def getFormType: FormType = {
    FormType.NATIVE
  }

  override def getProgress(context: InterpreterContext): Int = {
    // not implemented
    42
  }

  override def completion(buf: String, cursor: Int): util.List[InterpreterCompletion] = {
    //TODO: implement, delegate?
    super.completion(buf, cursor)
  }

}
