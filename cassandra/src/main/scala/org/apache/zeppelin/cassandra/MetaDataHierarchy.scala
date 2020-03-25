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

import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.uuid.Uuids

import scala.util.parsing.json.JSONObject

/**
 * Define a hierarchy for CQL meta data
 */
object MetaDataHierarchy {
  object OrderConverter {
    def convert(clusteringOrder: com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder): ClusteringOrder = {
      clusteringOrder match {
        case com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder.ASC => ASC
        case com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder.DESC => DESC
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
  case class IndexDetails(name: String, target: String, asCQL: String)
  case class ColumnDetails(name: String, columnType: ColumnType, dataType: DataType)

  case class ClusterDetails(name: String, partitioner: String)
  case class ClusterContent(clusterName: String, clusterDetails: String, keyspaces: Seq[(UUID, String, String)])
  case class AllTables(tables: Map[String, Seq[String]])
  case class KeyspaceDetails(name: String, replication: Map[String,String], durableWrites: Boolean,
                             asCQL: String, uniqueId: UUID = Uuids.timeBased()) {
    def getReplicationMap: String = {
      JSONObject(replication).toString().replaceAll(""""""","'")
    }
  }
  case class KeyspaceContent(keyspaceName: String, keyspaceDetails: String,
                             tables: Seq[(UUID,String, String)],
                             views: Seq[(UUID,String, String)],
                             udts: Seq[(UUID, String, String)],
                             functions: Seq[(UUID, String, String)],
                             aggregates: Seq[(UUID, String, String)])
  case class TableDetails(tableName: String, columns: Seq[ColumnDetails], indices: Seq[IndexDetails], asCQL: String,
                          indicesAsCQL: String, uniqueId: UUID = Uuids.timeBased())
  case class UDTDetails(typeName: String, columns: Seq[ColumnDetails], asCQL: String, uniqueId: UUID = Uuids.timeBased())

  case class SameNameFunctionDetails(functions: Seq[FunctionDetails])
  case class FunctionDetails(keyspace:String, name: String, arguments: Seq[String], calledOnNullInput: Boolean, returnType: String,
    language:String, body: String, asCQL: String, uniqueId: UUID = Uuids.timeBased())
  case class FunctionSummary(keyspace:String, name: String, arguments: Seq[String], returnType: String)

  case class AggregateDetails(keyspace:String, name: String, arguments: Seq[String], sFunc: String, sType: String,
    finalFunc: Option[String], initCond: Option[String], returnType: String,
    asCQL: String, uniqueId: UUID = Uuids.timeBased())
  case class AggregateSummary(keyspace:String, name: String, arguments: Seq[String], returnType: String)
  case class SameNameAggregateDetails(aggregates: Seq[AggregateDetails])

  case class MaterializedViewDetails(name: String, columns: Seq[ColumnDetails], asCQL: String, baseTable: String,
                                     uniqueId: UUID = Uuids.timeBased())
  case class MaterializedViewSummary(name: String, baseTable: String)
}
