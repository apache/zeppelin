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

import org.apache.spark.SparkConf
import org.apache.spark.repl.SparkILoop
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream
import org.apache.zeppelin.interpreter.{InterpreterContext, InterpreterException, InterpreterGroup, InterpreterResult}
import org.apache.zeppelin.kotlin.KotlinInterpreter
import org.slf4j.{Logger, LoggerFactory}

import java.io.{BufferedReader, File, PrintStream}
import java.net.URLClassLoader
import java.nio.file.Paths
import java.util.Properties
import scala.collection.JavaConverters._
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._


/**
 * SparkInterpreter for scala-2.11.
 * It only works for Spark 2.x, as Spark 3.x doesn't support scala-2.11
 */
class SparkScala211Interpreter(conf: SparkConf,
                               depFiles: java.util.List[String],
                               properties: Properties,
                               interpreterGroup: InterpreterGroup,
                               sparkInterpreterClassLoader: URLClassLoader,
                               outputDir: File) extends AbstractSparkScalaInterpreter(conf, properties, depFiles) {

  private lazy val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  private var sparkILoop: SparkILoop = _
  private var scalaCompletion: Completion = _
  private val interpreterOutput = new InterpreterOutputStream(LOGGER)
  private val sparkMaster: String = conf.get(SparkStringConstants.MASTER_PROP_NAME,
    SparkStringConstants.DEFAULT_MASTER_VALUE)

  override def interpret(code: String, context: InterpreterContext): InterpreterResult = {

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
      case InterpreterResult.Code.INCOMPLETE => new InterpreterResult(lastStatus, "Incomplete expression")
      case _ => new InterpreterResult(lastStatus)
    }
  }

  override def completion(buf: String,
                          cursor: Int,
                          context: InterpreterContext): java.util.List[InterpreterCompletion] = {
    scalaCompletion.completer().complete(buf.substring(0, cursor), cursor)
      .candidates
      .map(e => new InterpreterCompletion(e, e, null))
      .asJava
  }

  private def bind(name: String, tpe: String, value: Object, modifier: List[String]): Unit = {
    sparkILoop.beQuietDuring {
      val result = sparkILoop.bind(name, tpe, value, modifier)
      if (result != IR.Success) {
        throw new RuntimeException("Fail to bind variable: " + name)
      }
    }
  }

  override def bind(name: String,
                    tpe: String,
                    value: Object,
                    modifier: java.util.List[String]): Unit =
    bind(name, tpe, value, modifier.asScala.toList)

  private def scalaInterpret(code: String): scala.tools.nsc.interpreter.IR.Result =
    sparkILoop.interpret(code)

  @throws[InterpreterException]
  def scalaInterpretQuietly(code: String): Unit = {
    scalaInterpret(code) match {
      case scala.tools.nsc.interpreter.Results.Success =>
      // do nothing
      case scala.tools.nsc.interpreter.Results.Error =>
        throw new InterpreterException("Fail to run code: " + code)
      case scala.tools.nsc.interpreter.Results.Incomplete =>
        throw new InterpreterException("Incomplete code: " + code)
    }
  }

  override def getScalaShellClassLoader: ClassLoader = {
    sparkILoop.classLoader
  }

  // Used by KotlinSparkInterpreter
  override def delegateInterpret(interpreter: KotlinInterpreter,
                                 code: String,
                                 context: InterpreterContext): InterpreterResult = {
    val out = context.out
    val newOut = if (out != null) new PrintStream(out) else null
    Console.withOut(newOut) {
      interpreter.interpret(code, context)
    }
  }

  override def close(): Unit = {
    super.close()
    if (sparkILoop != null) {
      sparkILoop.closeInterpreter()
    }
  }

  override def createSparkILoop(): Unit = {
    if (sparkMaster == "yarn-client") {
      System.setProperty("SPARK_YARN_MODE", "true")
    }

    LOGGER.info("Scala shell repl output dir: " + outputDir.getAbsolutePath)
    conf.set("spark.repl.class.outputDir", outputDir.getAbsolutePath)
    val target = conf.get("spark.repl.target", "jvm-1.6")
    val settings = new Settings()
    settings.processArguments(List("-Yrepl-class-based",
      "-Yrepl-outdir", s"${outputDir.getAbsolutePath}"), true)
    settings.embeddedDefaults(sparkInterpreterClassLoader)
    settings.usejavacp.value = true
    settings.target.value = target
    val userJars = getUserJars()
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

    val in0 = getField(sparkILoop, "scala$tools$nsc$interpreter$ILoop$$in0").asInstanceOf[Option[BufferedReader]]
    val reader = in0.fold(sparkILoop.chooseReader(settings))(r => SimpleReader(r, replOut, interactive = true))

    sparkILoop.in = reader
    sparkILoop.initializeSynchronous()
    SparkScala211Interpreter.loopPostInit(this)
    this.scalaCompletion = reader.completion
  }

  override def createZeppelinContext(): Unit = {
    val sparkShims = SparkShims.getInstance(sc.version, properties, sparkSession)
    sparkShims.setupSparkListener(sc.master, sparkUrl, InterpreterContext.get)
    z = new SparkZeppelinContext(sc, sparkShims,
      interpreterGroup.getInterpreterHookRegistry,
      properties.getProperty("zeppelin.spark.maxResult", "1000").toInt)
    bind("z", z.getClass.getCanonicalName, z, List("""@transient"""))
  }

  private def getField(obj: Object, name: String): Object = {
    val field = obj.getClass.getField(name)
    field.setAccessible(true)
    field.get(obj)
  }

  private def callMethod(obj: Object, name: String,
                         parameterTypes: Array[Class[_]],
                         parameters: Array[Object]): Object = {
    val method = obj.getClass.getMethod(name, parameterTypes: _ *)
    method.setAccessible(true)
    method.invoke(obj, parameters: _ *)
  }

  private def getUserJars(): Seq[String] = {
    var classLoader = Thread.currentThread().getContextClassLoader
    var extraJars = Seq.empty[String]
    while (classLoader != null) {
      if (classLoader.getClass.getCanonicalName ==
        "org.apache.spark.util.MutableURLClassLoader") {
        extraJars = classLoader.asInstanceOf[URLClassLoader].getURLs()
          // Check if the file exists.
          .filter { u => u.getProtocol == "file" && new File(u.getPath).isFile }
          // Some bad spark packages depend on the wrong version of scala-reflect. Blacklist it.
          .filterNot {
            u => Paths.get(u.toURI).getFileName.toString.contains("org.scala-lang_scala-reflect")
          }
          .map(url => url.toString).toSeq
        classLoader = null
      } else {
        classLoader = classLoader.getParent
      }
    }

    extraJars ++= sparkInterpreterClassLoader.getURLs().map(_.getPath())
    LOGGER.debug("User jar for spark repl: " + extraJars.mkString(","))
    extraJars
  }
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
    import scala.reflect.{classTag, io}

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
