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
package org.apache.zeppelin.cassandra

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.datastax.driver.core.{DataType, TableMetadata}

import scala.util.parsing.json.JSONObject

/**
 * Define a hierarchy for CQL meta data
 */
object MetaDataHierarchy {
  object OrderConverter {
    def convert(clusteringOrder: TableMetadata.Order): ClusteringOrder = {
      clusteringOrder match {
        case TableMetadata.Order.ASC => ASC
        case TableMetadata.Order.DESC => DESC
      }
    }
  }


  sealed trait ClusteringOrder
  object ASC extends ClusteringOrder
  object DESC extends ClusteringOrder

  sealed trait ColumnType
  object PartitionKey extends ColumnType
  case class ClusteringColumn(order: ClusteringOrder) extends ColumnType
  object StaticColumn extends ColumnType
  object NormalColumn extends ColumnType
  case class IndexDetails(name: String, info: String)
  case class ColumnDetails(name: String, columnType: ColumnType, dataType: DataType, index: Option[IndexDetails])


  case class ClusterDetails(name: String, partitioner: String)
  case class ClusterContent(clusterName: String, clusterDetails: String, keyspaces: List[(UUID, String, String)])
  case class AllTables(tables: Map[String,List[String]])
  case class KeyspaceDetails(name: String, replication: Map[String,String], durableWrites: Boolean, asCQL: String, uniqueId: UUID = UUIDs.timeBased()) {
    def getReplicationMap: String = {
      JSONObject(replication).toString().replaceAll(""""""","'")
    }
  }
  case class KeyspaceContent(keyspaceName: String, keyspaceDetails: String, tables: List[(UUID,String, String)], udts: List[(UUID, String, String)])
  case class TableDetails(tableName: String, columns: List[ColumnDetails], asCQL: String, uniqueId: UUID = UUIDs.timeBased())
  case class UDTDetails(typeName: String, columns: List[ColumnDetails], asCQL: String, uniqueId: UUID = UUIDs.timeBased())



}
