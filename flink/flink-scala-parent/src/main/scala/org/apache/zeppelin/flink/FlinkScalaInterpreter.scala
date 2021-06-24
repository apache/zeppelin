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

import java.io.File
import java.net.{URL, URLClassLoader}
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.api.scala.ExecutionEnvironment
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
import org.apache.flink.table.catalog.hive.HiveCatalog
import org.apache.flink.table.functions.{AggregateFunction, ScalarFunction, TableAggregateFunction, TableFunction}
import org.apache.flink.table.module.hive.HiveModule
import org.apache.flink.yarn.cli.FlinkYarnSessionCli
import org.apache.zeppelin.dep.DependencyResolver
import org.apache.zeppelin.flink.internal.FlinkShell._
import org.apache.zeppelin.flink.internal.FlinkILoop
import org.apache.zeppelin.interpreter.Interpreter.FormType
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream
import org.apache.zeppelin.interpreter.{InterpreterContext, InterpreterException, InterpreterHookRegistry, InterpreterResult}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConversions
import scala.collection.JavaConverters._
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.{Completion, IMain, IR, JPrintWriter, Results, SimpleReader}

/**
 * It instantiate flink scala shell and create env, senv, btenv, stenv.
 *
 * @param properties
 */
abstract class FlinkScalaInterpreter(val properties: Properties,
                                     val flinkScalaClassLoader: URLClassLoader) {

  private lazy val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  protected var flinkILoop: FlinkILoop = _
  private var cluster: Option[ClusterClient[_]] = _

  protected var scalaCompletion: Completion = _
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
  // java version of blink TableEnvironment
  private var java_btenv: TableEnvironment = _
  private var java_stenv: TableEnvironment = _

  // java version of flink TableEnvironment
  private var java_btenv_2: TableEnvironment = _
  private var java_stenv_2: TableEnvironment = _

  private var z: FlinkZeppelinContext = _
  private var flinkVersion: FlinkVersion = _
  private var flinkShims: FlinkShims = _
  // used for calling jm rest api
  private var jmWebUrl: String = _
  // used for displaying on zeppelin ui
  private var displayedJMWebUrl: String = _
  private var jobManager: JobManager = _
  private var defaultParallelism = 1
  private var defaultSqlParallelism = 1

  // flink.execution.jars + flink.execution.jars + flink.udf.jars
  protected var userJars: Seq[String] = _
  // flink.udf.jars
  private var userUdfJars: Seq[String] = _


  def open(): Unit = {

    val config = initFlinkConfig()
    createFlinkILoop(config)
    createTableEnvs()
    setTableEnvConfig()

    // init ZeppelinContext
    this.z = new FlinkZeppelinContext(this, new InterpreterHookRegistry(),
      Integer.parseInt(properties.getProperty("zeppelin.flink.maxResult", "1000")))
    this.bind("z", z.getClass().getCanonicalName(), z, List("@transient"))
    this.jobManager = new JobManager(jmWebUrl, displayedJMWebUrl, properties)

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

    if (mode == ExecutionMode.YARN_APPLICATION) {
      // have to call senv.execute method before running any user code, otherwise yarn application mode
      // will cause ClassNotFound issue. Needs to do more investigation. TODO(zjffdu)
      val initCode =
        """
          |val data = senv.fromElements("hello world", "hello flink", "hello hadoop")
          |data.flatMap(line => line.split("\\s"))
          |  .map(w => (w, 1))
          |  .keyBy(0)
          |  .sum(1)
          |  .print
          |
          |senv.execute()
          |""".stripMargin

      interpret(initCode, InterpreterContext.get())
      InterpreterContext.get().out.clear()
    }
  }

  def createIMain(settings: Settings, out: JPrintWriter): IMain

  def createSettings(): Settings

  private def initFlinkConfig(): Config = {

    this.flinkVersion = new FlinkVersion(EnvironmentInformation.getVersion)
    LOGGER.info("Using flink: " + flinkVersion)
    this.flinkShims = FlinkShims.getInstance(flinkVersion, properties)

    var flinkHome = sys.env.getOrElse("FLINK_HOME", "")
    var flinkConfDir = sys.env.getOrElse("FLINK_CONF_DIR", "")
    val hadoopConfDir = sys.env.getOrElse("HADOOP_CONF_DIR", "")
    val yarnConfDir = sys.env.getOrElse("YARN_CONF_DIR", "")
    var hiveConfDir = sys.env.getOrElse("HIVE_CONF_DIR", "")

    mode = ExecutionMode.withName(
      properties.getProperty("flink.execution.mode", "LOCAL")
        .replace("-", "_")
        .toUpperCase)
    if (mode == ExecutionMode.YARN_APPLICATION) {
      if (flinkVersion.isFlink110) {
        throw new Exception("yarn-application mode is only supported after Flink 1.11")
      }
      // use current yarn container working directory as FLINK_HOME, FLINK_CONF_DIR and HIVE_CONF_DIR
      val workingDirectory = new File(".").getAbsolutePath
      flinkHome = workingDirectory
      flinkConfDir = workingDirectory
      hiveConfDir = workingDirectory
    }
    LOGGER.info("FLINK_HOME: " + flinkHome)
    LOGGER.info("FLINK_CONF_DIR: " + flinkConfDir)
    LOGGER.info("HADOOP_CONF_DIR: " + hadoopConfDir)
    LOGGER.info("YARN_CONF_DIR: " + yarnConfDir)
    LOGGER.info("HIVE_CONF_DIR: " + hiveConfDir)

    this.configuration = GlobalConfiguration.loadConfiguration(flinkConfDir)
    var config = Config(executionMode = mode)
    val jmMemory = properties.getProperty("jobmanager.memory.process.size",
      properties.getProperty("flink.jm.memory", "1024"))
    config = config.copy(yarnConfig =
      Some(ensureYarnConfig(config)
        .copy(jobManagerMemory = Some(jmMemory))))

    val tmMemory = properties.getProperty("taskmanager.memory.process.size",
      properties.getProperty("flink.tm.memory", "1024"))
    config = config.copy(yarnConfig =
      Some(ensureYarnConfig(config)
        .copy(taskManagerMemory = Some(tmMemory))))

    val appName = properties.getProperty("yarn.application.name",
      properties.getProperty("flink.yarn.appName", "Flink Yarn App Name"))
    config = config.copy(yarnConfig =
      Some(ensureYarnConfig(config)
        .copy(name = Some(appName))))

    val slotNum =  Integer.parseInt(properties.getProperty("taskmanager.numberOfTaskSlots",
     properties.getProperty("flink.tm.slot", "1")))
    config = config.copy(yarnConfig =
      Some(ensureYarnConfig(config)
        .copy(slots = Some(slotNum))))

    val queue = properties.getProperty("yarn.application.queue",
      properties.getProperty("flink.yarn.queue", "default"))
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
                this.displayedJMWebUrl = FlinkScalaInterpreter.replaceYarnAddress(this.jmWebUrl, yarnAddress)
              }
            } else {
              this.jmWebUrl = clusterClient.getWebInterfaceURL
            }
          } else {
            throw new Exception("Starting FlinkCluster in invalid mode: " + mode)
          }
        case None =>
          // remote mode
          if (mode == ExecutionMode.YARN_APPLICATION) {
            val yarnAppId = System.getenv("_APP_ID");
            LOGGER.info("Use FlinkCluster in yarn application mode, appId: {}", yarnAppId)
            this.jmWebUrl = "http://localhost:" + HadoopUtils.getFlinkRestPort(yarnAppId)
            this.displayedJMWebUrl = HadoopUtils.getYarnAppTrackingUrl(yarnAppId)
          } else {
            LOGGER.info("Use FlinkCluster in remote mode")
            this.jmWebUrl = "http://" + config.host.get + ":" + config.port.get
          }
      }

      if (this.displayedJMWebUrl == null) {
        // use jmWebUrl as displayedJMWebUrl if it is not set
        this.displayedJMWebUrl = this.jmWebUrl
      }

      LOGGER.info(s"\nConnecting to Flink cluster: " + this.jmWebUrl)
      if (InterpreterContext.get() != null) {
        InterpreterContext.get().getIntpEventClient.sendWebUrlInfo(this.jmWebUrl)
      }
      LOGGER.info("externalJars: " +
        config.externalJars.getOrElse(Array.empty[String]).mkString(":"))
      val classLoader = Thread.currentThread().getContextClassLoader
      try {
        // use FlinkClassLoader to initialize FlinkILoop, otherwise TableFactoryService could not find
        // the TableFactory properly
        Thread.currentThread().setContextClassLoader(getFlinkClassLoader)
        val repl = new FlinkILoop(configuration, config.externalJars, None, replOut, mode,
          JExecutionEnvironment.getExecutionEnvironment,
          JStreamExecutionEnvironment.getExecutionEnvironment,
          this)
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

    val settings = createSettings()

    val outputDir = Files.createTempDirectory("flink-repl");
    val interpArguments = List(
      "-Yrepl-class-based",
      "-Yrepl-outdir", s"${outputDir.toFile.getAbsolutePath}"
    )
    settings.processArguments(interpArguments, true)

    flinkILoop.settings = settings
    flinkILoop.intp = createIMain(settings, replOut)
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

    val in0 = None
    val reader = in0.fold(flinkILoop.chooseReader(settings))(r =>
      SimpleReader(r, replOut, interactive = true))

    flinkILoop.in = reader
    flinkILoop.initializeSynchronous()
    flinkILoop.intp.setContextClassLoader()
    reader.postInit()
    this.scalaCompletion = reader.completion

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
      val tableConfig = new TableConfig
      tableConfig.getConfiguration.addAll(configuration)

      this.tblEnvFactory = new TableEnvFactory(this.flinkVersion, this.flinkShims,
        this.benv, this.senv, tableConfig)

      // blink planner
      var btEnvSetting = EnvironmentSettings.newInstance().inBatchMode().useBlinkPlanner().build()
      this.btenv = tblEnvFactory.createJavaBlinkBatchTableEnvironment(btEnvSetting, getFlinkClassLoader);
      flinkILoop.bind("btenv", btenv.getClass().getCanonicalName(), btenv, List("@transient"))
      this.java_btenv = this.btenv

      var stEnvSetting =
        EnvironmentSettings.newInstance().inStreamingMode().useBlinkPlanner().build()
      this.stenv = tblEnvFactory.createScalaBlinkStreamTableEnvironment(stEnvSetting, getFlinkClassLoader)
      flinkILoop.bind("stenv", stenv.getClass().getCanonicalName(), stenv, List("@transient"))
      this.java_stenv = tblEnvFactory.createJavaBlinkStreamTableEnvironment(stEnvSetting, getFlinkClassLoader)

      // flink planner
      this.btenv_2 = tblEnvFactory.createScalaFlinkBatchTableEnvironment()
      flinkILoop.bind("btenv_2", btenv_2.getClass().getCanonicalName(), btenv_2, List("@transient"))
      stEnvSetting =
        EnvironmentSettings.newInstance().inStreamingMode().useOldPlanner().build()
      this.stenv_2 = tblEnvFactory.createScalaFlinkStreamTableEnvironment(stEnvSetting, getFlinkClassLoader)
      flinkILoop.bind("stenv_2", stenv_2.getClass().getCanonicalName(), stenv_2, List("@transient"))

      this.java_btenv_2 = tblEnvFactory.createJavaFlinkBatchTableEnvironment()
      btEnvSetting = EnvironmentSettings.newInstance.useOldPlanner.inStreamingMode.build
      this.java_stenv_2 = tblEnvFactory.createJavaFlinkStreamTableEnvironment(btEnvSetting, getFlinkClassLoader)
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
      flinkShims.disableSysoutLogging(this.benv.getConfig, this.senv.getConfig);
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
              flinkShims.registerScalarFunction(btenv, c.getSimpleName, scalarUDF)
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
          case e : Throwable =>
            LOGGER.info("Fail to inspect udf class: " + je.getName, e)
        }
      }
    }
  }

  private def setAsContext(): Unit = {
    val streamFactory = flinkShims.createStreamExecutionEnvironmentFactory(this.senv.getJavaEnv)
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

  protected def bind(name: String,
                     tpe: String,
                     value: Object,
                     modifier: List[String]): Unit = {
    flinkILoop.beQuietDuring {
      val result = flinkILoop.bind(name, tpe, value, modifier)
      if (result != IR.Success) {
        throw new Exception("Fail to bind variable: " + name)
      }
    }
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

  /**
   * This is just a workaround to make table api work in multiple threads.
   */
  def createPlannerAgain(): Unit = {
    val originalClassLoader = Thread.currentThread().getContextClassLoader
    try {
      Thread.currentThread().setContextClassLoader(getFlinkClassLoader)
      val stEnvSetting =
        EnvironmentSettings.newInstance().inStreamingMode().useBlinkPlanner().build()
      this.tblEnvFactory.createStreamPlanner(stEnvSetting)
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

  /**
   * Set execution.savepoint.path in the following order:
   *
   * 1. Use savepoint path stored in paragraph config, this is recorded by zeppelin when paragraph is canceled,
   * 2. Use checkpoint path stored in pararaph config, this is recorded by zeppelin in flink job progress poller.
   * 3. Use local property 'execution.savepoint.path' if user set it.
   * 4. Otherwise remove 'execution.savepoint.path' when user didn't specify it in %flink.conf
   *
   * @param context
   */
  def setSavepointPathIfNecessary(context: InterpreterContext): Unit = {
    val savepointPath = context.getConfig.getOrDefault(JobManager.SAVEPOINT_PATH, "").toString
    val resumeFromSavepoint = context.getBooleanLocalProperty(JobManager.RESUME_FROM_SAVEPOINT, false)
    // flink 1.12 use copied version of configuration, so in order to update configuration we have to
    // get the internal configuration of StreamExecutionEnvironment.
    val internalConfiguration = getConfigurationOfStreamExecutionEnv()
    if (!StringUtils.isBlank(savepointPath) && resumeFromSavepoint){
      LOGGER.info("Resume job from savepoint , savepointPath = {}", savepointPath)
      internalConfiguration.setString(SavepointConfigOptions.SAVEPOINT_PATH.key(), savepointPath);
      return
    }

    val checkpointPath = context.getConfig.getOrDefault(JobManager.LATEST_CHECKPOINT_PATH, "").toString
    val resumeFromLatestCheckpoint = context.getBooleanLocalProperty(JobManager.RESUME_FROM_CHECKPOINT, false)
    if (!StringUtils.isBlank(checkpointPath) && resumeFromLatestCheckpoint) {
      LOGGER.info("Resume job from checkpoint , checkpointPath = {}", checkpointPath)
      internalConfiguration.setString(SavepointConfigOptions.SAVEPOINT_PATH.key(), checkpointPath);
      return
    }

    val userSavepointPath = context.getLocalProperties.getOrDefault(
      SavepointConfigOptions.SAVEPOINT_PATH.key(), "")
    if (!StringUtils.isBlank(userSavepointPath)) {
      LOGGER.info("Resume job from user set savepoint , savepointPath = {}", userSavepointPath)
      internalConfiguration.setString(SavepointConfigOptions.SAVEPOINT_PATH.key(), checkpointPath)
      return;
    }

    val userSettingSavepointPath = properties.getProperty(SavepointConfigOptions.SAVEPOINT_PATH.key())
    if (StringUtils.isBlank(userSettingSavepointPath)) {
      // remove SAVEPOINT_PATH when user didn't set it via %flink.conf
      internalConfiguration.removeConfig(SavepointConfigOptions.SAVEPOINT_PATH)
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
        val dependencyResolver = new DependencyResolver(System.getProperty("user.home") + "/.m2/repository")
        packages.split(",")
          .flatMap(e => JavaConversions.asScalaBuffer(dependencyResolver.load(e)))
          .map(e => e.getAbsolutePath).toSeq
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
    val userCodeJarFile = this.flinkILoop.writeFilesToDisk()
    new URLClassLoader(Array(userCodeJarFile.toURL) ++ userJars.map(e => new File(e).toURL))
  }

  private def getFlinkClassLoader: ClassLoader = {
    new URLClassLoader(userJars.map(e => new File(e).toURL).toArray)
  }

  def getZeppelinContext = this.z

  def getFlinkConfiguration = this.configuration

  def getFormType() = FormType.NATIVE

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

  def getUserJars:java.util.List[String] = JavaConversions.seqAsJavaList(userJars)

  def completion(buf: String, cursor: Int, context: InterpreterContext): java.util.List[InterpreterCompletion]

  private def getConfigurationOfStreamExecutionEnv(): Configuration = {
    val getConfigurationMethod = classOf[JStreamExecutionEnvironment].getDeclaredMethod("getConfiguration")
    getConfigurationMethod.setAccessible(true)
    getConfigurationMethod.invoke(this.senv.getJavaEnv).asInstanceOf[Configuration]
  }
}

object FlinkScalaInterpreter {
  def replaceYarnAddress(webURL: String, yarnAddress: String): String = {
    val pattern = "(https?://.*:\\d+)(.*)".r
    val pattern(prefix, remaining) = webURL
    yarnAddress + remaining
  }
}


