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
import com.spotify.scio._
import com.spotify.scio.bigquery._
import com.spotify.scio.values.SCollection
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord

import scala.reflect.ClassTag

/**
 * Implicit Zeppelin display helpers for SCollection.
 */
object DisplaySCollectionImplicits {

  private def materialize[T: ClassTag](self: SCollection[T]) = {
    val f = self.materialize
    self.context.close()
    f
  }

  // TODO: scala 2.11
  // implicit class ZeppelinSCollection[T: ClassTag](private val self: SCollection[T])(implicit ev: T <:< AnyVal) extends AnyVal {
  implicit class ZeppelinSCollection[T: ClassTag](val self: SCollection[T])
                                                 (implicit ev: T <:< AnyVal) {
    /** Convenience method to close the current [[com.spotify.scio.ScioContext]]
     * and display elements from SCollection. */
    def closeAndDisplay(printer: (T) => String = (e: T) => e.toString): Unit = {
      DisplayTapImplicits.ZeppelinTap(materialize(self).waitForResult()).display(printer)
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinSCollection[T: ClassTag](private val self: SCollection[T]) extends AnyVal {
  implicit class ZeppelinStringSCollection[T: ClassTag](val self: SCollection[T])
                                                       (implicit ev: T <:< String) {
    /** Convenience method to close the current [[com.spotify.scio.ScioContext]]
     * and display elements from SCollection. */
    def closeAndDisplay(printer: (T) => String = (e: T) => e.toString): Unit = {
      DisplayTapImplicits.ZeppelinStringTap(materialize(self).waitForResult()).display(printer)
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinKVSCollection[K: ClassTag, V: ClassTag](val self: SCollection[(K, V)]) extends AnyVal {
  implicit class ZeppelinKVSCollection[K: ClassTag, V: ClassTag](val self: SCollection[(K, V)]) {
    /** Convenience method to close the current [[com.spotify.scio.ScioContext]]
     * and display elements from KV SCollection. */
    def closeAndDisplay(): Unit = {
      DisplayTapImplicits.ZeppelinKVTap(materialize(self).waitForResult()).display()
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinProductSCollection[T: ClassTag](val self: SCollection[T])(implicit ev: T <:< Product) extends AnyVal {
  implicit class ZeppelinProductSCollection[T: ClassTag](val self: SCollection[T])
                                                        (implicit ev: T <:< Product) {
    /** Convenience method to close the current [[com.spotify.scio.ScioContext]]
     * and display elements from Product like SCollection */
    def closeAndDisplay(): Unit = {
      DisplayTapImplicits.ZeppelinProductTap(materialize(self).waitForResult()).display()
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinAvroSCollection[T: ClassTag](val self: SCollection[T])(implicit ev: T <:< GenericRecord) extends AnyVal {
  implicit class ZeppelinAvroSCollection[T: ClassTag](val self: SCollection[T])
                                                     (implicit ev: T <:< GenericRecord) {
    /** Convenience method to close the current [[com.spotify.scio.ScioContext]]
     * and display elements from Avro like SCollection */
    def closeAndDisplay(schema: Schema = null): Unit = {
      DisplayTapImplicits.ZeppelinAvroTap(materialize(self).waitForResult()).display(schema)
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinBQTableSCollection[T: ClassTag](val self: SCollection[T])(implicit ev: T <:< TableRow) extends AnyVal {
  implicit class ZeppelinBQTableSCollection[T: ClassTag](val self: SCollection[T])
                                                        (implicit ev: T <:< TableRow) {
    /** Convenience method to close the current [[com.spotify.scio.ScioContext]]
     * and display elements from TableRow like SCollection */
    def closeAndDisplay(schema: TableSchema): Unit = {
      DisplayTapImplicits.ZeppelinBQTableTap(materialize(self).waitForResult()).display(schema)
    }
  }

}
