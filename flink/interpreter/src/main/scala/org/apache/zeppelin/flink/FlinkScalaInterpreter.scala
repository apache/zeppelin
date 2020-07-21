/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.flink

import java.io.{BufferedReader, File, IOException}
import java.net.{URL, URLClassLoader}
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.api.scala.{ExecutionEnvironment, FlinkILoop}
import org.apache.flink.client.program.ClusterClient
import org.apache.flink.configuration._
import org.apache.flink.core.execution.{JobClient, JobListener}
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.environment.{StreamExecutionEnvironmentFactory, StreamExecutionEnvironment => JStreamExecutionEnvironment}
import org.apache.flink.api.java.{ExecutionEnvironmentFactory, ExecutionEnvironment => JExecutionEnvironment}
import org.apache.flink.runtime.jobgraph.SavepointConfigOptions
import org.apache.flink.runtime.util.EnvironmentInformation
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.table.api.config.ExecutionConfigOptions
import org.apache.flink.table.api.{EnvironmentSettings, TableConfig, TableEnvironment}
import org.apache.flink.table.catalog.{CatalogManager, FunctionCatalog}
import org.apache.flink.table.catalog.hive.HiveCatalog
import org.apache.flink.table.functions.{AggregateFunction, ScalarFunction, TableAggregateFunction, TableFunction}
import org.apache.flink.table.module.ModuleManager
import org.apache.flink.table.module.hive.HiveModule
import org.apache.flink.yarn.cli.FlinkYarnSessionCli
import org.apache.zeppelin.flink.util.DependencyUtils
import org.apache.zeppelin.flink.FlinkShell._
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream
import org.apache.zeppelin.interpreter.{InterpreterContext, InterpreterException, InterpreterHookRegistry, InterpreterResult}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.Completion.ScalaCompleter
import scala.tools.nsc.interpreter.{JPrintWriter, SimpleReader}

/**
 * It instantiate flink scala shell and create env, senv, btenv, stenv.
 *
 * @param properties
 */
class FlinkScalaInterpreter(val properties: Properties) {

  private lazy val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  private var flinkILoop: FlinkILoop = _
  private var cluster: Option[ClusterClient[_]] = _

  private var scalaCompleter: ScalaCompleter = _
  private val interpreterOutput = new InterpreterOutputStream(LOGGER)
  private var configuration: Configuration = _

  private var mode: ExecutionMode.Value = _

  private var tblEnvFactory: TableEnvFactory = _
  private var benv: ExecutionEnvironment = _
  private var senv: StreamExecutionEnvironment = _

  // TableEnvironment of blink planner
  private var btenv: TableEnvironment = _
  private var stenv: TableEnvironment = _

  // TableEnvironment of flink planner
  private var btenv_2: TableEnvironment = _
  private var stenv_2: TableEnvironment = _

  // PyFlink depends on java version of TableEnvironment,
  // so need to create java version of TableEnvironment
  private var java_btenv: TableEnvironment = _
  private var java_stenv: TableEnvironment = _

  private var java_btenv_2: TableEnvironment = _
  private var java_stenv_2: TableEnvironment = _

  private var z: FlinkZeppelinContext = _
  private var flinkVersion: FlinkVersion = _
  private var flinkShims: FlinkShims = _
  private var jmWebUrl: String = _
  private var replacedJMWebUrl: String = _
  private var jobManager: JobManager = _
  private var defaultParallelism = 1
  private var defaultSqlParallelism = 1
  private var userJars: Seq[String] = _
  private var userUdfJars: Seq[String] = _

  def open(): Unit = {
    val config = initFlinkConfig()
    createFlinkILoop(config)
    createTableEnvs()
    setTableEnvConfig()

    // init ZeppelinContext
    this.z = new FlinkZeppelinContext(this, new InterpreterHookRegistry(),
      Integer.parseInt(properties.getProperty("zeppelin.flink.maxResult", "1000")))
    val modifiers = new java.util.ArrayList[String]()
    modifiers.add("@transient")
    this.bind("z", z.getClass().getCanonicalName(), z, modifiers);

    this.jobManager = new JobManager(this.z, jmWebUrl, replacedJMWebUrl)

    // register JobListener
    val jobListener = new FlinkJobListener()
    this.benv.registerJobListener(jobListener)
    this.senv.registerJobListener(jobListener)

    // register hive catalog
    if (properties.getProperty("zeppelin.flink.enableHive", "false").toBoolean) {
      LOGGER.info("Hive is enabled, registering hive catalog.")
      registerHiveCatalog()
    } else {
      LOGGER.info("Hive is disabled.")
    }

    // load udf jar
    this.userUdfJars.foreach(jar => loadUDFJar(jar))
  }

  private def initFlinkConfig(): Config = {
    val flinkHome = sys.env.getOrElse("FLINK_HOME", "")
    val flinkConfDir = sys.env.getOrElse("FLINK_CONF_DIR", "")
    val hadoopConfDir = sys.env.getOrElse("HADOOP_CONF_DIR", "")
    val yarnConfDir = sys.env.getOrElse("YARN_CONF_DIR", "")
    val hiveConfDir = sys.env.getOrElse("HIVE_CONF_DIR", "")
    LOGGER.info("FLINK_HOME: " + flinkHome)
    LOGGER.info("FLINK_CONF_DIR: " + flinkConfDir)
    LOGGER.info("HADOOP_CONF_DIR: " + hadoopConfDir)
    LOGGER.info("YARN_CONF_DIR: " + yarnConfDir)
    LOGGER.info("HIVE_CONF_DIR: " + hiveConfDir)

    this.flinkVersion = new FlinkVersion(EnvironmentInformation.getVersion)
    LOGGER.info("Using flink: " + flinkVersion)
    this.flinkShims = FlinkShims.getInstance(flinkVersion, properties)

    this.configuration = GlobalConfiguration.loadConfiguration(flinkConfDir)

    mode = ExecutionMode.withName(
      properties.getProperty("flink.execution.mode", "LOCAL").toUpperCase)
    var config = Config(executionMode = mode)
    val jmMemory = properties.getProperty("flink.jm.memory", "1024")
    config = config.copy(yarnConfig =
      Some(ensureYarnConfig(config)
        .copy(jobManagerMemory = Some(jmMemory))))

    val tmMemory = properties.getProperty("flink.tm.memory", "1024")
    config = config.copy(yarnConfig =
      Some(ensureYarnConfig(config)
        .copy(taskManagerMemory = Some(tmMemory))))

    val appName = properties.getProperty("flink.yarn.appName", "Flink Yarn App Name")
    config = config.copy(yarnConfig =
      Some(ensureYarnConfig(config)
        .copy(name = Some(appName))))

    val slotNum = Integer.parseInt(properties.getProperty("flink.tm.slot", "1"))
    config = config.copy(yarnConfig =
      Some(ensureYarnConfig(config)
        .copy(slots = Some(slotNum))))
    this.configuration.setInteger("taskmanager.numberOfTaskSlots", slotNum)

    val queue = (properties.getProperty("flink.yarn.queue", "default"))
    config = config.copy(yarnConfig =
      Some(ensureYarnConfig(config)
        .copy(queue = Some(queue))))

    this.userUdfJars = getUserUdfJars()
    this.userJars = getUserJarsExceptUdfJars ++ this.userUdfJars
    LOGGER.info("UserJars: " + userJars.mkString(","))
    config = config.copy(externalJars = Some(userJars.toArray))
    LOGGER.info("Config: " + config)
    configuration.setString("flink.yarn.jars", userJars.mkString(":"))

    // load other configuration from interpreter properties
    properties.asScala.foreach(entry => configuration.setString(entry._1, entry._2))
    this.defaultParallelism = configuration.getInteger(CoreOptions.DEFAULT_PARALLELISM)
    this.defaultSqlParallelism = configuration.getInteger(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_DEFAULT_PARALLELISM)
    LOGGER.info("Default Parallelism: " + this.defaultParallelism)
    LOGGER.info("Default SQL Parallelism: " + this.defaultSqlParallelism)

    // set scala.color
    if (properties.getProperty("zeppelin.flink.scala.color", "true").toBoolean) {
      System.setProperty("scala.color", "true")
    }

    // set host/port when it is remote mode
    if (config.executionMode == ExecutionMode.REMOTE) {
      val host = properties.getProperty("flink.execution.remote.host")
      val port = properties.getProperty("flink.execution.remote.port")
      if (host == null) {
        throw new InterpreterException("flink.execution.remote.host is not " +
          "specified when using REMOTE mode")
      }
      if (port == null) {
        throw new InterpreterException("flink.execution.remote.port is not " +
          "specified when using REMOTE mode")
      }
      config = config.copy(host = Some(host))
        .copy(port = Some(Integer.parseInt(port)))
    }

    config
  }

  private def createFlinkILoop(config: Config): Unit = {
    val printReplOutput = properties.getProperty("zeppelin.flink.printREPLOutput", "true").toBoolean
    val replOut = if (printReplOutput) {
      new JPrintWriter(interpreterOutput, true)
    } else {
      new JPrintWriter(Console.out, true)
    }

    val (iLoop, cluster) = {
      // workaround of checking hadoop jars in yarn  mode
      if (mode == ExecutionMode.YARN) {
        try {
          Class.forName(classOf[FlinkYarnSessionCli].getName)
        } catch {
          case e: ClassNotFoundException =>
            throw new InterpreterException("Unable to load FlinkYarnSessionCli for yarn mode", e)
          case e: NoClassDefFoundError =>
            throw new InterpreterException("No hadoop jar found, make sure you have hadoop command in your PATH", e)
        }
      }

      val (effectiveConfig, cluster) = fetchConnectionInfo(config, configuration, flinkShims)
      this.configuration = effectiveConfig
      cluster match {
        case Some(clusterClient) =>
          // local mode or yarn
          if (mode == ExecutionMode.LOCAL) {
            LOGGER.info("Starting FlinkCluster in local mode")
            this.jmWebUrl = clusterClient.getWebInterfaceURL
          } else if (mode == ExecutionMode.YARN) {
            LOGGER.info("Starting FlinkCluster in yarn mode")
            if (properties.getProperty("flink.webui.yarn.useProxy", "false").toBoolean) {
              this.jmWebUrl = HadoopUtils.getYarnAppTrackingUrl(clusterClient)
              // for some cloud vender, the yarn address may be mapped to some other address.
              val yarnAddress = properties.getProperty("flink.webui.yarn.address")
              if (!StringUtils.isBlank(yarnAddress)) {
                this.replacedJMWebUrl = replaceYarnAddress(this.jmWebUrl, yarnAddress)
              }
            } else {
              this.jmWebUrl = clusterClient.getWebInterfaceURL
            }
          } else {
            throw new Exception("Starting FlinkCluster in invalid mode: " + mode)
          }
        case None =>
          // remote mode
          LOGGER.info("Use FlinkCluster in remote mode")
          this.jmWebUrl = "http://" + config.host.get + ":" + config.port.get
      }

      LOGGER.info(s"\nConnecting to Flink cluster: " + this.jmWebUrl)
      LOGGER.info("externalJars: " +
        config.externalJars.getOrElse(Array.empty[String]).mkString(":"))
      val classLoader = Thread.currentThread().getContextClassLoader
      try {
        // use FlinkClassLoader to initialize FlinkILoop, otherwise TableFactoryService could not find
        // the TableFactory properly
        Thread.currentThread().setContextClassLoader(getFlinkClassLoader)
        val repl = new FlinkILoop(configuration, config.externalJars, None, replOut)
        (repl, cluster)
      } catch {
        case e: Exception =>
          LOGGER.error(ExceptionUtils.getStackTrace(e))
          throw e
      } finally {
        Thread.currentThread().setContextClassLoader(classLoader)
      }
    }

    this.flinkILoop = iLoop
    this.cluster = cluster

    val settings = new Settings()
    settings.usejavacp.value = true
    settings.Yreplsync.value = true
    settings.classpath.value = userJars.mkString(File.pathSeparator)

    val outputDir = Files.createTempDirectory("flink-repl");
    val interpArguments = List(
      "-Yrepl-class-based",
      "-Yrepl-outdir", s"${outputDir.toFile.getAbsolutePath}"
    )
    settings.processArguments(interpArguments, true)

    flinkILoop.settings = settings
    flinkILoop.intp = new FlinkILoopInterpreter(settings, replOut)
    flinkILoop.intp.beQuietDuring {
      // set execution environment
      flinkILoop.intp.bind("benv", flinkILoop.scalaBenv)
      flinkILoop.intp.bind("senv", flinkILoop.scalaSenv)

      val packageImports = Seq[String](
        "org.apache.flink.core.fs._",
        "org.apache.flink.core.fs.local._",
        "org.apache.flink.api.common.io._",
        "org.apache.flink.api.common.aggregators._",
        "org.apache.flink.api.common.accumulators._",
        "org.apache.flink.api.common.distributions._",
        "org.apache.flink.api.common.operators._",
        "org.apache.flink.api.common.operators.base.JoinOperatorBase.JoinHint",
        "org.apache.flink.api.common.functions._",
        "org.apache.flink.api.java.io._",
        "org.apache.flink.api.java.aggregation._",
        "org.apache.flink.api.java.functions._",
        "org.apache.flink.api.java.operators._",
        "org.apache.flink.api.java.sampling._",
        "org.apache.flink.api.scala._",
        "org.apache.flink.api.scala.utils._",
        "org.apache.flink.streaming.api.scala._",
        "org.apache.flink.streaming.api.windowing.time._",
        "org.apache.flink.types.Row"
      )

      flinkILoop.intp.interpret("import " + packageImports.mkString(", "))

      if (flinkVersion.isFlink110) {
        flinkILoop.intp.interpret("import org.apache.flink.table.api.scala._")
      } else {
        flinkILoop.intp.interpret("import org.apache.flink.table.api._")
        flinkILoop.intp.interpret("import org.apache.flink.table.api.bridge.scala._")
      }

      flinkILoop.intp.interpret("import org.apache.flink.table.functions.ScalarFunction")
      flinkILoop.intp.interpret("import org.apache.flink.table.functions.AggregateFunction")
      flinkILoop.intp.interpret("import org.apache.flink.table.functions.TableFunction")
      flinkILoop.intp.interpret("import org.apache.flink.table.functions.TableAggregateFunction")
    }

    val in0 = getField(flinkILoop, "scala$tools$nsc$interpreter$ILoop$$in0")
      .asInstanceOf[Option[BufferedReader]]
    val reader = in0.fold(flinkILoop.chooseReader(settings))(r =>
      SimpleReader(r, replOut, interactive = true))

    flinkILoop.in = reader
    flinkILoop.initializeSynchronous()
    flinkILoop.intp.setContextClassLoader()
    reader.postInit()
    this.scalaCompleter = reader.completion.completer()

    this.benv = flinkILoop.scalaBenv
    this.senv = flinkILoop.scalaSenv
    val timeType = properties.getProperty("flink.senv.timecharacteristic", "EventTime")
    this.senv.setStreamTimeCharacteristic(TimeCharacteristic.valueOf(timeType))
    this.benv.setParallelism(configuration.getInteger(CoreOptions.DEFAULT_PARALLELISM))
    this.senv.setParallelism(configuration.getInteger(CoreOptions.DEFAULT_PARALLELISM))

    setAsContext()
  }

  private def createTableEnvs(): Unit = {
    val originalClassLoader = Thread.currentThread().getContextClassLoader
    try {
      Thread.currentThread().setContextClassLoader(getFlinkClassLoader)
      val tblConfig = new TableConfig
      tblConfig.getConfiguration.addAll(configuration)
      // Step 1.1 Initialize the CatalogManager if required.
      val catalogManager = flinkShims.createCatalogManager(tblConfig.getConfiguration).asInstanceOf[CatalogManager]
      // Step 1.2 Initialize the ModuleManager if required.
      val moduleManager = new ModuleManager();
      // Step 1.3 Initialize the FunctionCatalog if required.
      val flinkFunctionCatalog = new FunctionCatalog(tblConfig, catalogManager, moduleManager);
      val blinkFunctionCatalog = new FunctionCatalog(tblConfig, catalogManager, moduleManager);

      this.tblEnvFactory = new TableEnvFactory(this.flinkVersion, this.flinkShims, this.benv, this.senv, tblConfig,
        catalogManager, moduleManager, flinkFunctionCatalog, blinkFunctionCatalog)

      val modifiers = new java.util.ArrayList[String]()
      modifiers.add("@transient")

      // blink planner
      var btEnvSetting = EnvironmentSettings.newInstance().inBatchMode().useBlinkPlanner().build()
      this.btenv = tblEnvFactory.createJavaBlinkBatchTableEnvironment(btEnvSetting);
      flinkILoop.bind("btenv", btenv.getClass().getCanonicalName(), btenv, List("@transient"))
      this.java_btenv = this.btenv

      var stEnvSetting =
        EnvironmentSettings.newInstance().inStreamingMode().useBlinkPlanner().build()
      this.stenv = tblEnvFactory.createScalaBlinkStreamTableEnvironment(stEnvSetting)
      flinkILoop.bind("stenv", stenv.getClass().getCanonicalName(), stenv, List("@transient"))
      this.java_stenv = tblEnvFactory.createJavaBlinkStreamTableEnvironment(stEnvSetting)

      // flink planner
      this.btenv_2 = tblEnvFactory.createScalaFlinkBatchTableEnvironment()
      flinkILoop.bind("btenv_2", btenv_2.getClass().getCanonicalName(), btenv_2, List("@transient"))
      stEnvSetting =
        EnvironmentSettings.newInstance().inStreamingMode().useOldPlanner().build()
      this.stenv_2 = tblEnvFactory.createScalaFlinkStreamTableEnvironment(stEnvSetting)
      flinkILoop.bind("stenv_2", stenv_2.getClass().getCanonicalName(), stenv_2, List("@transient"))

      this.java_btenv_2 = tblEnvFactory.createJavaFlinkBatchTableEnvironment()
      btEnvSetting = EnvironmentSettings.newInstance.useOldPlanner.inStreamingMode.build
      this.java_stenv_2 = tblEnvFactory.createJavaFlinkStreamTableEnvironment(btEnvSetting)
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader)
    }
  }

  private def setTableEnvConfig(): Unit = {
    this.properties.asScala.filter(e => e._1.startsWith("table.exec"))
      .foreach(e => {
        this.btenv.getConfig.getConfiguration.setString(e._1, e._2)
        this.java_btenv.getConfig.getConfiguration.setString(e._1, e._2)
        this.stenv.getConfig.getConfiguration.setString(e._1, e._2)
        this.java_stenv.getConfig.getConfiguration.setString(e._1, e._2)
      })

    // set python exec for PyFlink
    val pythonExec = properties.getProperty("zeppelin.pyflink.python", "")
    if (!StringUtils.isBlank(pythonExec)) {
      this.stenv.getConfig.getConfiguration.setString("python.exec", pythonExec)
      this.btenv.getConfig.getConfiguration.setString("python.exec", pythonExec)
      this.java_btenv.getConfig.getConfiguration.setString("python.exec", pythonExec)
      this.java_stenv.getConfig.getConfiguration.setString("python.exec", pythonExec)
    }

    if (java.lang.Boolean.parseBoolean(
      properties.getProperty("zeppelin.flink.disableSysoutLogging", "true"))) {
      this.benv.getConfig.disableSysoutLogging()
      this.senv.getConfig.disableSysoutLogging()
    }
  }

  private def registerHiveCatalog(): Unit = {
    val hiveConfDir =
      properties.getOrDefault("HIVE_CONF_DIR", System.getenv("HIVE_CONF_DIR")).toString
    if (hiveConfDir == null) {
      throw new InterpreterException("HIVE_CONF_DIR is not specified");
    }
    val database = properties.getProperty("zeppelin.flink.hive.database", "default")
    val hiveVersion = properties.getProperty("zeppelin.flink.hive.version", "2.3.4")
    val hiveCatalog = new HiveCatalog("hive", database, hiveConfDir, hiveVersion)
    this.btenv.registerCatalog("hive", hiveCatalog)
    this.btenv.useCatalog("hive")
    this.btenv.useDatabase(database)
    if (properties.getProperty("zeppelin.flink.module.enableHive", "false").toBoolean) {
      this.btenv.loadModule("hive", new HiveModule(hiveVersion))
    }
  }

  private def loadUDFJar(jar: String): Unit = {
    LOGGER.info("Loading UDF Jar: " + jar)
    val jarFile = new JarFile(jar)
    val entries = jarFile.entries

    val udfPackages = properties.getProperty("flink.udf.jars.packages", "").split(",").toSet
    val urls = Array(new URL("jar:file:" + jar + "!/"))
    val cl = new URLClassLoader(urls, getFlinkScalaShellLoader)

    while (entries.hasMoreElements) {
      val je = entries.nextElement
      if (!je.isDirectory && je.getName.endsWith(".class") && !je.getName.contains("$")) {
        try {
          // -6 because of .class
          var className = je.getName.substring(0, je.getName.length - 6)
          className = className.replace('/', '.')
          if (udfPackages.isEmpty || udfPackages.exists( p => className.startsWith(p))) {
            val c = cl.loadClass(className)
            val udf = c.newInstance()
            if (udf.isInstanceOf[ScalarFunction]) {
              val scalarUDF = udf.asInstanceOf[ScalarFunction]
              btenv.registerFunction(c.getSimpleName, scalarUDF)
            } else if (udf.isInstanceOf[TableFunction[_]]) {
              val tableUDF = udf.asInstanceOf[TableFunction[_]]
              flinkShims.registerTableFunction(btenv, c.getSimpleName, tableUDF)
            } else if (udf.isInstanceOf[AggregateFunction[_, _]]) {
              val aggregateUDF = udf.asInstanceOf[AggregateFunction[_, _]]
              flinkShims.registerAggregateFunction(btenv, c.getSimpleName, aggregateUDF)
            } else if (udf.isInstanceOf[TableAggregateFunction[_, _]]) {
              val tableAggregateUDF = udf.asInstanceOf[TableAggregateFunction[_, _]]
              flinkShims.registerTableAggregateFunction(btenv, c.getSimpleName, tableAggregateUDF)
            } else {
              LOGGER.warn("No UDF definition found in class file: " + je.getName)
            }
          }
        } catch {
          case e : Exception =>
            LOGGER.info("Fail to inspect udf class: " + je.getName, e)
        }
      }
    }
  }

  private def setAsContext(): Unit = {
    val streamFactory = new StreamExecutionEnvironmentFactory() {
      override def createExecutionEnvironment = senv.getJavaEnv
    }
    //StreamExecutionEnvironment
    var method = classOf[JStreamExecutionEnvironment].getDeclaredMethod("initializeContextEnvironment",
      classOf[StreamExecutionEnvironmentFactory])
    method.setAccessible(true)
    method.invoke(null, streamFactory);

    val batchFactory = new ExecutionEnvironmentFactory() {
      override def createExecutionEnvironment = benv.getJavaEnv
    }
    //StreamExecutionEnvironment
    method = classOf[JExecutionEnvironment].getDeclaredMethod("initializeContextEnvironment",
      classOf[ExecutionEnvironmentFactory])
    method.setAccessible(true)
    method.invoke(null, batchFactory);
  }

  // for use in java side
  protected def bind(name: String,
                     tpe: String,
                     value: Object,
                     modifier: java.util.List[String]): Unit = {
    flinkILoop.beQuietDuring {
      flinkILoop.bind(name, tpe, value, modifier.asScala.toList)
    }
  }

  protected def bind(name: String,
                     tpe: String,
                     value: Object,
                     modifier: List[String]): Unit = {
    flinkILoop.beQuietDuring {
      flinkILoop.bind(name, tpe, value, modifier)
    }
  }

  protected def completion(buf: String,
                           cursor: Int,
                           context: InterpreterContext): java.util.List[InterpreterCompletion] = {
    val completions = scalaCompleter.complete(buf.substring(0, cursor), cursor).candidates
      .map(e => new InterpreterCompletion(e, e, null))
    scala.collection.JavaConversions.seqAsJavaList(completions)
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


  protected def getField(obj: Object, name: String): Object = {
    val field = obj.getClass.getField(name)
    field.setAccessible(true)
    field.get(obj)
  }

  /**
   * This is just a workaround to make table api work in multiple threads.
   */
  def createPlannerAgain(): Unit = {
    val originalClassLoader = Thread.currentThread().getContextClassLoader
    try {
      Thread.currentThread().setContextClassLoader(getFlinkClassLoader)
      val stEnvSetting =
        EnvironmentSettings.newInstance().inStreamingMode().useBlinkPlanner().build()
      this.tblEnvFactory.createPlanner(stEnvSetting)
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader)
    }
  }

  def interpret(code: String, context: InterpreterContext): InterpreterResult = {
    val originalStdOut = System.out
    val originalStdErr = System.err;
    if (context != null) {
      interpreterOutput.setInterpreterOutput(context.out)
      context.out.clear()
    }

    Console.withOut(if (context != null) context.out else Console.out) {
      System.setOut(Console.out)
      System.setErr(Console.out)
      interpreterOutput.ignoreLeadingNewLinesFromScalaReporter()
      // add print("") at the end in case the last line is comment which lead to INCOMPLETE
      val lines = code.split("\\n") ++ List("print(\"\")")
      var incompleteCode = ""
      var lastStatus: InterpreterResult.Code = null

      for ((line, i) <- lines.zipWithIndex if !line.trim.isEmpty) {
        val nextLine = if (incompleteCode != "") {
          incompleteCode + "\n" + line
        } else {
          line
        }
        if (i < (lines.length - 1) && lines(i + 1).trim.startsWith(".")) {
          incompleteCode = nextLine
        } else {
          flinkILoop.interpret(nextLine) match {
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
      }
      // flush all output before returning result to frontend
      Console.flush()
      interpreterOutput.setInterpreterOutput(null)
      // reset the java stdout
      System.setOut(originalStdOut)
      System.setErr(originalStdErr)
      return new InterpreterResult(lastStatus)
    }
  }

  def setSavePointIfNecessary(context: InterpreterContext): Unit = {
    val savepointDir = context.getLocalProperties.get("savepointDir")
    val savepointPath = context.getLocalProperties.get("savepointPath");

    if (!StringUtils.isBlank(savepointPath)){
      LOGGER.info("savepointPath has been setup by user , savepointPath = {}", savepointPath)
      configuration.setString("execution.savepoint.path", savepointPath)
      return
    } else if ("".equals(savepointPath)) {
      LOGGER.info("savepointPath is empty, remove execution.savepoint.path")
      configuration.removeConfig(SavepointConfigOptions.SAVEPOINT_PATH);
      return;
    }

    if (!StringUtils.isBlank(savepointDir)) {
      val savepointPath = z.angular(context.getParagraphId + "_savepointpath", context.getNoteId, null)
      if (savepointPath == null) {
        LOGGER.info("savepointPath is null because it is the first run")
        // remove the SAVEPOINT_PATH which may be set by last job.
        configuration.removeConfig(SavepointConfigOptions.SAVEPOINT_PATH)
      } else {
        LOGGER.info("Set savepointPath to: " + savepointPath.toString)
        configuration.setString("execution.savepoint.path", savepointPath.toString)
      }
    }
  }

  def setParallelismIfNecessary(context: InterpreterContext): Unit = {
    val parallelismStr = context.getLocalProperties.get("parallelism")
    if (!StringUtils.isBlank(parallelismStr)) {
      val parallelism = parallelismStr.toInt
      this.senv.setParallelism(parallelism)
      this.benv.setParallelism(parallelism)
      this.stenv.getConfig.getConfiguration
        .setString(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_DEFAULT_PARALLELISM.key(), parallelism + "")
      this.btenv.getConfig.getConfiguration
        .setString(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_DEFAULT_PARALLELISM.key(), parallelism + "")
    }
    val maxParallelismStr = context.getLocalProperties.get("maxParallelism")
    if (!StringUtils.isBlank(maxParallelismStr)) {
      val maxParallelism = maxParallelismStr.toInt
      senv.setParallelism(maxParallelism)
    }
  }

  def cancel(context: InterpreterContext): Unit = {
    jobManager.cancelJob(context)
  }

  def getProgress(context: InterpreterContext): Int = {
    jobManager.getJobProgress(context.getParagraphId)
  }

  def close(): Unit = {
    LOGGER.info("Closing FlinkScalaInterpreter")
    if (properties.getProperty("flink.interpreter.close.shutdown_cluster", "true").toBoolean) {
      if (cluster != null) {
        cluster match {
          case Some(clusterClient) =>
            LOGGER.info("Shutdown FlinkCluster")
            clusterClient.shutDownCluster()
            clusterClient.close()
            // delete staging dir
            if (mode == ExecutionMode.YARN) {
              HadoopUtils.cleanupStagingDirInternal(clusterClient)
            }
          case None =>
            LOGGER.info("Don't close the Remote FlinkCluster")
        }
      }
    } else {
      LOGGER.info("Keep cluster alive when closing interpreter")
    }

    if (flinkILoop != null) {
      flinkILoop.closeInterpreter()
      flinkILoop = null
    }
    if (jobManager != null) {
      jobManager.shutdown()
    }
  }

  def getExecutionEnvironment(): ExecutionEnvironment = this.benv

  def getStreamExecutionEnvironment(): StreamExecutionEnvironment = this.senv

  def getBatchTableEnvironment(planner: String = "blink"): TableEnvironment = {
    if (planner == "blink")
      this.btenv
    else
      this.btenv_2
  }

  def getStreamTableEnvironment(planner: String = "blink"): TableEnvironment = {
    if (planner == "blink")
      this.stenv
    else
      this.stenv_2
  }

  def getJavaBatchTableEnvironment(planner: String): TableEnvironment = {
    if (planner == "blink") {
      this.java_btenv
    } else {
      this.java_btenv_2
    }
  }

  def getJavaStreamTableEnvironment(planner: String): TableEnvironment = {
    if (planner == "blink") {
      this.java_stenv
    } else {
      this.java_stenv_2
    }
  }

  def getDefaultParallelism = this.defaultParallelism

  def getDefaultSqlParallelism = this.defaultSqlParallelism

  private def getUserJarsExceptUdfJars: Seq[String] = {
    val flinkJars =
      if (!StringUtils.isBlank(properties.getProperty("flink.execution.jars", ""))) {
        getOrDownloadJars(properties.getProperty("flink.execution.jars").split(",").toSeq)
      } else {
        Seq.empty[String]
      }

    val flinkPackageJars =
      if (!StringUtils.isBlank(properties.getProperty("flink.execution.packages", ""))) {
        val packages = properties.getProperty("flink.execution.packages")
        DependencyUtils.resolveMavenDependencies(null, packages, null, null, None).split(":").toSeq
      } else {
        Seq.empty[String]
      }

    flinkJars ++ flinkPackageJars
  }

  private def getUserUdfJars(): Seq[String] = {
    if (!StringUtils.isBlank(properties.getProperty("flink.udf.jars", ""))) {
      getOrDownloadJars(properties.getProperty("flink.udf.jars").split(",").toSeq)
    } else {
      Seq.empty[String]
    }
  }

  private def getOrDownloadJars(jars: Seq[String]): Seq[String] = {
    jars.map(jar => {
      if (jar.contains("://")) {
        HadoopUtils.downloadJar(jar)
      } else {
        val jarFile = new File(jar)
        if (!jarFile.exists() || !jarFile.isFile) {
          throw new Exception(s"jar file: ${jar} doesn't exist")
        } else {
          jar
        }
      }
    })
  }

  def getJobManager = this.jobManager

  def getFlinkScalaShellLoader: ClassLoader = {
    val userCodeJarFile = this.flinkILoop.writeFilesToDisk();
    new URLClassLoader(Array(userCodeJarFile.toURL) ++ userJars.map(e => new File(e).toURL))
  }

  private def getFlinkClassLoader: ClassLoader = {
    new URLClassLoader(userJars.map(e => new File(e).toURL).toArray)
  }

  def getZeppelinContext = this.z

  def getConfiguration = this.configuration

  def getCluster = cluster

  def getFlinkILoop = flinkILoop

  def getFlinkShims = flinkShims

  def getFlinkVersion = flinkVersion

  class FlinkJobListener extends JobListener {

    override def onJobSubmitted(jobClient: JobClient, e: Throwable): Unit = {
      if (e != null) {
        LOGGER.warn("Fail to submit job")
      } else {
        if (InterpreterContext.get() == null) {
          LOGGER.warn("Job {} is submitted but unable to associate this job to paragraph, " +
            "as InterpreterContext is null", jobClient.getJobID)
        } else {
          LOGGER.info("Job {} is submitted for paragraph {}", Array(jobClient.getJobID,
            InterpreterContext.get().getParagraphId): _ *)
          jobManager.addJob(InterpreterContext.get(), jobClient)
          if (jmWebUrl != null) {
            jobManager.sendFlinkJobUrl(InterpreterContext.get());
          } else {
            LOGGER.error("Unable to link JobURL, because JobManager weburl is null")
          }
        }
      }
    }

    override def onJobExecuted(jobExecutionResult: JobExecutionResult, e: Throwable): Unit = {
      if (e != null) {
        LOGGER.warn("Fail to execute job")
      } else {
        LOGGER.info("Job {} is executed with time {} seconds", jobExecutionResult.getJobID,
          jobExecutionResult.getNetRuntime(TimeUnit.SECONDS))
      }
      if (InterpreterContext.get() != null) {
        jobManager.removeJob(InterpreterContext.get().getParagraphId)
      } else {
        if (e == null) {
          LOGGER.warn("Unable to remove this job {}, as InterpreterContext is null",
            jobExecutionResult.getJobID)
        }
      }
    }
  }

  def replaceYarnAddress(webURL: String, yarnAddress: String): String = {
    val pattern = "(https?://.*:\\d+)(.*)".r
    val pattern(prefix, remaining) = webURL
    yarnAddress + remaining
  }
}


