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

package org.apache.zeppelin.spark.utils

import java.lang.StringBuilder

import org.apache.spark.rdd.RDD

import scala.collection.IterableLike

object DisplayUtils {

  implicit def toDisplayRDDFunctions[T <: Product](rdd: RDD[T]): DisplayRDDFunctions[T] = new DisplayRDDFunctions[T](rdd)

  implicit def toDisplayTraversableFunctions[T <: Product](traversable: Traversable[T]): DisplayTraversableFunctions[T] = new DisplayTraversableFunctions[T](traversable)

  def html(htmlContent: String = "") = s"%html $htmlContent"

  def img64(base64Content: String = "") = s"%img $base64Content"

  def img(url: String) = s"<img src='$url' />"
}

trait DisplayCollection[T <: Product] {

  def printFormattedData(traversable: Traversable[T], columnLabels: String*): Unit = {
    val providedLabelCount: Int = columnLabels.size
    var maxColumnCount:Int = 1
    val headers = new StringBuilder("%table ")

    val data = new StringBuilder("")

    traversable.foreach(tuple => {
      maxColumnCount = math.max(maxColumnCount,tuple.productArity)
      data.append(tuple.productIterator.mkString("\t")).append("\n")
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

    print(headers.toString)
  }

}

class DisplayRDDFunctions[T <: Product] (val rdd: RDD[T]) extends DisplayCollection[T] {

  def display(columnLabels: String*)(implicit sparkMaxResult: SparkMaxResult): Unit = {
    printFormattedData(rdd.take(sparkMaxResult.maxResult), columnLabels: _*)
  }

  def display(sparkMaxResult:Int, columnLabels: String*): Unit = {
    printFormattedData(rdd.take(sparkMaxResult), columnLabels: _*)
  }
}

class DisplayTraversableFunctions[T <: Product] (val traversable: Traversable[T]) extends DisplayCollection[T] {

  def display(columnLabels: String*): Unit = {
    printFormattedData(traversable, columnLabels: _*)
  }
}

class SparkMaxResult(val maxResult: Int) extends Serializable
