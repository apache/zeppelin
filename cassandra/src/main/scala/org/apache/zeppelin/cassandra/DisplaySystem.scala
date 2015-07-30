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

import com.datastax.driver.core.ColumnMetadata.IndexMetadata
import com.datastax.driver.core.utils.UUIDs
import org.apache.zeppelin.cassandra.MetaDataHierarchy._
import org.fusesource.scalate.TemplateEngine

import scala.collection.JavaConverters._

import com.datastax.driver.core._

import scala.collection.immutable.ListMap

/**
 * Format and display
 * schema meta data
 */
object DisplaySystem {

  val engine = new TemplateEngine

  val CLUSTER_DETAILS_TEMPLATE = "scalate/clusterDetails.ssp"
  val KEYSPACE_DETAILS_TEMPLATE = "scalate/keyspaceDetails.ssp"
  val TABLE_DETAILS_TEMPLATE = "scalate/tableDetails.ssp"
  val UDT_DETAILS_TEMPLATE = "scalate/udtDetails.ssp"

  val MENU_TEMPLATE = "scalate/menu.ssp"
  val CLUSTER_DROPDOWN_TEMPLATE = "scalate/dropDownMenuForCluster.ssp"
  val KEYSPACE_DROPDOWN_TEMPLATE = "scalate/dropDownMenuForKeyspace.ssp"

  val CLUSTER_CONTENT_TEMPLATE = "scalate/clusterContent.ssp"
  val KEYSPACE_CONTENT_TEMPLATE = "scalate/keyspaceContent.ssp"
  val ALL_TABLES_TEMPLATE = "scalate/allTables.ssp"

  object TableDisplay {

    def format(statement: String, meta: TableMetadata, withCaption: Boolean): String = {
      MenuDisplay.formatMenu(statement) + formatWithoutMenu(meta, withCaption)
    }

    protected[DisplaySystem] def formatWithoutMenu(meta: TableMetadata, withCaption: Boolean): String = {
      val tableName: String = meta.getName
      val columnsDetails = MetaDataConverter.tableMetaToColumnDetails(meta)

      engine.layout(TABLE_DETAILS_TEMPLATE,
        Map[String, Any]("tableDetails" -> TableDetails(tableName, columnsDetails, meta.exportAsString), "withCaption" -> withCaption))
    }
  }

  object UDTDisplay {
    def format(statement: String, userType: UserType, withCaption: Boolean): String = {
      MenuDisplay.formatMenu(statement) ++ formatWithoutMenu(userType, withCaption)
    }

    protected[DisplaySystem] def formatWithoutMenu(userType: UserType, withCaption: Boolean): String = {
      val udtName: String = userType.getTypeName
      val columnsDetails = MetaDataConverter.userTypeToColumnDetails(userType)

      engine.layout(UDT_DETAILS_TEMPLATE,
        Map[String, Any]("udtDetails" -> UDTDetails(udtName, columnsDetails, userType.exportAsString), "withCaption" -> withCaption))
    }
  }
  
  object KeyspaceDisplay {

    private def formatCQLQuery(cql: String): String = {
      cql.replaceAll(""" WITH REPLICATION = \{"""," WITH REPLICATION = \\{")
        .replaceAll("('[^']+'\\s*:\\s+'[^']+',?)","\n\t$1")
        .replaceAll(""" \} AND DURABLE_WRITES = """," \\}\nAND DURABLE_WRITES = ")
    }

    protected[cassandra] def formatKeyspaceOnly(meta: KeyspaceMetadata, withCaption: Boolean): String = {
      val ksDetails = KeyspaceDetails(meta.getName,
        meta.getReplication.asScala.toMap,
        meta.isDurableWrites,
        formatCQLQuery(meta.asCQLQuery()))

      engine.layout(KEYSPACE_DETAILS_TEMPLATE,
        Map[String, Any]("ksDetails" -> ksDetails, "withCaption" -> withCaption))
    }

    def formatKeyspaceContent(statement: String, meta: KeyspaceMetadata): String = {
      val ksName: String = meta.getName
      val ksDetails = formatKeyspaceOnly(meta, false)

      val tableDetails: List[(UUID, String, String)] = meta.getTables.asScala.toList
        .sortBy(meta => meta.getName)
        .map(meta => (UUIDs.timeBased(), meta.getName, TableDisplay.formatWithoutMenu(meta, false)))

      val udtDetails: List[(UUID, String, String)] = meta.getUserTypes.asScala.toList
        .sortBy(udt => udt.getTypeName)
        .map(udt => (UUIDs.timeBased(), udt.getTypeName, UDTDisplay.formatWithoutMenu(udt, false)))

      val ksContent: KeyspaceContent = KeyspaceContent(ksName, ksDetails, tableDetails, udtDetails)

      MenuDisplay.formatMenuForKeyspace(statement, ksContent) +
        engine.layout(KEYSPACE_CONTENT_TEMPLATE,
          Map[String, Any]("statement" -> statement, "ksContent" -> ksContent))
    }
  }

  object ClusterDisplay {

    def formatClusterOnly(statement: String, meta: Metadata, withMenu: Boolean = true): String = {
      val clusterDetails: ClusterDetails = ClusterDetails(meta.getClusterName, meta.getPartitioner)
      val content: String = engine.layout(CLUSTER_DETAILS_TEMPLATE,
        Map[String, Any]("clusterDetails" -> clusterDetails))

      if(withMenu) MenuDisplay.formatMenu(statement) + content else content
    }

    def formatClusterContent(statement: String, meta: Metadata): String = {
      val clusterName: String = meta.getClusterName
      val clusterDetails: String = formatClusterOnly(statement, meta, false)

      val keyspaceDetails: List[(UUID, String, String)] = meta.getKeyspaces.asScala.toList
        .sortBy(ks => ks.getName)
        .map(ks => (UUIDs.timeBased(), ks.getName, KeyspaceDisplay.formatKeyspaceOnly(ks, false)))

      val clusterContent: ClusterContent = ClusterContent(clusterName, clusterDetails, keyspaceDetails)

      MenuDisplay.formatMenuForCluster(statement, clusterContent) +
        engine.layout(CLUSTER_CONTENT_TEMPLATE,
          Map[String, Any]("clusterContent" -> clusterContent))
    }

    def formatAllTables(statement: String, meta: Metadata): String = {
      val ksMetas: List[KeyspaceMetadata] = meta.getKeyspaces.asScala.toList
        .sortBy(ks => ks.getName)

      val allTables: Map[(UUID, String), List[String]] = ListMap.empty ++
        ksMetas
        .map(ks => {
          ((UUIDs.timeBased(), ks.getName),
            ks.getTables.asScala.toList.map(table => table.getName).sortBy(name => name))
         })
        .sortBy{case ((id,name), _) => name}


      val keyspaceDetails: List[(UUID, String, String)] = allTables
        .keySet.toList.sortBy{case(id,ksName) => ksName}
        .map{case(id,ksName) => (id,ksName, "")}

      val clusterContent: ClusterContent = ClusterContent(meta.getClusterName, "", keyspaceDetails)

      MenuDisplay.formatMenuForCluster(statement, clusterContent) +
        engine.layout(ALL_TABLES_TEMPLATE,
          Map[String, Any]("allTables" -> allTables))
    }
  }

  object HelpDisplay {

    def formatHelp(): String = {
      engine.layout("/scalate/helpMenu.ssp")
    }
  }

  object NoResultDisplay {

    val formatNoResult: String = engine.layout("/scalate/noResult.ssp")

    def noResultWithExecutionInfo(lastQuery: String, execInfo: ExecutionInfo): String = {
      val consistency = Option(execInfo.getAchievedConsistencyLevel).getOrElse("N/A")
      val queriedHosts = execInfo.getQueriedHost.toString.replaceAll("/","").replaceAll("""\[""","").replaceAll("""\]""","")
      val triedHosts = execInfo.getTriedHosts.toString.replaceAll("/","").replaceAll("""\[""","").replaceAll("""\]""","")
      val schemaInAgreement = Option(execInfo.isSchemaInAgreement).map(_.toString).getOrElse("N/A")

      engine.layout("/scalate/noResultWithExecutionInfo.ssp",
        Map[String,Any]("query" -> lastQuery, "consistency" -> consistency,
                        "triedHosts" -> triedHosts, "queriedHosts" -> queriedHosts,
                        "schemaInAgreement" -> schemaInAgreement))
    }
  }


  private object MenuDisplay {
    def formatMenu(statement: String, dropDownMenu: String = ""): String = {
      engine.layout(MENU_TEMPLATE,
        Map[String, Any]("statement" -> statement, "dropDownMenu" -> dropDownMenu))
    }

    def formatMenuForKeyspace(statement: String, ksContent: KeyspaceContent): String = {
      val dropDownMenu: String = engine.layout(KEYSPACE_DROPDOWN_TEMPLATE,
        Map[String, Any]("ksContent" -> ksContent))

      formatMenu(statement, dropDownMenu)
    }

    def formatMenuForCluster(statement: String, clusterContent: ClusterContent): String = {
      val dropDownMenu: String = engine.layout(CLUSTER_DROPDOWN_TEMPLATE,
        Map[String, Any]("clusterContent" -> clusterContent))

      formatMenu(statement, dropDownMenu)
    }
  }
}

class ColumnMetaWrapper(val columnMeta: ColumnMetadata) {
  def canEqual(other: Any): Boolean = other.isInstanceOf[ColumnMetaWrapper]

  override def equals(other: Any): Boolean = other match {
    case that: ColumnMetaWrapper => (that canEqual this) &&
      (columnMeta.getName == that.columnMeta.getName)
    case _ => false
  }

  override def hashCode: Int = columnMeta.getName.hashCode
}

/**
 * Convert Java driver
 * meta data structure
 * to our own structure
 */
object MetaDataConverter {

  def tableMetaToColumnDetails(meta: TableMetadata): List[ColumnDetails] = {
    val partitionKeys: List[ColumnMetaWrapper] = meta.getPartitionKey.asScala.toList.map(new ColumnMetaWrapper(_))
    val clusteringColumns: List[ColumnMetaWrapper] = meta.getClusteringColumns.asScala.toList.map(new ColumnMetaWrapper(_))
    val columns: List[ColumnMetaWrapper] = meta.getColumns.asScala.toList.map(new ColumnMetaWrapper(_))
      .diff(partitionKeys).diff(clusteringColumns)
    val clusteringOrders = meta.getClusteringOrder.asScala.toList

    convertPartitionKeys(partitionKeys):::
      extractStaticColumns(columns):::
      convertClusteringColumns(clusteringColumns, clusteringOrders):::
      extractNormalColumns(columns)
  }
  
  def userTypeToColumnDetails(userType: UserType): List[ColumnDetails] = {
    userType.getFieldNames.asScala.toList
      .map(name => new ColumnDetails(name, NormalColumn, userType.getFieldType(name), None))
  }

  private def extractNormalColumns(columns: List[ColumnMetaWrapper]): List[ColumnDetails] = {
    columns
      .filter(_.columnMeta.isStatic == false)
      .map(c => new ColumnDetails(c.columnMeta.getName, NormalColumn, c.columnMeta.getType, extractIndexDetail(c)))
  }

  private def extractIndexDetail(column: ColumnMetaWrapper): Option[IndexDetails] = {
    val indexOption = Option(column.columnMeta.getIndex)

    def buildIndexInfo(indexMeta: IndexMetadata): String = {
      if(indexMeta.isKeys) "KEYS"
      if(indexMeta.isEntries) "ENTRIES"
      if(indexMeta.isFull) "FULL"
      if(indexMeta.isCustomIndex) s"Class = ${indexMeta.getIndexClassName}"
      else ""
    }

    indexOption.map(index => IndexDetails(index.getName, buildIndexInfo(index)))
  }

  private def extractStaticColumns(columns: List[ColumnMetaWrapper]): List[ColumnDetails] = {
    columns
      .filter(_.columnMeta.isStatic == true)
      .map(c => new ColumnDetails(c.columnMeta.getName, StaticColumn, c.columnMeta.getType, extractIndexDetail(c)))
  }

  private def convertClusteringColumns(columns: List[ColumnMetaWrapper], orders: List[TableMetadata.Order]): List[ColumnDetails] = {
    columns
      .zip(orders)
      .map{case(c,order) => new ColumnDetails(c.columnMeta.getName,
      new ClusteringColumn(OrderConverter.convert(order)),
      c.columnMeta.getType, extractIndexDetail(c))}

  }

  private def convertPartitionKeys(columns: List[ColumnMetaWrapper]): List[ColumnDetails] = {
    columns
      .map(c => new ColumnDetails(c.columnMeta.getName, PartitionKey, c.columnMeta.getType, extractIndexDetail(c)))
  }
}





