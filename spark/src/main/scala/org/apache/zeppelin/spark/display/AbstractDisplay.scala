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
package org.apache.zeppelin.spark.display

import org.apache.spark.SparkContext
import org.apache.spark.sql.{QueryExecutionHelper, Row}
import org.apache.zeppelin.interpreter.{InterpreterContext, InterpreterException}
import org.slf4j.{LoggerFactory, Logger}

/**
 *  Trait to display Scala traversable
 *  and SchemaRDD or DataFrame
 */
trait AbstractDisplay {

  private val logger:Logger = LoggerFactory.getLogger(classOf[AbstractDisplay])

  def printFormattedData[T](traversable: Traversable[T], columnLabels: String*): String = {

    if(logger.isDebugEnabled()) logger.debug(s"Print collection $traversable with columns label $columnLabels")

    val providedLabelCount: Int = columnLabels.size
    var maxColumnCount:Int = 1
    val headers = new StringBuilder("%table ")

    val data = new StringBuilder("")

    traversable.foreach(instance => {
      require(instance.isInstanceOf[Product],
        throw new InterpreterException(s"$instance should be an instance of scala.Product (case class or tuple)"))

      val product = instance.asInstanceOf[Product]
      maxColumnCount = math.max(maxColumnCount,product.productArity)
      data.append(product.productIterator.mkString("\t")).append("\n")
    })

    if (providedLabelCount > maxColumnCount) {
      headers.append(columnLabels.take(maxColumnCount).mkString("\t")).append("\n")
    } else if (providedLabelCount < maxColumnCount) {
      val missingColumnHeaders = ((providedLabelCount+1) to maxColumnCount).foldLeft[String](""){
        (stringAccumulator,index) =>  if (index==1) s"Column$index" else s"$stringAccumulator\tColumn$index"
      }

      headers.append(columnLabels.mkString("\t")).append(missingColumnHeaders).append("\n")
    } else {
      headers.append(columnLabels.mkString("\t")).append("\n")
    }

    headers.append(data)
    headers.toString
  }

  def printDFOrSchemaRDD(sc: SparkContext, interpreterContext: InterpreterContext, df: Any, maxResult: Int): String = {

    if(logger.isDebugEnabled()) logger.debug(s"Print DataFrame/SchemaRDD $df limiting to $maxResult elements")

    sc.setJobGroup("zeppelin-" + interpreterContext.getParagraphId, "Zeppelin", false)

    val queryExecutionHelper: QueryExecutionHelper = new QueryExecutionHelper(sc)
    try {
      val rows: Array[Row] = df.getClass().getMethod("take", classOf[Int]).invoke(df, new Integer(maxResult)).asInstanceOf[Array[Row]]
      val attributes: Seq[String] = queryExecutionHelper.schemaAttributes(df).map(_.name)
      val msg = new StringBuilder("")
      try {
        val headerCount = attributes.size
        msg.append("%table ").append(attributes.mkString("", "\t", "\n"))

        rows.foreach(row => {
          val tableRow: String = (0 until headerCount).map(index =>
            if (row.isNullAt(index)) "null" else row(index).toString
          ).mkString("", "\t", "\n")
          msg.append(tableRow)
        })
      } catch {
        case e: Throwable => {
          sc.clearJobGroup()
          throw new InterpreterException(e)
        }
      }
      msg.toString
    } catch {
      case e: Throwable => {
        sc.clearJobGroup()
        throw new InterpreterException(e)
      }
    }
  }
}