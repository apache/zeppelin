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

package org.apache.zeppelin.scio

import com.google.api.services.bigquery.model.TableSchema
import com.spotify.scio.bigquery._
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord

import scala.reflect.ClassTag

/**
 * Set of helpers for Zeppelin Display system.
 */
private[scio] object DisplayHelpers {

  private[scio] val sCollectionEmptyMsg =
    "\n%html <font color=red>Result SCollection is empty!</font>\n"
  private val maxResults = Integer.getInteger("zeppelin.scio.maxResult", 1000)
  private[scio] val tab = "\t"
  private[scio] val newline = "\n"
  private[scio] val table = "%table"
  private[scio] val endTable = "%text"
  private[scio] val rowLimitReachedMsg =
    s"$newline<font color=red>Results are limited to " + maxResults + s" rows.</font>$newline"
  private[scio] val bQSchemaIncomplete =
    s"$newline<font color=red>Provided BigQuery Schema has not fields!</font>$newline"

  private def notifyIfTruncated(it: Iterator[_]): Unit = {
    if(it.hasNext) println(rowLimitReachedMsg)
  }

  /**
   * Displays [[AnyVal]] values from given [[Iterator]].
   */
  private[scio] def displayAnyVal[T: ClassTag](it: Iterator[T], printer: (T) => String): Unit = {
    if (it.isEmpty) {
      println(sCollectionEmptyMsg)
    } else {
      println(s"$table value$newline${it.take(maxResults).map(printer).mkString(newline)}")
      println(endTable)
      notifyIfTruncated(it)
    }
  }

  /**
   * Displays [[String]] values from given [[Iterator]].
   */
  private[scio] def displayString[T: ClassTag](it: Iterator[T], printer: (T) => String): Unit = {
    if (it.isEmpty) {
      println(sCollectionEmptyMsg)
    } else {
      println(s"$table value$newline${it.take(maxResults).map(printer).mkString(newline)}")
      println(endTable)
      notifyIfTruncated(it)
    }
  }

  /**
   * Displays [[com.google.cloud.dataflow.sdk.values.KV]] values from given [[Iterator]].
   */
  private[scio] def displayKV[K: ClassTag, V: ClassTag](it: Iterator[(K,V)]): Unit = {
    if (it.isEmpty) {
      println(sCollectionEmptyMsg)
    } else {
      val content = it.take(maxResults).map{ case (k, v) => s"$k$tab$v" }.mkString(newline)
      println(s"$table key${tab}value$newline$content")
      println(endTable)
      notifyIfTruncated(it)
    }
  }

  /**
   * Displays [[Product]] values from given [[Iterator]].
   */
  private[scio] def displayProduct[T: ClassTag](it: Iterator[T])
                                               (implicit ev: T <:< Product): Unit = {
    if (it.isEmpty) {
      println(sCollectionEmptyMsg)
    } else {
      val first = it.next()
      //TODO is this safe field name to value iterator?
      val fieldNames = first.getClass.getDeclaredFields.map(_.getName)

      val header = fieldNames.mkString(tab)
      val firstStr = first.productIterator.mkString(tab)
      val content = it.take(maxResults - 1).map(_.productIterator.mkString(tab)).mkString(newline)
      println(s"$table $header$newline$firstStr$newline$content")
      println(endTable)
      notifyIfTruncated(it)
    }
  }

  /**
   * Displays Avro values from given [[Iterator]] using optional [[Schema]].
   * @param schema optional "view" schema, otherwise schema is inferred from the first object
   */
  private[scio] def displayAvro[T: ClassTag](it: Iterator[T], schema: Schema = null)
                                            (implicit ev: T <:< GenericRecord): Unit = {
    if (it.isEmpty) {
      println(sCollectionEmptyMsg)
    } else {
      val first = it.next()
      import collection.JavaConverters._

      val fieldNames = if (schema != null) {
        schema.getFields.iterator().asScala.map(_.name()).toArray
      } else {
        first.getSchema.getFields.iterator.asScala.map(_.name()).toArray
      }

      val header = fieldNames.mkString(tab)
      val firstStr = fieldNames.map(first.get).mkString(tab)
      val content = it.take(maxResults - 1)
        .map(r => fieldNames.map(r.get).mkString(tab))
        .mkString(newline)
      println(s"$table $header$newline$firstStr$newline$content")
      println(endTable)
      notifyIfTruncated(it)
    }
  }

  /**
   * Displays [[TableRow]] values from given [[Iterator]] using specified [[TableSchema]].
   */
  private[scio] def displayBQTableRow[T: ClassTag](it: Iterator[T], schema: TableSchema)
                                                  (implicit ev: T <:< TableRow) : Unit = {
    if (it.isEmpty) {
      println(sCollectionEmptyMsg)
    } else {
      import collection.JavaConverters._
      val fieldsOp = Option(schema.getFields)
      fieldsOp match {
        case None => println(bQSchemaIncomplete)
        case Some(f) => {
          val fields = f.asScala.map(_.getName).toArray

          val header = fields.mkString(tab)

          val content = it.take(maxResults)
            .map(r => fields.map(r.get).mkString(tab))
            .mkString(newline)

          println(s"$table $header$newline$content")
          println(endTable)
          notifyIfTruncated(it)
        }
      }
    }
  }

}
