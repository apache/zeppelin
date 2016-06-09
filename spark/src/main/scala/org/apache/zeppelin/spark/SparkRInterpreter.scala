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

import grizzled.slf4j.Logging
import org.apache.zeppelin.interpreter.Interpreter.FormType
import org.apache.zeppelin.spark.ZeppelinRDisplay.render
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.spark.SparkRBackend
import org.apache.zeppelin.interpreter._
import org.apache.zeppelin.scheduler.Scheduler
import org.apache.zeppelin.scheduler.SchedulerFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.ArrayList
import java.util.List
import java.util.Properties

class SparkRInterpreter(properties: Properties) extends Interpreter(properties) with Logging {
  private var renderOptions: String = null
  private var zeppelinR: ZeppelinR = null

  def open {
    val rCmdPath: String = getProperty("zeppelin.R.cmd")
    var sparkRLibPath: String = null
    if (System.getenv("SPARK_HOME") != null) {
      sparkRLibPath = System.getenv("SPARK_HOME") + "/R/lib"
    }
    else {
      sparkRLibPath = System.getenv("ZEPPELIN_HOME") + "/interpreter/spark/R/lib"
      System.setProperty("spark.test.home", System.getenv("ZEPPELIN_HOME") + "/interpreter/spark")
    }
    SparkRBackend.backend synchronized {
      if (!SparkRBackend.isStarted) {
        SparkRBackend.init
        SparkRBackend.start
      }
    }
    val port: Int = SparkRBackend.port
    val sparkInterpreter: SparkInterpreter = getSparkInterpreter
    ZeppelinRContext.setSparkContext(sparkInterpreter.getSparkContext)
    ZeppelinRContext.setSqlContext(sparkInterpreter.getSQLContext)
    ZeppelinRContext.setZepplinContext(sparkInterpreter.getZeppelinContext)
    zeppelinR = new ZeppelinR(rCmdPath, sparkRLibPath, port)
    try {
      zeppelinR.open
    }
    catch {
      case e: IOException => {
        logger.error("Exception while opening SparkRInterpreter", e)
        throw new InterpreterException(e)
      }
    }
    if (useKnitr) {
      zeppelinR.eval("library('knitr')")
    }
    renderOptions = getProperty("zeppelin.R.render.options")
  }

  def interpret(lines: String, interpreterContext: InterpreterContext): InterpreterResult = {
    var imageWidth: String = getProperty("zeppelin.R.image.width")
    val sl: Array[String] = lines.split("\n")
    var commands: String = lines
    if (sl(0).contains("{") && sl(0).contains("}")) {
      val jsonConfig: String = sl(0).substring(sl(0).indexOf("{"), sl(0).indexOf("}") + 1)
      val m: ObjectMapper = new ObjectMapper
      try {
        val rootNode: JsonNode = m.readTree(jsonConfig)
        val imageWidthNode: JsonNode = rootNode.path("imageWidth")
        if (!imageWidthNode.isMissingNode) imageWidth = imageWidthNode.textValue
      }
      catch {
        case e: Exception => {
          logger.warn("Can not parse json config: " + jsonConfig, e)
        }
      } finally {
        commands = lines.replace(jsonConfig, "")
      }
    }
    try {
      if (useKnitr) {
        zeppelinR.setInterpreterOutput(null)
        zeppelinR.set(".zcmd", "\n```{r " + renderOptions + "}\n" + commands + "\n```")
        zeppelinR.eval(".zres <- knit2html(text=.zcmd)")
        val html: String = zeppelinR.getS0(".zres")
        val rDisplay: RDisplay = render(html, imageWidth)
        return new InterpreterResult(rDisplay.code, rDisplay.`type`, rDisplay.content)
      }
      else {
        zeppelinR.setInterpreterOutput(interpreterContext.out)
        zeppelinR.eval(lines)
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, "")
      }
    }
    catch {
      case e: Exception => {
        logger.error("Exception while connecting to R", e)
        return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage)
      }
    } finally {
      try {
      }
      catch {
        case e: Exception => {
        }
      }
    }
  }

  def close {
    zeppelinR.close
  }

  def cancel(context: InterpreterContext) {
  }

  def getFormType: Interpreter.FormType = {
    return FormType.NONE
  }

  def getProgress(context: InterpreterContext): Int = {
    return 0
  }

  override def getScheduler: Scheduler = {
    return SchedulerFactory.singleton.createOrGetFIFOScheduler(classOf[SparkRInterpreter].getName + this.hashCode)
  }

  def completion(buf: String, cursor: Int): List[String] = {
    return new ArrayList[String]
  }

  private def getSparkInterpreter: SparkInterpreter = {
    var `lazy`: LazyOpenInterpreter = null
    var spark: SparkInterpreter = null
    var p: Interpreter = getInterpreterInTheSameSessionByClassName(classOf[SparkInterpreter].getName)
    while (p.isInstanceOf[WrappedInterpreter]) {
      if (p.isInstanceOf[LazyOpenInterpreter]) {
        `lazy` = p.asInstanceOf[LazyOpenInterpreter]
      }
      p = (p.asInstanceOf[WrappedInterpreter]).getInnerInterpreter
    }
    spark = p.asInstanceOf[SparkInterpreter]
    if (`lazy` != null) {
      `lazy`.open
    }
    return spark
  }

  private def useKnitr: Boolean = {
    try {
      return java.lang.Boolean.parseBoolean(getProperty("zeppelin.R.knitr"))
    }
    catch {
      case e: Exception => {
        return false
      }
    }
  }
}
