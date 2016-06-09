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

package org.apache.spark

import java.io.File
import java.io.PrintWriter
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.URL
import java.net.URLClassLoader
import java.util._
import java.util.concurrent.atomic.AtomicInteger

import com.google.common.base.Joiner

import org.apache.spark.repl.SparkILoop
import org.apache.spark.scheduler.{SparkActiveJobs, ActiveJob, Pool}
import org.apache.spark.sql.SparkSession
import org.apache.spark.ui.jobs.JobProgressListener
import org.apache.zeppelin.interpreter.Interpreter
import org.apache.zeppelin.interpreter.Interpreter.FormType
import org.apache.zeppelin.interpreter.InterpreterContext
import org.apache.zeppelin.interpreter.InterpreterException
import org.apache.zeppelin.interpreter.InterpreterResult
import org.apache.zeppelin.interpreter.InterpreterResult.Code
import org.apache.zeppelin.interpreter.InterpreterUtils
import org.apache.zeppelin.scheduler.Scheduler
import org.apache.zeppelin.scheduler.SchedulerFactory
import org.apache.zeppelin.spark.{SparkVersion, SparkOutputStream}
import org.apache.zeppelin.spark2.ZeppelinContext
import org.apache.zeppelin.spark2.dep.SparkDependencyResolver

import grizzled.slf4j.Logging

import scala.collection.Iterator
import scala.collection.JavaConversions
import scala.collection.JavaConverters
import scala.collection.Seq
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.reflect.io.AbstractFile
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.{Results, IMain, JLineCompletion}
import scala.tools.nsc.settings.MutableSettings
import scala.collection.JavaConversions._

/**
 * Spark interpreter for Zeppelin.
 */
class SparkInterpreter(properties: Properties) extends Interpreter(properties) with Logging {
  private var sc: SparkContext = null
  private var spark: SparkSession = null
  private var env: SparkEnv = null
  private var sparkListener: JobProgressListener = null
  private var classOutputDir: AbstractFile = null
  private var z: ZeppelinContext = null
  private var interpreter: SparkILoop = null
  private var intp: IMain = null
  private var sparkActiveJobs: SparkActiveJobs = null
  private var out: SparkOutputStream = null
  private var dep: SparkDependencyResolver = null
  private var completor: JLineCompletion = null
  private var binder: Map[String, AnyRef] = null
  private var sparkVersion: SparkVersion = null

  private var sharedInterpreterLock: Integer = new Integer(0)
  private var numReferenceOfSparkContext: AtomicInteger = new AtomicInteger(0)

  out = new SparkOutputStream

  def this(properties: Properties, sc: SparkContext) {
    this(properties)
    this.sc = sc
    env = SparkEnv.get
    sparkListener = setupListeners(this.sc)
  }

  def getSystemDefault(envName: String, propertyName: String, defaultValue: String): String = {
    if (envName != null && !envName.isEmpty) {
      val envValue: String = System.getenv.get(envName)
      if (envValue != null) {
        return envValue
      }
    }
    if (propertyName != null && !propertyName.isEmpty) {
      val propValue: String = System.getProperty(propertyName)
      if (propValue != null) {
        return propValue
      }
    }
    return defaultValue
  }

  def getSparkContext: SparkContext = {
    sharedInterpreterLock synchronized {
      if (sc == null) {
        sc = createSparkContext
        env = SparkEnv.get
        sparkListener = setupListeners(sc)
        sparkActiveJobs = new SparkActiveJobs(sc)
      }
      return sc
    }
  }

  def isSparkContextInitialized: Boolean = {
    sharedInterpreterLock synchronized {
      return sc != null
    }
  }

  def getSparkSession: SparkSession = {
    sharedInterpreterLock synchronized {
      if (spark == null) {
        if (useHiveContext) {
          spark = SparkSession
            .builder
            .enableHiveSupport()
            .config(sc.getConf)
            .getOrCreate()
        }
        else {
          spark = SparkSession
            .builder
            .config(sc.getConf)
            .getOrCreate()
        }
      }
      return spark
    }
  }

  def getDependencyResolver: SparkDependencyResolver = {
    if (dep == null) {
      dep = new SparkDependencyResolver(intp, sc, getProperty("zeppelin.dep.localrepo"), getProperty("zeppelin.dep.additionalRemoteRepository"))
    }
    return dep
  }

  def createSparkContext: SparkContext = {
    logger.info("Create new SparkContext with master=" + getProperty("master"))
    val execUri = System.getenv("SPARK_EXECUTOR_URI")
    val conf = new SparkConf().setMaster(getProperty("master")).setAppName(getProperty("spark.app.name"))
    if (System.getenv("SPARK_HOME") != null) {
      conf.setSparkHome(System.getenv("SPARK_HOME"))
    }
    conf.set("spark.scheduler.mode", "FAIR")
    val intpProperty: Properties = getProperty
    for (k <- intpProperty.keySet) {
      val key: String = k.asInstanceOf[String]
      val `val`: String = asString(intpProperty.get(key))
      if (!key.startsWith("spark.") || !`val`.trim.isEmpty) {
        logger.debug(String.format("SparkConf: key = [%s], value = [%s]", key, `val`))
        conf.set(key, `val`)
      }
    }
    var pysparkBasePath: String = getSystemDefault("SPARK_HOME", null, null)
    var pysparkPath: File = null
    if (null == pysparkBasePath) {
      pysparkBasePath = getSystemDefault("ZEPPELIN_HOME", "zeppelin.home", "../")
      pysparkPath = new File(pysparkBasePath, "interpreter" + File.separator + "spark" + File.separator + "pyspark")
    }
    else {
      pysparkPath = new File(pysparkBasePath, "python" + File.separator + "lib")
    }
    val pythonLibs: Array[String] = Array[String]("pyspark.zip", "py4j-0.9-src.zip", "py4j-0.8.2.1-src.zip")
    val pythonLibUris: ArrayList[String] = new ArrayList[String]
    for (lib <- pythonLibs) {
      val libFile: File = new File(pysparkPath, lib)
      if (libFile.exists) {
        pythonLibUris.add(libFile.toURI.toString)
      }
    }
    pythonLibUris.trimToSize
    if (pythonLibUris.size == 2) {
      try {
        val confValue = conf.get("spark.yarn.dist.files")
        conf.set("spark.yarn.dist.files", confValue + "," + Joiner.on(",").join(pythonLibUris))
      }
      catch {
        case e: NoSuchElementException => {
          conf.set("spark.yarn.dist.files", Joiner.on(",").join(pythonLibUris))
        }
      }
      if (!useSparkSubmit) {
        conf.set("spark.files", conf.get("spark.yarn.dist.files"))
      }
      /*
      TODO(ECH)
      conf.set("spark.submit.pyArchives", Joiner.on(":").join(pythonLibs))
       */
    }
    if (getProperty("master") == "yarn-client") {
      conf.set("spark.yarn.isPython", "true")
    }
    val sparkContext: SparkContext = new SparkContext(conf)
    return sparkContext
  }

  private def useSparkSubmit: Boolean = {
    return null != System.getenv("SPARK_SUBMIT")
  }

  def printREPLOutput: Boolean = {
    return getProperty("zeppelin.spark.printREPLOutput").toBoolean
  }

  def open {
    val urls = getClassloaderUrls
    var settings = new Settings
    if (getProperty("args") != null) {
      val argsArray = getProperty("args").split(" ")
      val argList = new LinkedList[String]
      for (arg <- argsArray) {
        argList.add(arg)
      }
      /*
      TODO(ECH)
      val command: SparkCommandLine = new SparkCommandLine(scala.collection.JavaConversions.asScalaBuffer(argList).toList)
      settings = command.settings
      */
    }
    val pathSettings: MutableSettings#PathSetting = settings.classpath
    var classpath: String = ""
    val paths = currentClassPath
    for (f <- paths) {
      if (classpath.length > 0) {
        classpath += File.pathSeparator
      }
      classpath += f.getAbsolutePath
    }
    if (urls != null) {
      for (u <- urls) {
        if (classpath.length > 0) {
          classpath += File.pathSeparator
        }
        classpath += u.getFile
      }
    }
    val localRepo = getProperty("zeppelin.interpreter.localRepo")
    if (localRepo != null) {
      val localRepoDir: File = new File(localRepo)
      if (localRepoDir.exists) {
        val files: Array[File] = localRepoDir.listFiles
        if (files != null) {
          for (f <- files) {
            if (classpath.length > 0) {
              classpath += File.pathSeparator
            }
            classpath += f.getAbsolutePath
          }
        }
      }
    }
    settings.classpath.value = classpath
    settings.embeddedDefaults(Thread.currentThread.getContextClassLoader)
    val b: MutableSettings#BooleanSetting = settings.usejavacp.asInstanceOf[MutableSettings#BooleanSetting]
    /*
    TODO(ECH)
    b.v_$eq(true)
    settings.scala$tools$nsc$settings$StandardScalaSettings$_setter_$usejavacp_$eq(b)
    */
    System.setProperty("scala.repl.name.line", "line" + this.hashCode + "$")
    sharedInterpreterLock synchronized {
      if (printREPLOutput) {
        this.interpreter = new SparkILoop(Option.empty, new PrintWriter(out))
      }
      else {
        this.interpreter = new SparkILoop(Option.empty, new PrintWriter(Console.out, false))
      }
      interpreter.settings_$eq(settings)
      interpreter.createInterpreter
      intp = interpreter.intp
      intp.setContextClassLoader
      intp.initializeSynchronous
      if (classOutputDir == null) {
        classOutputDir = settings.outputDirs.getSingleOutput.get
      }
      else {
        settings.outputDirs.setSingleOutput(classOutputDir)
        val cl: ClassLoader = intp.classLoader
        try {
          val rootField: Field = cl.getClass.getSuperclass.getDeclaredField("root")
          rootField.setAccessible(true)
          rootField.set(cl, classOutputDir)
        }
        catch {
          case e: Any => {
            logger.error(e.getMessage, e)
          }
        }
      }
      completor = new JLineCompletion(intp)
      sc = getSparkContext
      if (sc.getPoolForName("fair").isEmpty) {
        val schedulingMode = org.apache.spark.scheduler.SchedulingMode.FAIR
        val minimumShare: Int = 0
        val weight: Int = 1
        val pool: Pool = new Pool("fair", schedulingMode, minimumShare, weight)
        sc.taskScheduler.rootPool.addSchedulable(pool)
      }

      sparkVersion = SparkVersion.fromVersionString(sc.version)
      spark = getSparkSession
      dep = getDependencyResolver
      z = new ZeppelinContext(sc, spark, null, dep, getProperty("zeppelin.spark.maxResult").toInt)

      intp.interpret("@transient var _binder = new java.util.HashMap[String, Object]()")

      binder = getValue("_binder").asInstanceOf[Map[String, AnyRef]]
      binder.put("sc", sc)
      binder.put("spark", spark)
      binder.put("z", z)

      intp.interpret("@transient val z = " + "_binder.get(\"z\").asInstanceOf[org.apache.zeppelin.spark.ZeppelinContext]")
      intp.interpret("@transient val sc = " + "_binder.get(\"sc\").asInstanceOf[org.apache.spark.SparkContext]")
      intp.interpret("@transient val spark = " + "_binder.get(\"sqlc\").asInstanceOf[org.apache.spark.sql.SparkSession]")
      intp.interpret("import org.apache.spark.SparkContext._")
      intp.interpret("import spark.implicits._")
      intp.interpret("import spark.sql")
      intp.interpret("import org.apache.spark.sql.functions._")

    }

    /*
    TODO(ECH)
    try {
      this.interpreter.loadFiles(settings)
    }
    catch {
      case e: Any => {
        throw new InterpreterException(e)
      }
    }
     */

    if (localRepo != null) {
      val localRepoDir: File = new File(localRepo)
      if (localRepoDir.exists) {
        val files: Array[File] = localRepoDir.listFiles
        if (files != null) {
          for (f <- files) {
            if (f.getName.toLowerCase.endsWith(".jar")) {
              sc.addJar(f.getAbsolutePath)
              logger.info("sc.addJar(" + f.getAbsolutePath + ")")
            }
            else {
              sc.addFile(f.getAbsolutePath)
              logger.info("sc.addFile(" + f.getAbsolutePath + ")")
            }
          }
        }
      }
    }

    numReferenceOfSparkContext.incrementAndGet

  }

  private def currentClassPath: List[File] = {
    val paths: List[File] = classPath(Thread.currentThread.getContextClassLoader)
    val cps: Array[String] = System.getProperty("java.class.path").split(File.pathSeparator)
    if (cps != null) {
      for (cp <- cps) {
        paths.add(new File(cp))
      }
    }
    paths
  }

  private def classPath(cl: ClassLoader): List[File] = {
    val paths: List[File] = new LinkedList[File]
    if (cl == null) {
      return paths
    }
    if (cl.isInstanceOf[URLClassLoader]) {
      val ucl: URLClassLoader = cl.asInstanceOf[URLClassLoader]
      val urls: Array[URL] = ucl.getURLs
      if (urls != null) {
        for (url <- urls) {
          paths.add(new File(url.getFile))
        }
      }
    }
    paths
  }

  def completion(buf: String, cursor: Int): List[String] = {
    var cur = cursor
    if (buf.length < cursor) {
      cur = buf.length
    }
    var completionText = getCompletionTargetString(buf, cursor)
    if (completionText == null) {
      completionText = ""
      cur = completionText.length
    }
    val c = completor.completer
    val ret = c.complete(completionText, cursor)
    scala.collection.JavaConversions.seqAsJavaList(ret.candidates)
  }

  private def getCompletionTargetString(text: String, cursor: Int): String = {
    val completionSeqCharaters: Array[String] = Array(" ", "\n", "\t")
    var completionEndPosition: Int = cursor
    var completionStartPosition: Int = cursor
    var indexOfReverseSeqPostion: Int = cursor
    var resultCompletionText: String = ""
    var completionScriptText: String = ""
    try {
      completionScriptText = text.substring(0, cursor)
    }
    catch {
      case e: Exception => {
        logger.error(e.toString)
        return null
      }
    }
    completionEndPosition = completionScriptText.length
    val tempReverseCompletionText: String = new StringBuilder(completionScriptText).reverse.toString
    for (seqCharacter <- completionSeqCharaters) {
      indexOfReverseSeqPostion = tempReverseCompletionText.indexOf(seqCharacter)
      if (indexOfReverseSeqPostion < completionStartPosition && indexOfReverseSeqPostion > 0) {
        completionStartPosition = indexOfReverseSeqPostion
      }
    }
    if (completionStartPosition == completionEndPosition) {
      completionStartPosition = 0
    }
    else {
      completionStartPosition = completionEndPosition - completionStartPosition
    }
    resultCompletionText = completionScriptText.substring(completionStartPosition, completionEndPosition)
    return resultCompletionText
  }

  def getValue(name: String): AnyRef = {
    val ret: AnyRef = intp.valueOfTerm(name)
    if (ret.isInstanceOf[None.type]) {
      return null
    }
    else if (ret.isInstanceOf[Some[Any]]) {
      return (ret.asInstanceOf[Some[AnyRef]]).get
    }
    else {
      return ret
    }
  }

  private def getJobGroup(context: InterpreterContext): String = {
    return "zeppelin-" + context.getParagraphId
  }

  /**
   * Interpret a single line.
   */
  def interpret(line: String, context: InterpreterContext): InterpreterResult = {
    if (sparkVersion.isUnsupportedVersion) {
      return new InterpreterResult(Code.ERROR, "Spark " + sparkVersion.toString + " is not supported")
    }
    z.setInterpreterContext(context)
    if (line == null || line.trim.length == 0) {
      return new InterpreterResult(Code.SUCCESS)
    }
    return interpret(line.split("\n"), context)
  }

  def interpret(lines: Array[String], context: InterpreterContext): InterpreterResult = {
    this synchronized {
      z.setGui(context.getGui)
      sc.setJobGroup(getJobGroup(context), "Zeppelin", false)
      val r: InterpreterResult = interpretInput(lines, context)
      sc.clearJobGroup
      return r
    }
  }

  def interpretInput(lines: Array[String], context: InterpreterContext): InterpreterResult = {
    SparkEnv.set(env)
    val linesToRun: Array[String] = Array.fill(lines.length + 1){""}
    var i: Int = 0
    while (i < lines.length) {
      {
        linesToRun(i) = lines(i)
      }
      ({
        i += 1; i - 1
      })
    }
    linesToRun(lines.length) = "print(\"\")"
    Console.setOut(context.out)
    out.setInterpreterOutput(context.out)
    context.out.clear
    var r: InterpreterResult.Code = null
    var incomplete: String = ""
    var inComment: Boolean = false
    var l: Int = 0
    while (l < linesToRun.length) {
      {
        val s: String = linesToRun(l)
        if (l + 1 < linesToRun.length) {
          val nextLine: String = linesToRun(l + 1).trim
          var continuation: Boolean = false
          if (nextLine.isEmpty || nextLine.startsWith("//") || nextLine.startsWith("}") || nextLine.startsWith("object")) {
            continuation = true
          }
          else if (!inComment && nextLine.startsWith("/*")) {
            inComment = true
            continuation = true
          }
          else if (inComment && nextLine.lastIndexOf("*/") >= 0) {
            inComment = false
            continuation = true
          }
          else if (nextLine.length > 1 && nextLine.charAt(0) == '.' && nextLine.charAt(1) != '.' && nextLine.charAt(1) != '/') {
            continuation = true
          }
          else if (inComment) {
            continuation = true
          }
          if (continuation) {
            incomplete += s + "\n"
            /*
            TODO(ECH)
            continue //todo: continue is not supported
             */
          }
        }
        var res: Results.Result = null
        try {
          res = intp.interpret(incomplete + s)
        }
        catch {
          case e: Exception => {
            sc.clearJobGroup
            out.setInterpreterOutput(null)
            logger.info("Interpreter exception", e)
            return new InterpreterResult(Code.ERROR, InterpreterUtils.getMostRelevantMessage(e))
          }
        }
        r = getResultCode(res)
        if (r eq Code.ERROR) {
          sc.clearJobGroup
          out.setInterpreterOutput(null)
          return new InterpreterResult(r, "")
        }
        else if (r eq Code.INCOMPLETE) {
          incomplete += s + "\n"
        }
        else {
          incomplete = ""
        }
      }
      ({
        l += 1; l - 1
      })
    }
    if (r eq Code.INCOMPLETE) {
      sc.clearJobGroup
      out.setInterpreterOutput(null)
      return new InterpreterResult(r, "Incomplete expression")
    }
    else {
      sc.clearJobGroup
      out.setInterpreterOutput(null)
      return new InterpreterResult(Code.SUCCESS)
    }
  }

  def cancel(context: InterpreterContext) {
    sc.cancelJobGroup(getJobGroup(context))
  }

  def getProgress(context: InterpreterContext): Int = {
    val jobGroup: String = getJobGroup(context)
    var completedTasks: Int = 0
    var totalTasks: Int = 0
    val jobs: HashSet[ActiveJob] = sparkActiveJobs.activeJobs()
    if (jobs == null) {
      return 0
    }
    if (jobs == null || jobs.size == 0) {
      return 0
    }
    val it: Iterator[ActiveJob] = jobs.iterator
    while (it.hasNext) {
      val job: ActiveJob = it.next
      val g: String = job.properties.get("spark.jobGroup.id").asInstanceOf[String]
      if (jobGroup == g) {
        var progressInfo: Array[Int] = null
        try {
          val finalStage: AnyRef = job.getClass.getMethod("finalStage").invoke(job)
          if (sparkVersion.getProgress1_0) {
            progressInfo = getProgressFromStage_1_0x(sparkListener, finalStage)
          }
          else {
            progressInfo = getProgressFromStage_1_1x(sparkListener, finalStage)
          }
        }
        catch {
          case e: Any => {
            logger.error("Can't get progress info", e)
            return 0
          }
        }
        totalTasks += progressInfo(0)
        completedTasks += progressInfo(1)
      }
    }
    if (totalTasks == 0) {
      return 0
    }
    return completedTasks * 100 / totalTasks
  }

  private def getProgressFromStage_1_0x(sparkListener: JobProgressListener, stage: AnyRef): Array[Int] = {
    var numTasks: Int = stage.getClass.getMethod("numTasks").invoke(stage).asInstanceOf[Int]
    var completedTasks: Int = 0
    val id: Int = stage.getClass.getMethod("id").invoke(stage).asInstanceOf[Int]
    var completedTaskInfo: AnyRef = null
    completedTaskInfo = JavaConversions.mapAsJavaMap(sparkListener.getClass.getMethod("stageIdToTasksComplete").invoke(sparkListener).asInstanceOf[HashMap[AnyRef, AnyRef]]).get(id)
    if (completedTaskInfo != null) {
      completedTasks += completedTaskInfo.asInstanceOf[Int]
    }
    val parents: List[AnyRef] = JavaConversions.seqAsJavaList(stage.getClass.getMethod("parents").invoke(stage).asInstanceOf[Seq[AnyRef]])
    if (parents != null) {
      import scala.collection.JavaConversions._
      for (s <- parents) {
        val p: Array[Int] = getProgressFromStage_1_0x(sparkListener, s)
        numTasks += p(0)
        completedTasks += p(1)
      }
    }
    return Array[Int](numTasks, completedTasks)
  }

  private def getProgressFromStage_1_1x(sparkListener: JobProgressListener, stage: AnyRef): Array[Int] = {
    var numTasks: Int = stage.getClass.getMethod("numTasks").invoke(stage).asInstanceOf[Int]
    var completedTasks: Int = 0
    val id: Int = stage.getClass.getMethod("id").invoke(stage).asInstanceOf[Int]
    try {
      val stageIdToData: Method = sparkListener.getClass.getMethod("stageIdToData")
      val stageIdData: HashMap[(AnyRef, AnyRef), AnyRef] = stageIdToData.invoke(sparkListener).asInstanceOf[HashMap[(AnyRef, AnyRef), AnyRef]]
      val stageUIDataClass: Class[_] = Class.forName("org.apache.spark.ui.jobs.UIData$StageUIData")
      val numCompletedTasks: Method = stageUIDataClass.getMethod("numCompleteTasks")
      val keys: Set[(AnyRef, AnyRef)] = JavaConverters.setAsJavaSetConverter(stageIdData.keySet).asJava
      import scala.collection.JavaConversions._
      for (k <- keys) {
        if (id == k._1.asInstanceOf[Int]) {
          val uiData: AnyRef = stageIdData.get(k).get
          completedTasks += numCompletedTasks.invoke(uiData).asInstanceOf[Int]
        }
      }
    }
    catch {
      case e: Exception => {
        logger.error("Error on getting progress information", e)
      }
    }
    val parents: List[AnyRef] = JavaConversions.seqAsJavaList(stage.getClass.getMethod("parents").invoke(stage).asInstanceOf[Seq[AnyRef]])
    if (parents != null) {
      import scala.collection.JavaConversions._
      for (s <- parents) {
        val p: Array[Int] = getProgressFromStage_1_1x(sparkListener, s)
        numTasks += p(0)
        completedTasks += p(1)
      }
    }
    return Array[Int](numTasks, completedTasks)
  }

  private def getResultCode(r: Results.Result): InterpreterResult.Code = {
    if (r.isInstanceOf[Results.Success.type]) {
      return Code.SUCCESS
    }
    else if (r.isInstanceOf[Results.Incomplete.type]) {
      return Code.INCOMPLETE
    }
    else {
      return Code.ERROR
    }
  }

  def close {
    logger.info("Close interpreter")
    if (numReferenceOfSparkContext.decrementAndGet == 0) {
      sc.stop
      sc = null
    }
    intp.close
  }

  def getFormType: Interpreter.FormType = {
    return FormType.NATIVE
  }

  def getJobProgressListener: JobProgressListener = {
    return sparkListener
  }

  override def getScheduler: Scheduler = {
    return SchedulerFactory.singleton.createOrGetFIFOScheduler(classOf[SparkInterpreter].getName + this.hashCode)
  }

  def getZeppelinContext: ZeppelinContext = {
    return z
  }

  def getSparkVersion: SparkVersion = {
    return sparkVersion
  }

  private def useHiveContext: Boolean = {
    return getProperty("zeppelin.spark.useHiveContext").toBoolean
  }

  private def setupListeners(context: SparkContext): JobProgressListener = {
    val pl: JobProgressListener = new JobProgressListener(context.getConf)
    context.addSparkListener(pl)
    pl
  }

  private def asString(o: AnyRef): String = {
    return if ((o.isInstanceOf[String])) o.asInstanceOf[String] else ""
  }

}
