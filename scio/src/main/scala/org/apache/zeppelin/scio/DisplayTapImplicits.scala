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
import com.spotify.scio.bigquery.TableRow
import com.spotify.scio.io.Tap
import com.spotify.scio._
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord

import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * Implicit Zeppelin display helpers for [[Tap]] and [[Future]] of a [[Tap]].
 */
object DisplayTapImplicits {

  // TODO: scala 2.11
  // implicit class ZeppelinTap[T: ClassTag](private val self: Tap[T])(implicit ev: T <:< AnyVal) extends AnyVal {
  implicit class ZeppelinTap[T: ClassTag](val self: Tap[T])
                                         (implicit ev: T <:< AnyVal) {
    /** Convenience method to display [[com.spotify.scio.io.Tap]] of AnyVal. */
    def display(printer: (T) => String = (e: T) => e.toString): Unit = {
      DisplayHelpers.displayAnyVal(self.value, printer)
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinFutureTap[T: ClassTag](private val self: Future[Tap[T]])(implicit ev: T <:< AnyVal) extends AnyVal {
  implicit class ZeppelinFutureTap[T: ClassTag](val self: Future[Tap[T]])
                                               (implicit ev: T <:< AnyVal) {
    /** Convenience method to display [[Future]] of a [[com.spotify.scio.io.Tap]] of AnyVal. */
    def waitAndDisplay(printer: (T) => String = (e: T) => e.toString): Unit = {
      ZeppelinTap(self.waitForResult()).display(printer)
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinStringTap[T: ClassTag](private val self: Tap[T])(implicit ev: T <:< String) extends AnyVal {
  implicit class ZeppelinStringTap[T: ClassTag](val self: Tap[T])
                                               (implicit ev: T <:< String) {
    /** Convenience method to display [[com.spotify.scio.io.Tap]] of Strings. */
    def display(printer: (T) => String = (e: T) => e.toString): Unit = {
      DisplayHelpers.displayString(self.value, printer)
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinFutureStringTap[T: ClassTag](private val self: Tap[T])(implicit ev: T <:< String) extends AnyVal {
  implicit class ZeppelinFutureStringTap[T: ClassTag](val self: Future[Tap[T]])
                                                     (implicit ev: T <:< String) {
    /** Convenience method to display [[Future]] of a [[com.spotify.scio.io.Tap]] of Strings. */
    def waitAndDisplay(printer: (T) => String = (e: T) => e.toString): Unit = {
      ZeppelinStringTap(self.waitForResult()).display(printer)
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinKVTap[K: ClassTag, V: ClassTag](val self: Tap[(K, V)]) extends AnyVal {
  implicit class ZeppelinKVTap[K: ClassTag, V: ClassTag](val self: Tap[(K, V)]) {
    /** Convenience method to display [[com.spotify.scio.io.Tap]] of KV. */
    def display(): Unit = {
      DisplayHelpers.displayKV(self.value)
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinFutureKVTap[K: ClassTag, V: ClassTag](val self: Future[Tap[(K, V)]]) extends AnyVal {
  implicit class ZeppelinFutureKVTap[K: ClassTag, V: ClassTag](val self: Future[Tap[(K, V)]]) {
    /** Convenience method to display [[Future]] of a [[com.spotify.scio.io.Tap]] of KV. */
    def waitAndDisplay(): Unit = {
      ZeppelinKVTap(self.waitForResult()).display()
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinProductTap[T: ClassTag](val self: Tap[T])(implicit ev: T <:< Product) extends AnyVal {
  implicit class ZeppelinProductTap[T: ClassTag](val self: Tap[T])
                                                (implicit ev: T <:< Product) {
    /** Convenience method to display [[com.spotify.scio.io.Tap]] of Product. */
    def display(): Unit = {
      DisplayHelpers.displayProduct(self.value)
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinFutureProductTap[T: ClassTag](val self: Future[Tap[T]])(implicit ev: T <:< Product) extends AnyVal {
  implicit class ZeppelinFutureProductTap[T: ClassTag](val self: Future[Tap[T]])
                                                      (implicit ev: T <:< Product) {
    /** Convenience method to display [[Future]] of a [[com.spotify.scio.io.Tap]] of Product. */
    def waitAndDisplay(): Unit = {
      ZeppelinProductTap(self.waitForResult()).display()
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinAvroTap[T: ClassTag](val self: Tap[T])(implicit ev: T <:< GenericRecord) extends AnyVal {
  implicit class ZeppelinAvroTap[T: ClassTag](val self: Tap[T])
                                             (implicit ev: T <:< GenericRecord) {
    /** Convenience method to display [[com.spotify.scio.io.Tap]] of Avro. */
    def display(schema: Schema = null): Unit = {
      DisplayHelpers.displayAvro(self.value, schema)
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinFutureAvroTap[T: ClassTag](val self: Future[Tap[T]])(implicit ev: T <:< GenericRecord) extends AnyVal {
  implicit class ZeppelinFutureAvroTap[T: ClassTag](val self: Future[Tap[T]])
                                                   (implicit ev: T <:< GenericRecord) {
    /** Convenience method to display [[Future]] of a [[com.spotify.scio.io.Tap]] of Avro. */
    def waitAndDisplay(schema: Schema = null): Unit = {
      ZeppelinAvroTap(self.waitForResult()).display(schema)
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinBQTableTap[T: ClassTag](val self: Tap[T])(implicit ev: T <:< TableRow) extends AnyVal {
  implicit class ZeppelinBQTableTap[T: ClassTag](val self: Tap[T])
                                                (implicit ev: T <:< TableRow) {
    /** Convenience method to display [[com.spotify.scio.io.Tap]] of BigQuery TableRow. */
    def display(schema: TableSchema): Unit = {
      DisplayHelpers.displayBQTableRow(self.value, schema)
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinFutureBQTableTap[T: ClassTag](val self: Future[Tap[T]])(implicit ev: T <:< TableRow) extends AnyVal {
  implicit class ZeppelinFutureBQTableTap[T: ClassTag](val self: Future[Tap[T]])
                                                      (implicit ev: T <:< TableRow) {
    /** Convenience method to display [[Future]] of a [[com.spotify.scio.io.Tap]] of BigQuery
     * TableRow. */
    def waitAndDisplay(schema: TableSchema): Unit = {
      ZeppelinBQTableTap(self.waitForResult()).display(schema)
    }
  }

}
