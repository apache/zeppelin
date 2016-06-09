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

import java.lang.reflect.{InvocationTargetException, Method}
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger

import grizzled.slf4j.Logging
import org.apache.spark.sql.SparkSession
import org.apache.zeppelin.interpreter.Interpreter.FormType
import org.apache.zeppelin.interpreter.InterpreterResult.Code
import org.apache.zeppelin.interpreter._
import org.apache.zeppelin.scheduler.{SchedulerFactory, Scheduler}

/**
 * Spark SQL interpreter for Zeppelin.
 */
class SparkSqlInterpreter(properties: Properties) extends Interpreter(properties) with Logging {
  private var num: AtomicInteger = new AtomicInteger(0)

  private var maxResult: Int = 0

  private def getJobGroup(context: InterpreterContext): String = {
    return "zeppelin-" + context.getParagraphId
  }

  def open {
    this.maxResult = getProperty("zeppelin.spark.maxResult").toInt
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

  def concurrentSQL: Boolean = {
    return java.lang.Boolean.parseBoolean(getProperty("zeppelin.spark.concurrentSQL"))
  }

  def close {
  }

  def interpret(st: String, context: InterpreterContext): InterpreterResult = {
    var spark: SparkSession = null
    val sparkInterpreter: SparkInterpreter = getSparkInterpreter
    if (sparkInterpreter.getSparkVersion.isUnsupportedVersion) {
      return new InterpreterResult(Code.ERROR, "Spark " + sparkInterpreter.getSparkVersion.toString + " is not supported")
    }
    spark = getSparkInterpreter.getSparkSession
    val sc: SparkContext = spark.sparkContext
    if (concurrentSQL) {
      sc.setLocalProperty("spark.scheduler.pool", "fair")
    }
    else {
      sc.setLocalProperty("spark.scheduler.pool", null)
    }
    sc.setJobGroup(getJobGroup(context), "Zeppelin", false)
    var rdd: AnyRef = null
    try {
      val sqlMethod: Method = spark.getClass.getMethod("sql", classOf[String])
      rdd = sqlMethod.invoke(spark, st)
    }
    catch {
      case ite: InvocationTargetException => {
        if (getProperty("zeppelin.spark.sql.stacktrace").toBoolean) {
          throw new InterpreterException(ite)
        }
        logger.error("Invocation target exception", ite)
        val msg: String = ite.getTargetException.getMessage + "\nset zeppelin.spark.sql.stacktrace = true to see full stacktrace"
        return new InterpreterResult(Code.ERROR, msg)
      }
      case e: Any => {
        throw new InterpreterException(e)
      }
    }
    /*
    TODO(ECH)
    val msg: String = ZeppelinContext.showDF(sc, context, rdd, maxResult)
     */
    val msg = ""
    sc.clearJobGroup
    return new InterpreterResult(Code.SUCCESS, msg)
  }

  def cancel(context: InterpreterContext) {
    val spark = getSparkInterpreter.getSparkSession
    val sc = spark.sparkContext
    sc.cancelJobGroup(getJobGroup(context))
  }

  def getFormType: Interpreter.FormType = {
    return FormType.SIMPLE
  }

  def getProgress(context: InterpreterContext): Int = {
    val sparkInterpreter: SparkInterpreter = getSparkInterpreter
    return sparkInterpreter.getProgress(context)
  }

  override def getScheduler: Scheduler = {
    if (concurrentSQL) {
      val maxConcurrency: Int = 10
      return SchedulerFactory.singleton.createOrGetParallelScheduler(classOf[SparkSqlInterpreter].getName + this.hashCode, maxConcurrency)
    }
    else {
      val intp: Interpreter = getInterpreterInTheSameSessionByClassName(classOf[SparkInterpreter].getName)
      if (intp != null) {
        return intp.getScheduler
      }
      else {
        return null
      }
    }
  }

  override def completion(buf: String, cursor: Int): java.util.List[String] = {
    return null
  }

}
