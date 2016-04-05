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

package org.apache.zeppelin.rinterpreter

import java.io._
import java.nio.file.{Files, Paths}
import java.util.Properties

import org.apache.spark.SparkContext
import org.apache.spark.api.r.RBackendHelper
import org.apache.spark.sql.SQLContext
import org.apache.zeppelin.interpreter._
import org.apache.zeppelin.rinterpreter.rscala.RClient._
import org.apache.zeppelin.rinterpreter.rscala._
import org.apache.zeppelin.scheduler._
import org.apache.zeppelin.spark.{SparkInterpreter, ZeppelinContext}
import org.slf4j._

import scala.collection.JavaConversions._

// TODO:  Setup rmr, etc.
// TODO:  Stress-test spark.  What happens on close?  Etc.

private[rinterpreter] class RContext(private val sockets: ScalaSockets,
                                     debug: Boolean) extends RClient(sockets.in, sockets.out, debug) {

  private val logger: Logger = RContext.logger
  lazy val getScheduler: Scheduler = SchedulerFactory.singleton().createOrGetFIFOScheduler(this.hashCode().toString)

  val backend: RBackendHelper = RBackendHelper()
  private var sc: Option[SparkContext] = None
  private var sql: Option[SQLContext] = None
  private var z: Option[ZeppelinContext] = None

  val rPkgMatrix = collection.mutable.HashMap[String,Boolean]()

  var isOpen: Boolean = false
  private var isFresh : Boolean = true

  private var property: Properties = null
  private[rinterpreter] var sparkRStarted : Boolean = false

  override def toString() : String = s"""${super.toString()}
       |\t Open: $isOpen Fresh: $isFresh SparkStarted: $sparkRStarted
       |\t Progress: $progress
       |\t Sockets: ${sockets.toString()}
     """.stripMargin

  var progress: Int = 0

  def getProgress: Int = {
    return progress
  }

  def setProgress(i: Int) : Unit = {
    progress = i % 100
  }

  def incrementProgress(i: Int) : Unit = {
    progress = (progress + i) % 100
  }

  // handle properties this way so it can be a mutable object shared with the R Interpreters
  def setProperty(properties: Properties): Unit = synchronized {
    if (property == null) property = properties
    else property.putAll(properties)
  }

  def open(startSpark : Option[SparkInterpreter]): Unit = synchronized {
    if (isOpen && sparkRStarted) {
      logger.trace("Reusing rContext.")
      return
    }
    testRPackage("rzeppelin", fail = true, message =
      "The rinterpreter cannot run without the rzeppelin package, which was included in your distribution.")
    startSpark match {
      case Some(x : SparkInterpreter) => {
        sparkStartup(x)
      }
      case _ => logger.error("Could not find a SparkInterpreter")
    }
    isOpen = true
  }
  private def sparkStartup(startSpark : SparkInterpreter): Unit = try {
    val sparkHome: String = System.getenv("SPARK_HOME") match {
          case null => {
            logger.error("SPARK_HOME is not set. The R Interpreter will start without Spark.")
            return
          }
          case y => y
        }
    testRPackage("SparkR", fail = true, path = sparkHome)
    if (startSpark.getSparkVersion() == null) throw new RuntimeException("No spark version")
    if (!startSpark.getSparkVersion().isSparkRSupported) throw new RuntimeException("SparkR requires Spark 1.4 or later")
    sc = Some(startSpark.getSparkContext())
    sql = Some(startSpark.getSQLContext())
    z = Some(startSpark.getZeppelinContext())
    logger.trace("Registered Spark Contexts")
    backend.init()
    backend.start()
    if (!backend.backendThread.isAlive) throw new RuntimeException("SparkR could not startup because the Backend Thread is not alive")
    logger.trace("Started Spark Backend")
    eval( s"""SparkR:::connectBackend("localhost", ${backend.port})""")
    logger.trace("SparkR backend connected")
    initializeSparkR(sc.get, sql.get, z.get)
    logger.info("Initialized SparkR")
    sparkRStarted = true
  } catch {
    case e: Exception => throw new RuntimeException("""
      Could not connect R to Spark.  If the stack trace is not clear,
    check whether SPARK_HOME is set properly.""", e)
  }

  private def initializeSparkR(sc : SparkContext, sql : SQLContext, z : ZeppelinContext) : Unit = synchronized {

    logger.trace("Getting a handle to the JavaSparkContext")

    eval("assign(\".scStartTime\", as.integer(Sys.time()), envir = SparkR:::.sparkREnv)")
    RStatics.setSC(sc)
    eval(
      """
        |assign(
        |".sparkRjsc",
        |SparkR:::callJStatic("org.apache.zeppelin.rinterpreter.RStatics",
        | "getJSC"),
        | envir = SparkR:::.sparkREnv)""".stripMargin)

    eval("assign(\"sc\", get(\".sparkRjsc\", envir = SparkR:::.sparkREnv), envir=.GlobalEnv)")

    logger.trace("Established SparkR Context")

    val sqlEnvName = sql match {
      case null => throw new RuntimeException("Tried to initialize SparkR without setting a SQLContext")
      case x : org.apache.spark.sql.hive.HiveContext => ".sparkRHivesc"
      case x : SQLContext => ".sparkRSQLsc"
    }
    RStatics.setSQL(sql)
    eval(
      s"""
        |assign(
        |"${sqlEnvName}",
        |SparkR:::callJStatic("org.apache.zeppelin.rinterpreter.RStatics",
        | "getSQL"),
        | envir = SparkR:::.sparkREnv)""".stripMargin)
    eval(
      s"""
         |assign("sqlContext",
         |get("$sqlEnvName",
         |envir = SparkR:::.sparkREnv),
         |envir = .GlobalEnv)
       """.stripMargin)

    logger.trace("Proving spark")
    val proof = evalS1("names(SparkR:::.sparkREnv)")
    logger.info("Proof of spark is : " + proof.mkString)

    RStatics.setZ(z)

    RStatics.setrCon(this)
    eval(
      s"""
         |assign(".rContext",
         |  SparkR:::callJStatic("org.apache.zeppelin.rinterpreter.RStatics",
         | "getRCon"),
         | envir = .GlobalEnv)
     """.stripMargin
    )
  }

  def close(): Unit = synchronized {
    if (isOpen) {
      if (sparkRStarted) {
        try {
          eval("SparkR:::sparkR.stop()")
        } catch {
          case e: RException => {}
          case e: Exception => logger.error("Error closing SparkR", e)
        }
      }
      try {
        backend.close
        backend.backendThread.stop()
      } catch {
        case e: Exception => logger.error("Error closing RContext ", e)
      }
      try {
        exit()
      } catch {
        case e: Exception => logger.error("Shutdown error", e)
      }
    }
    isOpen = false
  }


  private[rinterpreter] def testRPackage(pack: String,
                                         fail: Boolean = false,
                                         license: Boolean = false,
                                         message: String = "",
                                          path : String = ""): Boolean = synchronized {


    rPkgMatrix.get(pack) match {
      case Some(x: Boolean) => return x
      case None => {}
    }

    evalB0( s"""require('$pack',quietly=TRUE, lib.loc="$path/R/lib/")""") match {
      case true => {
        rPkgMatrix.put(pack, true)
        return (true)
      }
      case false => {
        evalB0(s"require('$pack', quietly=TRUE)") match {
          case true => {
            rPkgMatrix.put(pack, true)
            return true
          }
          case false => {
            rPkgMatrix.put(pack, false)
            val failMessage =
              s"""The $pack package could not be loaded. """ + {
                if (license) "We cannot install it for you because it is published under the GPL3 license."
                else ""
              } + message
            logger.error(failMessage)
            if (fail) throw new RException(failMessage)
            return (false)
          }
        }
      }
    }
  }

  logger.info("RContext Finished Starting")
}

object RContext {
  val logger: Logger = LoggerFactory.getLogger(getClass)

  logger.trace("Inside the RContext Object")
  private val contextMap : collection.mutable.HashMap[String, RContext] = collection.mutable.HashMap[String,RContext]()

  // This function is here to work around inconsistencies in the SparkInterpreter startup sequence
  // that caused testing issues
  private[rinterpreter] def resetRcon() : Boolean = synchronized {
    contextMap foreach((con) => {
      con._2.close()
      if (con._2.isOpen) throw new RuntimeException("Failed to close an existing RContext")
      contextMap.remove(con._1)
    })
    return true
  }

  def apply( property: Properties, id : String): RContext = synchronized {
        contextMap.get(id) match {
          case Some(x : RContext) if x.isFresh || x.isOpen => return(x)
          case Some(x : RContext) => resetRcon()
          case _ => {}
        }
        val debug: Boolean = property.getProperty("rscala.debug", "false").toBoolean
        val timeout: Int = property.getProperty("rscala.timeout", "60").toInt
        import scala.sys.process._
        logger.trace("Creating processIO")
        var cmd: PrintWriter = null
        val command = RClient.defaultRCmd +: RClient.defaultArguments
        val processCmd = Process(command)

        val processIO = new ProcessIO(
          o => {
            cmd = new PrintWriter(o)
          },
          reader("STDOUT DEBUG: "),
          reader("STDERR DEBUG: "),
          true
        )
        val portsFile = File.createTempFile("rscala-", "")
        val processInstance = processCmd.run(processIO)
        // Find rzeppelin
        val libpath : String = if (Files.exists(Paths.get("R/lib"))) "R/lib"
        else if (Files.exists(Paths.get("../R/lib"))) "../R/lib"
        else throw new RuntimeException("Could not find rzeppelin - it must be in either R/lib or ../R/lib")
        val snippet =
          s"""
library(lib.loc="$libpath", rzeppelin)
rzeppelin:::rServe(rzeppelin:::newSockets('${portsFile.getAbsolutePath.replaceAll(File.separator, "/")}',debug=${if (debug) "TRUE" else "FALSE"},timeout=${timeout}))
q(save='no')"""
        while (cmd == null) Thread.sleep(100)
        cmd.println(snippet)
        cmd.flush()
        val sockets = RClient.makeSockets(portsFile.getAbsolutePath)
        sockets.out.writeInt(RClient.Protocol.OK)
        sockets.out.flush()
        val packVersion = RClient.readString(sockets.in)
        if (packVersion != org.apache.zeppelin.rinterpreter.rscala.Version) {
          logger.warn("Connection to R started but versions don't match " + packVersion + " " + org.apache.zeppelin.rinterpreter.rscala.Version)
        } else {
          logger.trace("Connected to a new R Session")
        }
        val context = new RContext(sockets, debug)
        context.setProperty(property)
        contextMap.put(id, context)
        context
  }
}

