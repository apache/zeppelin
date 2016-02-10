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

import org.apache.spark.SparkRBackend
import org.ddahl.rscala.callback._

object ZeppelinR {

  val css =
    """
      |<style type="text/css">
      |* {
      |  font-family: Monaco,Menlo,"Ubuntu Mono",Consolas,source-code-pro,monospace !important;
      |  font-size:	12px !important;
      |  color:	rgb(33,​ 33,​ 33) !important;
      |  line-height: 17.15px !important;
      |}
      |pre {
      |  white-space: pre-wrap !important;
      |}
      |</script>
    """.stripMargin

  private val R = RClient()

  def open(master: String = "local[*]", sparkHome: String = "/opt/spark", sparkInterpreter: SparkInterpreter): Unit = {

    eval(
      s"""
         |Sys.setenv(SPARK_HOME="$sparkHome")
         |.libPaths(c(file.path(Sys.getenv("SPARK_HOME"), "R", "lib"), .libPaths()))
         |library(SparkR)
       """.stripMargin
    )

    // See ./core/src/main/scala/org/apache/spark/deploy/RRunner.scala for RBackend usage
    val port = SparkRBackend.init()
    SparkRBackend.start()
    eval(
      s"""
         |SparkR:::connectBackend("localhost", ${port})
         |""".stripMargin)

    // scStartTime is needed by R/pkg/R/sparkR.R
    eval(
      """
        |assign(".scStartTime", as.integer(Sys.time()), envir = SparkR:::.sparkREnv)
      """.stripMargin)

    ZeppelinRContext.setSparkContext(sparkInterpreter.getSparkContext())
    eval(
      """
        |assign(".sc", SparkR:::callJStatic("org.apache.zeppelin.spark.ZeppelinRContext", "getSparkContext"), envir = SparkR:::.sparkREnv)
        |""".stripMargin)
    eval(
      """
        |assign("sc", get(".sc", envir = SparkR:::.sparkREnv), envir=.GlobalEnv)
      """.stripMargin)

    ZeppelinRContext.setSqlContext(sparkInterpreter.getSQLContext())
    eval(
      """
         |assign(".sqlc", SparkR:::callJStatic("org.apache.zeppelin.spark.ZeppelinRContext", "getSqlContext"), envir = SparkR:::.sparkREnv)
         |""".stripMargin)
    eval(
      """
         |assign("sqlContext", get(".sqlc", envir = SparkR:::.sparkREnv), envir = .GlobalEnv)
         |""".stripMargin)

    ZeppelinRContext.setZepplinContext(sparkInterpreter.getZeppelinContext())
    eval(
      """
         |assign(".zeppelinContext", SparkR:::callJStatic("org.apache.zeppelin.spark.ZeppelinRContext", "getZeppelinContext"), envir = .GlobalEnv)
         |""".stripMargin
    )

    eval(
      """
         |z.put <- function(name, object) {
         |  SparkR:::callJMethod(.zeppelinContext, "put", name, object)
         |}
         |z.get <- function(name) {
         |  SparkR:::callJMethod(.zeppelinContext, "get", name)
         |}
         |""".stripMargin
    )

    eval(
      """
        |library("knitr")
      """.stripMargin)

  }

  def eval(command: String): Any = {
    try {
      R.eval(command)
    } catch {
      case e: Exception => throw new RuntimeException(e.getMessage + " - Given R command=" + command)
    }
  }

  def set(key: String, value: AnyRef): Unit = {
    R.set(key, value)
  }

  def get(key: String): Any = {
    R.get(key)._1
  }

  def getS0(key: String): String = {
    R.getS0(key)
  }

  def close():Unit = {
    R.eval("""
         |sparkR.stop()
       """.stripMargin
    )
  }

}
