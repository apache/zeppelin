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
import java.util.Properties
import org.apache.spark.SparkConf
import org.apache.spark.repl.SparkILoop
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream
import org.apache.zeppelin.interpreter.{InterpreterContext, InterpreterGroup, InterpreterResult}
import org.slf4j.LoggerFactory
import org.slf4j.Logger

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._

/**
  * SparkInterpreter for scala-2.12
  */
class SparkScala212Interpreter(override val conf: SparkConf,
                               override val depFiles: java.util.List[String],
                               override val properties: Properties,
                               override val interpreterGroup: InterpreterGroup,
                               override val sparkInterpreterClassLoader: URLClassLoader,
                               val outputDir: File)
  extends BaseSparkScalaInterpreter(conf, depFiles, properties, interpreterGroup, sparkInterpreterClassLoader) {

  lazy override val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  private var sparkILoop: SparkILoop = _

  private var scalaCompletion: Completion = _

  override val interpreterOutput = new InterpreterOutputStream(LOGGER)

  override def open(): Unit = {
    super.open()
    if (sparkMaster == "yarn-client") {
      System.setProperty("SPARK_YARN_MODE", "true")
    }

    LOGGER.info("Scala shell repl output dir: " + outputDir.getAbsolutePath)
    conf.set("spark.repl.class.outputDir", outputDir.getAbsolutePath)

    val settings = new Settings()
    settings.processArguments(List("-Yrepl-class-based",
      "-Yrepl-outdir", s"${outputDir.getAbsolutePath}"), true)
    settings.embeddedDefaults(sparkInterpreterClassLoader)
    settings.usejavacp.value = true
    this.userJars = getUserJars()
    LOGGER.info("UserJars: " + userJars.mkString(File.pathSeparator))
    settings.classpath.value = userJars.mkString(File.pathSeparator)

    val printReplOutput = properties.getProperty("zeppelin.spark.printREPLOutput", "true").toBoolean
    val replOut = if (printReplOutput) {
      new JPrintWriter(interpreterOutput, true)
    } else {
      new JPrintWriter(Console.out, true)
    }
    sparkILoop = new SparkILoop(None, replOut)
    sparkILoop.settings = settings
    sparkILoop.createInterpreter()
    val in0 = getDeclareField(sparkILoop, "in0").asInstanceOf[Option[BufferedReader]]
    val reader = in0.fold(sparkILoop.chooseReader(settings))(r => SimpleReader(r, replOut, interactive = true))

    sparkILoop.in = reader
    sparkILoop.initializeSynchronous()
    SparkScala212Interpreter.loopPostInit(this)
    this.scalaCompletion = reader.completion

    createSparkContext()

    scalaInterpret("import org.apache.spark.SparkContext._")
    scalaInterpret("import spark.implicits._")
    scalaInterpret("import spark.sql")
    scalaInterpret("import org.apache.spark.sql.functions._")
    // print empty string otherwise the last statement's output of this method
    // (aka. import org.apache.spark.sql.functions._) will mix with the output of user code
    scalaInterpret("print(\"\")")

    createZeppelinContext()
  }

  def interpret(code: String, context: InterpreterContext): InterpreterResult = {

    val originalOut = System.out
    val printREPLOutput = context.getStringLocalProperty("printREPLOutput", "true").toBoolean

    def _interpret(code: String): scala.tools.nsc.interpreter.Results.Result = {
      Console.withOut(interpreterOutput) {
        System.setOut(Console.out)
        if (printREPLOutput) {
          interpreterOutput.setInterpreterOutput(context.out)
        } else {
          interpreterOutput.setInterpreterOutput(null)
        }
        interpreterOutput.ignoreLeadingNewLinesFromScalaReporter()

        val status = scalaInterpret(code) match {
          case success@scala.tools.nsc.interpreter.IR.Success =>
            success
          case scala.tools.nsc.interpreter.IR.Error =>
            val errorMsg = new String(interpreterOutput.getInterpreterOutput.toByteArray)
            if (errorMsg.contains("value toDF is not a member of org.apache.spark.rdd.RDD") ||
              errorMsg.contains("value toDS is not a member of org.apache.spark.rdd.RDD")) {
              // prepend "import sqlContext.implicits._" due to
              // https://issues.scala-lang.org/browse/SI-6649
              context.out.clear()
              scalaInterpret("import sqlContext.implicits._\n" + code)
            } else {
              scala.tools.nsc.interpreter.IR.Error
            }
          case scala.tools.nsc.interpreter.IR.Incomplete =>
            // add print("") at the end in case the last line is comment which lead to INCOMPLETE
            scalaInterpret(code + "\nprint(\"\")")
        }
        context.out.flush()
        status
      }
    }
    // reset the java stdout
    System.setOut(originalOut)

    context.out.write("")
    val lastStatus = _interpret(code) match {
      case scala.tools.nsc.interpreter.IR.Success =>
        InterpreterResult.Code.SUCCESS
      case scala.tools.nsc.interpreter.IR.Error =>
        InterpreterResult.Code.ERROR
      case scala.tools.nsc.interpreter.IR.Incomplete =>
        InterpreterResult.Code.INCOMPLETE
    }

    lastStatus match {
      case InterpreterResult.Code.INCOMPLETE => new InterpreterResult( lastStatus, "Incomplete expression" )
      case _ => new InterpreterResult(lastStatus)
    }
  }

  protected override def completion(buf: String,
                           cursor: Int,
                           context: InterpreterContext): java.util.List[InterpreterCompletion] = {
    val completions = scalaCompletion.complete(buf.substring(0, cursor), cursor).candidates
      .map(e => new InterpreterCompletion(e, e, null))
    scala.collection.JavaConversions.seqAsJavaList(completions)
  }

  protected def bind(name: String, tpe: String, value: Object, modifier: List[String]): Unit = {
    sparkILoop.beQuietDuring {
      val result = sparkILoop.bind(name, tpe, value, modifier)
      if (result != IR.Success) {
        throw new RuntimeException("Fail to bind variable: " + name)
      }
    }
  }


  override def close(): Unit = {
    super.close()
    if (sparkILoop != null) {
      sparkILoop.closeInterpreter()
      sparkILoop = null
    }
  }

  def scalaInterpret(code: String): scala.tools.nsc.interpreter.IR.Result =
    sparkILoop.interpret(code)

  override def getScalaShellClassLoader: ClassLoader = {
    sparkILoop.classLoader
  }

}

private object SparkScala212Interpreter {
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
  private def loopPostInit(interpreter: SparkScala212Interpreter): Unit = {
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
