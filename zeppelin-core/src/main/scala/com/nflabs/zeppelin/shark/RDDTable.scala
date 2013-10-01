/*
 * Copyright (C) 2012 The Regents of The University California.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nflabs.zeppelin.shark

import scala.collection.mutable.ArrayBuffer

import shark.SharkEnv
import shark.memstore2.{TablePartitionStats, TablePartition, TablePartitionBuilder}
import org.apache.spark.rdd.RDD
import shark.api.{DataType, DataTypes}

class RDDTableFunctions(self: RDD[Product], columnTypes: Seq[DataType]) {
 
  
  def saveAsTable(tableName: String, fields: Seq[String]): Boolean = {
    //require(fields.size == columnTypes.size,
    //  "Number of column names != number of fields in the RDD.")

    // Get a local copy of the columnTypes so we don't need to serialize this object.
    val columnTypes = this.columnTypes

    val statsAcc = SharkEnv.sc.accumulableCollection(ArrayBuffer[(Int, TablePartitionStats)]())

    // Create the RDD object.
    val rdd = self.mapPartitionsWithIndex { case(partitionIndex, iter) =>
      val ois = columnTypes.map(HiveUtils.getJavaPrimitiveObjectInspector)
      val builder = new TablePartitionBuilder(ois, 1000000, shouldCompress = false)

      for (p <- iter) {
        builder.incrementRowCount()
        // TODO: this is not the most efficient code to do the insertion ...
        p.productIterator.zipWithIndex.foreach { case (v, i) =>
          builder.append(i, v.asInstanceOf[Object], ois(i))
        }
      }

      statsAcc += Tuple2(partitionIndex, builder.asInstanceOf[TablePartitionBuilder].stats)
      Iterator(builder.build())
    }.persist()

    // Put the table in the metastore. Only proceed if the DDL statement is executed successfully.
    if (HiveUtils.createTable(tableName, fields, columnTypes)) {
      // Force evaluate to put the data in memory.
      SharkEnv.memoryMetadataManager.put(tableName, rdd)
      rdd.context.runJob(rdd, (iter: Iterator[TablePartition]) => iter.foreach(_ => Unit))

      // Gather the partition statistics.
      SharkEnv.memoryMetadataManager.putStats(tableName, statsAcc.value.toMap)

      true
    } else {
      false
    }
  }
  
  
}


