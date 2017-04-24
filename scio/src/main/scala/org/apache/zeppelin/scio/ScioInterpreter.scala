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

import java.beans.Introspector
import java.io.PrintStream
import java.util.Properties

import com.google.cloud.dataflow.sdk.options.{PipelineOptions, PipelineOptionsFactory}
import com.google.cloud.dataflow.sdk.runners.inprocess.InProcessPipelineRunner
import com.spotify.scio.repl.{ScioILoop, ScioReplClassLoader}
import org.apache.zeppelin.interpreter.Interpreter.FormType
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream
import org.apache.zeppelin.interpreter.{Interpreter, InterpreterContext, InterpreterResult}
import org.slf4j.LoggerFactory

import scala.reflect.io.File
import scala.tools.nsc.GenericRunnerCommand
import scala.tools.nsc.interpreter.JPrintWriter
import scala.tools.nsc.util.ClassPath

/**
 * Scio interpreter for Zeppelin.
 *
 * <ul>
 * <li>{@code zeppelin.scio.argz} - Scio interpreter wide arguments</li>
 * <li>{@code zeppelin.scio.maxResult} - Max number of SCollection results to display.</li>
 * </ul>
 *
 * <p>
 * How to use: <br/>
 * {@code
 *  $beam.scio
 *  val (sc, args) = ContextAndArgs(argz)
 *  sc.parallelize(Seq("foo", "foo", "bar")).countByValue.closeAndDisplay()
 * }
 * </p>
 *
 */

class ScioInterpreter(property: Properties) extends Interpreter(property) {
  private val logger = LoggerFactory.getLogger(classOf[ScioInterpreter])
  private var REPL: ScioILoop = _

  val innerOut = new InterpreterOutputStream(logger)

  override def open(): Unit = {
    val argz = Option(getProperty("zeppelin.scio.argz"))
      .getOrElse(s"--runner=${classOf[InProcessPipelineRunner].getSimpleName}")
      .split(" ")
      .map(_.trim)
      .filter(_.nonEmpty)
      .toList

    // Process command line arguments into a settings object, and use that to start the REPL.
    // We ignore params we don't care about - hence error function is empty
    val command = new GenericRunnerCommand(argz, _ => ())
    val settings = command.settings

    settings.classpath.append(System.getProperty("java.class.path"))
    settings.usejavacp.value = true

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

    val thisJar = this.getClass.getProtectionDomain.getCodeSource.getLocation.getPath
    // In some cases this may be `target/classes`
    if(thisJar.endsWith(".jar")) settings.plugin.appendToValue(thisJar)

    ClassPath
      .split(settings.classpath.value)
      .find(File(_).name.startsWith("paradise_"))
      .foreach(settings.plugin.appendToValue)

    // Force the repl to be synchronous, so all cmds are executed in the same thread
    settings.Yreplsync.value = true

    val jars = ClassPath.split(settings.classpath.value)
      .flatMap(ClassPath.specToURL)
      .toArray

    val scioClassLoader = new ScioReplClassLoader(
      jars ++ classLoaderURLs(Thread.currentThread().getContextClassLoader),
      null,
      Thread.currentThread.getContextClassLoader)

    val (dfArgs, _) = parseAndPartitionArgs(argz)

    REPL = new ScioILoop(scioClassLoader, dfArgs, None, new JPrintWriter(innerOut))
    scioClassLoader.setRepl(REPL)

    // Set classloader chain - expose top level abstract class loader down
    // the chain to allow for readObject and latestUserDefinedLoader
    // See https://gist.github.com/harrah/404272
    settings.embeddedDefaults(scioClassLoader)

    // No need for bigquery dumps
    sys.props("bigquery.plugin.disable.dump") = true.toString

    REPL.settings_=(settings)
    REPL.createInterpreter()
    REPL.interpret(s"""val argz = Array("${argz.mkString("\", \"")}")""")
    REPL.interpret("import org.apache.zeppelin.scio.DisplaySCollectionImplicits._")
    REPL.interpret("import org.apache.zeppelin.scio.DisplayTapImplicits._")
    REPL.interpret("import org.apache.zeppelin.scio.ContextAndArgs")
  }

  private def parseAndPartitionArgs(args: List[String]): (List[String], List[String]) = {
    import scala.collection.JavaConverters._
    // Extract --pattern of all registered derived types of PipelineOptions
    val classes = PipelineOptionsFactory.getRegisteredOptions.asScala + classOf[PipelineOptions]
    val optPatterns = classes.flatMap { cls =>
      cls.getMethods.flatMap { m =>
        val n = m.getName
        if ((!n.startsWith("get") && !n.startsWith("is")) ||
          m.getParameterTypes.nonEmpty || m.getReturnType == classOf[Unit]) None
        else Some(Introspector.decapitalize(n.substring(if (n.startsWith("is")) 2 else 3)))
      }.map(s => s"--$s($$|=)".r)
    }

    // Split cmdlineArgs into 2 parts, optArgs for PipelineOptions and appArgs for Args
    args.partition(arg => optPatterns.exists(_.findFirstIn(arg).isDefined))
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
      import scala.tools.nsc.interpreter.Results._
      REPL.interpret(code) match {
        case Success => {
          logger.debug(s"Successfully executed `$code` in $paragraphId")
          new InterpreterResult(InterpreterResult.Code.SUCCESS)
        }
        case Error => {
          logger.error(s"Error executing `$code` in $paragraphId")
          new InterpreterResult(InterpreterResult.Code.ERROR, "Interpreter error")
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

  override def getFormType: FormType = FormType.NATIVE

  override def getProgress(context: InterpreterContext): Int = {
    // not implemented
    0
  }

}
