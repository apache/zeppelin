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
import java.net.URLClassLoader
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger

import org.apache.spark.sql.SQLContext
import org.apache.spark.{JobProgressUtil, SparkConf, SparkContext}
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream
import org.apache.zeppelin.interpreter.{InterpreterContext, InterpreterResult}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.tools.nsc.interpreter.Completion.ScalaCompleter
import scala.util.control.NonFatal

/**
  * Base class for different scala versions of SparkInterpreter. It should be
  * binary compatible between multiple scala versions.
  *
  * @param conf
  * @param depFiles
  */
abstract class BaseSparkScalaInterpreter(val conf: SparkConf,
                                         val depFiles: java.util.List[String],
                                         val printReplOutput: java.lang.Boolean) {

  protected lazy val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  private val isTest = conf.getBoolean("zeppelin.spark.test", false)

  protected var sc: SparkContext = _

  protected var sqlContext: SQLContext = _

  protected var sparkSession: Object = _

  protected var sparkHttpServer: Object = _

  protected var sparkUrl: String = _

  protected var scalaCompleter: ScalaCompleter = _

  protected val interpreterOutput: InterpreterOutputStream


  protected def open(): Unit = {
    /* Required for scoped mode.
     * In scoped mode multiple scala compiler (repl) generates class in the same directory.
     * Class names is not randomly generated and look like '$line12.$read$$iw$$iw'
     * Therefore it's possible to generated class conflict(overwrite) with other repl generated
     * class.
     *
     * To prevent generated class name conflict,
     * change prefix of generated class name from each scala compiler (repl) instance.
     *
     * In Spark 2.x, REPL generated wrapper class name should compatible with the pattern
     * ^(\$line(?:\d+)\.\$read)(?:\$\$iw)+$
     *
     * As hashCode() can return a negative integer value and the minus character '-' is invalid
     * in a package name we change it to a numeric value '0' which still conforms to the regexp.
     *
     */
    System.setProperty("scala.repl.name.line", ("$line" + this.hashCode).replace('-', '0'))

    BaseSparkScalaInterpreter.sessionNum.incrementAndGet()
  }

  def interpret(code: String, context: InterpreterContext): InterpreterResult = {

    val originalOut = System.out

    def _interpret(code: String): scala.tools.nsc.interpreter.Results.Result = {
      Console.withOut(interpreterOutput) {
        System.setOut(Console.out)
        interpreterOutput.setInterpreterOutput(context.out)
        interpreterOutput.ignoreLeadingNewLinesFromScalaReporter()
        context.out.clear()

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

    val lastStatus = _interpret(code) match {
      case scala.tools.nsc.interpreter.IR.Success =>
        InterpreterResult.Code.SUCCESS
      case scala.tools.nsc.interpreter.IR.Error =>
        InterpreterResult.Code.ERROR
      case scala.tools.nsc.interpreter.IR.Incomplete =>
        InterpreterResult.Code.INCOMPLETE
    }

    new InterpreterResult(lastStatus)
  }

  protected def interpret(code: String): InterpreterResult =
    interpret(code, InterpreterContext.get())

  protected def scalaInterpret(code: String): scala.tools.nsc.interpreter.IR.Result

  protected def completion(buf: String,
                           cursor: Int,
                           context: InterpreterContext): java.util.List[InterpreterCompletion] = {
    val completions = scalaCompleter.complete(buf.substring(0, cursor), cursor).candidates
      .map(e => new InterpreterCompletion(e, e, null))
    scala.collection.JavaConversions.seqAsJavaList(completions)
  }

  protected def getProgress(jobGroup: String, context: InterpreterContext): Int = {
    JobProgressUtil.progress(sc, jobGroup)
  }

  protected def bind(name: String, tpe: String, value: Object, modifier: List[String]): Unit

  // for use in java side
  protected def bind(name: String,
                     tpe: String,
                     value: Object,
                     modifier: java.util.List[String]): Unit =
    bind(name, tpe, value, modifier.asScala.toList)

  protected def close(): Unit = {
    if (BaseSparkScalaInterpreter.sessionNum.decrementAndGet() == 0) {
      if (sc != null) {
        sc.stop()
      }else {
        System.exit(1)
      }
      if (sparkHttpServer != null) {
        sparkHttpServer.getClass.getMethod("stop").invoke(sparkHttpServer)
      }
      sc = null
      sqlContext = null
      if (sparkSession != null) {
        sparkSession.getClass.getMethod("stop").invoke(sparkSession)
        sparkSession = null
      }
    }
  }

  protected def createSparkContext(): Unit = {
    if (isSparkSessionPresent()) {
      spark2CreateContext()
    } else {
      spark1CreateContext()
    }
  }

  private def spark1CreateContext(): Unit = {
    this.sc = SparkContext.getOrCreate(conf)
    if (!isTest) {
      interpreterOutput.write("Created SparkContext.\n".getBytes())
    }
    getUserFiles().foreach(file => sc.addFile(file))

    sc.getClass.getMethod("ui").invoke(sc).asInstanceOf[Option[_]] match {
      case Some(webui) =>
        sparkUrl = webui.getClass.getMethod("appUIAddress").invoke(webui).asInstanceOf[String]
      case None =>
    }

    val hiveSiteExisted: Boolean =
      Thread.currentThread().getContextClassLoader.getResource("hive-site.xml") != null
    val hiveEnabled = conf.getBoolean("spark.useHiveContext", false)
    if (hiveEnabled && hiveSiteExisted) {
      sqlContext = Class.forName("org.apache.spark.sql.hive.HiveContext")
        .getConstructor(classOf[SparkContext]).newInstance(sc).asInstanceOf[SQLContext]
      if (!isTest) {
        interpreterOutput.write("Created sql context (with Hive support).\n".getBytes())
      }
    } else {
      if (hiveEnabled && !hiveSiteExisted && !isTest) {
        interpreterOutput.write(("spark.useHiveContext is set as true but no hive-site.xml" +
          " is found in classpath, so zeppelin will fallback to SQLContext.\n").getBytes())
      }
      sqlContext = Class.forName("org.apache.spark.sql.SQLContext")
        .getConstructor(classOf[SparkContext]).newInstance(sc).asInstanceOf[SQLContext]
      if (!isTest) {
        interpreterOutput.write("Created sql context.\n".getBytes())
      }
    }

    bind("sc", "org.apache.spark.SparkContext", sc, List("""@transient"""))
    bind("sqlContext", sqlContext.getClass.getCanonicalName, sqlContext, List("""@transient"""))

    interpret("import org.apache.spark.SparkContext._")
    interpret("import sqlContext.implicits._")
    interpret("import sqlContext.sql")
    interpret("import org.apache.spark.sql.functions._")
    // print empty string otherwise the last statement's output of this method
    // (aka. import org.apache.spark.sql.functions._) will mix with the output of user code
    interpret("print(\"\")")
  }

  private def spark2CreateContext(): Unit = {
    val sparkClz = Class.forName("org.apache.spark.sql.SparkSession$")
    val sparkObj = sparkClz.getField("MODULE$").get(null)

    val builderMethod = sparkClz.getMethod("builder")
    val builder = builderMethod.invoke(sparkObj)
    builder.getClass.getMethod("config", classOf[SparkConf]).invoke(builder, conf)

    if (conf.get("spark.sql.catalogImplementation", "in-memory").toLowerCase == "hive"
      || conf.get("spark.useHiveContext", "false").toLowerCase == "true") {
      val hiveSiteExisted: Boolean =
        Thread.currentThread().getContextClassLoader.getResource("hive-site.xml") != null
      val hiveClassesPresent =
        sparkClz.getMethod("hiveClassesArePresent").invoke(sparkObj).asInstanceOf[Boolean]
      if (hiveSiteExisted && hiveClassesPresent) {
        builder.getClass.getMethod("enableHiveSupport").invoke(builder)
        sparkSession = builder.getClass.getMethod("getOrCreate").invoke(builder)
        if (!isTest) {
          interpreterOutput.write("Created Spark session (with Hive support).\n".getBytes())
        }
      } else {
        if (!hiveClassesPresent && !isTest) {
          interpreterOutput.write(
            "Hive support can not be enabled because spark is not built with hive\n".getBytes)
        }
        if (!hiveSiteExisted && !isTest) {
          interpreterOutput.write(
            "Hive support can not be enabled because no hive-site.xml found\n".getBytes)
        }
        sparkSession = builder.getClass.getMethod("getOrCreate").invoke(builder)
        if (!isTest) {
          interpreterOutput.write("Created Spark session.\n".getBytes())
        }
      }
    } else {
      sparkSession = builder.getClass.getMethod("getOrCreate").invoke(builder)
      if (!isTest) {
        interpreterOutput.write("Created Spark session.\n".getBytes())
      }
    }

    sc = sparkSession.getClass.getMethod("sparkContext").invoke(sparkSession)
      .asInstanceOf[SparkContext]
    getUserFiles().foreach(file => sc.addFile(file))
    sqlContext = sparkSession.getClass.getMethod("sqlContext").invoke(sparkSession)
      .asInstanceOf[SQLContext]
    sc.getClass.getMethod("uiWebUrl").invoke(sc).asInstanceOf[Option[String]] match {
      case Some(url) => sparkUrl = url
      case None =>
    }

    bind("spark", sparkSession.getClass.getCanonicalName, sparkSession, List("""@transient"""))
    bind("sc", "org.apache.spark.SparkContext", sc, List("""@transient"""))
    bind("sqlContext", "org.apache.spark.sql.SQLContext", sqlContext, List("""@transient"""))

    interpret("import org.apache.spark.SparkContext._")
    interpret("import spark.implicits._")
    interpret("import spark.sql")
    interpret("import org.apache.spark.sql.functions._")
    // print empty string otherwise the last statement's output of this method
    // (aka. import org.apache.spark.sql.functions._) will mix with the output of user code
    interpret("print(\"\")")
  }

  private def isSparkSessionPresent(): Boolean = {
    try {
      Class.forName("org.apache.spark.sql.SparkSession")
      true
    } catch {
      case _: ClassNotFoundException | _: NoClassDefFoundError => false
    }
  }

  protected def getField(obj: Object, name: String): Object = {
    val field = obj.getClass.getField(name)
    field.setAccessible(true)
    field.get(obj)
  }

  protected def getDeclareField(obj: Object, name: String): Object = {
    val field = obj.getClass.getDeclaredField(name)
    field.setAccessible(true)
    field.get(obj)
  }

  protected def setDeclaredField(obj: Object, name: String, value: Object): Unit = {
    val field = obj.getClass.getDeclaredField(name)
    field.setAccessible(true)
    field.set(obj, value)
  }

  protected def callMethod(obj: Object, name: String): Object = {
    callMethod(obj, name, Array.empty[Class[_]], Array.empty[Object])
  }

  protected def callMethod(obj: Object, name: String,
                           parameterTypes: Array[Class[_]],
                           parameters: Array[Object]): Object = {
    val method = obj.getClass.getMethod(name, parameterTypes: _ *)
    method.setAccessible(true)
    method.invoke(obj, parameters: _ *)
  }

  protected def startHttpServer(outputDir: File): Option[(Object, String)] = {
    try {
      val httpServerClass = Class.forName("org.apache.spark.HttpServer")
      val securityManager = {
        val constructor = Class.forName("org.apache.spark.SecurityManager")
          .getConstructor(classOf[SparkConf])
        constructor.setAccessible(true)
        constructor.newInstance(conf).asInstanceOf[Object]
      }
      val httpServerConstructor = httpServerClass
        .getConstructor(classOf[SparkConf],
          classOf[File],
          Class.forName("org.apache.spark.SecurityManager"),
          classOf[Int],
          classOf[String])
      httpServerConstructor.setAccessible(true)
      // Create Http Server
      val port = conf.getInt("spark.replClassServer.port", 0)
      val server = httpServerConstructor
        .newInstance(conf, outputDir, securityManager, new Integer(port), "HTTP server")
        .asInstanceOf[Object]

      // Start Http Server
      val startMethod = server.getClass.getMethod("start")
      startMethod.setAccessible(true)
      startMethod.invoke(server)

      // Get uri of this Http Server
      val uriMethod = server.getClass.getMethod("uri")
      uriMethod.setAccessible(true)
      val uri = uriMethod.invoke(server).asInstanceOf[String]
      Some((server, uri))
    } catch {
      // Spark 2.0+ removed HttpServer, so return null instead.
      case NonFatal(e) =>
        None
    }
  }

  protected def getUserJars(): Seq[String] = {
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
    LOGGER.debug("User jar for spark repl: " + extraJars.mkString(","))
    extraJars
  }

  protected def getUserFiles(): Seq[String] = {
    depFiles.asScala.filter(!_.endsWith(".jar"))
  }
}

object BaseSparkScalaInterpreter {
  val sessionNum = new AtomicInteger(0)
}
