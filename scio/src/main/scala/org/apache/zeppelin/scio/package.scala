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

package org.apache.zeppelin

import com.spotify.scio._
import com.spotify.scio.values.SCollection
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord

import scala.reflect.ClassTag

package object scio {
  private val SCollectionEmptyMsg = "\n%html <font color=red>Result SCollection is empty!</font>\n"
  private val maxResults = Integer.getInteger("zeppelin.scio.maxResult", 1000)

  private def materialize[T: ClassTag](self: SCollection[T]) = {
    val f = self.materialize
    self.context.close()
    f
  }

  private def notifIfTruncated(it: Iterator[_]): Unit = {
    if(it.hasNext)
      println("\n<font color=red>Results are limited to " + maxResults + " rows.</font>\n")
  }

  // TODO: scala 2.11
  // implicit class ZeppelinSCollection[T: ClassTag](private val self: SCollection[T]) extends AnyVal {
  implicit class ZeppelinSCollection[T: ClassTag](val self: SCollection[T])
                                                 (implicit ev: T <:< AnyVal) {
    /** Convenience method to close the current [[com.spotify.scio.ScioContext]]
     * and display elements from SCollection. */
    def closeAndDisplay(printer: (T) => String = (e: T) => e.toString): Unit = {
      val it = materialize(self).waitForResult().value

      if (it.isEmpty) {
        println(SCollectionEmptyMsg)
      } else {
        println(s"""%table value\n${it.take(maxResults).map(printer).mkString("\n")}""")
        notifIfTruncated(it)
      }
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinSCollection[T: ClassTag](private val self: SCollection[T]) extends AnyVal {
  implicit class ZeppelinStringSCollection[T: ClassTag](val self: SCollection[T])
                                                       (implicit ev: T <:< String) {
    /** Convenience method to close the current [[com.spotify.scio.ScioContext]]
     * and display elements from SCollection. */
    def closeAndDisplay(printer: (T) => String = (e: T) => e.toString): Unit = {
      val it = materialize(self).waitForResult().value

      if (it.isEmpty) {
        println(SCollectionEmptyMsg)
      } else {
        println(s"""%table value\n${it.take(maxResults).map(printer).mkString("\n")}""")
        notifIfTruncated(it)
      }
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinKVSCollection[K: ClassTag, V: ClassTag](val self: SCollection[(K, V)]) extends AnyVal {
  implicit class ZeppelinKVSCollection[K: ClassTag, V: ClassTag](val self: SCollection[(K, V)]) {
    /** Convenience method to close the current [[com.spotify.scio.ScioContext]]
     * and display elements from KV SCollection. */
    def closeAndDisplay(): Unit = {
      val it = materialize(self).waitForResult().value

      if (it.isEmpty) {
        println(SCollectionEmptyMsg)
      } else {
        val content = it.take(maxResults).map{ case (k, v) => s"$k\t$v" }.mkString("\n")
        println(s"""%table key\tvalue\n$content""")
        notifIfTruncated(it)
      }
    }

  }

  // TODO: scala 2.11
  // implicit class ZeppelinProductSCollection[T: ClassTag](val self: SCollection[T])(implicit ev: T <:< Product) extends AnyVal {
  implicit class ZeppelinProductSCollection[T: ClassTag](val self: SCollection[T])
                                                        (implicit ev: T <:< Product) {
    /** Convenience method to close the current [[com.spotify.scio.ScioContext]]
     * and display elements from Product like SCollection */
    def closeAndDisplay(): Unit = {
      val it = materialize(self).waitForResult().value

      if (it.isEmpty) {
        println(SCollectionEmptyMsg)
      } else {
        val first = it.next()
        //TODO is this safe field name to value iterator?
        val fieldNames = first.getClass.getDeclaredFields.map(_.getName)

        val header = fieldNames.mkString("\t")
        val firstStr = first.productIterator.mkString("\t")
        val content = it.take(maxResults).map(_.productIterator.mkString("\t")).mkString("\n")
        println(s"""%table $header\n$firstStr\n$content""")
        notifIfTruncated(it)
      }
    }
  }

  // TODO: scala 2.11
  // implicit class ZeppelinAvroSCollection[T: ClassTag](val self: SCollection[T])(implicit ev: T <:< GenericRecord) extends AnyVal {
  implicit class ZeppelinAvroSCollection[T: ClassTag](val self: SCollection[T])
                                                     (implicit ev: T <:< GenericRecord) {
    /** Convenience method to close the current [[com.spotify.scio.ScioContext]]
     * and display elements from Avro like SCollection */
    def closeAndDisplay(schema: Schema = null): Unit = {
      val it = materialize(self).waitForResult().value

      if (it.isEmpty) {
        println(SCollectionEmptyMsg)
      } else {
        val first = it.next()
        import collection.JavaConverters._
        val fieldNames = first.getSchema.getFields.iterator.asScala.map(_.name()).toArray

        val header = fieldNames.mkString("\t")
        val firstStr = fieldNames.map(first.get(_)).mkString("\t")
        val content = it.take(maxResults)
                        .map(r => fieldNames.map(r.get(_)).mkString("\t"))
                        .mkString("\n")
        println(s"""%table $header\n$firstStr\n$content""")
        notifIfTruncated(it)
      }
    }
  }

}
