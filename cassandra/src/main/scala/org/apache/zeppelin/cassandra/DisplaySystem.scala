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

import java.util.{Properties, UUID}

import org.apache.zeppelin.cassandra.MetaDataHierarchy._
import org.fusesource.scalate.TemplateEngine

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import com.datastax.driver.core._
import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.`type`.UserDefinedType
import com.datastax.oss.driver.api.core.`type`.codec.registry.CodecRegistry
import com.datastax.oss.driver.api.core.cql.ExecutionInfo
import com.datastax.oss.driver.api.core.metadata.{Metadata, Node}
import com.datastax.oss.driver.api.core.metadata.schema.{AggregateMetadata, ColumnMetadata, FunctionMetadata, FunctionSignature, IndexMetadata, KeyspaceMetadata, RelationMetadata, TableMetadata, ViewMetadata}
import com.datastax.oss.driver.api.core.uuid.Uuids

import scala.collection.immutable.ListMap

/**
 * Format and display
 * schema meta data
 */
object DisplaySystem {

  val engine = new TemplateEngine

  val CLUSTER_DETAILS_TEMPLATE: String = "scalate/clusterDetails.ssp"
  val KEYSPACE_DETAILS_TEMPLATE: String = "scalate/keyspaceDetails.ssp"

  val TABLE_DETAILS_TEMPLATE: String = "scalate/tableDetails.ssp"
  val ALL_TABLES_TEMPLATE: String = "scalate/allTables.ssp"

  val UDT_DETAILS_TEMPLATE: String = "scalate/udtDetails.ssp"
  val ALL_UDTS_TEMPLATE: String = "scalate/allUDTs.ssp"

  val FUNCTION_DETAILS_TEMPLATE: String = "scalate/functionDetails.ssp"
  val ALL_FUNCTIONS_TEMPLATE: String = "scalate/allFunctions.ssp"

  val AGGREGATE_DETAILS_TEMPLATE: String = "scalate/aggregateDetails.ssp"
  val ALL_AGGREGATES_TEMPLATE: String = "scalate/allAggregates.ssp"

  val MATERIALIZED_VIEW_DETAILS_TEMPLATE: String = "scalate/materializedViewDetails.ssp"
  val ALL_MATERIALIZED_VIEWS_TEMPLATE: String = "scalate/allMaterializedViews.ssp"

  val MENU_TEMPLATE: String = "scalate/menu.ssp"
  val CLUSTER_DROPDOWN_TEMPLATE: String = "scalate/dropDownMenuForCluster.ssp"

  val KEYSPACE_DROPDOWN_TEMPLATE: String = "scalate/dropDownMenuForKeyspace.ssp"
  val CLUSTER_CONTENT_TEMPLATE: String = "scalate/clusterContent.ssp"
  val KEYSPACE_CONTENT_TEMPLATE: String = "scalate/keyspaceContent.ssp"

  val NO_RESULT_TEMPLATE: String = "scalate/noResult.ssp"
  val NO_RESULT_WITH_EXECINFO_TEMPLATE: String = "scalate/noResultWithExecutionInfo.ssp"

  val HELP_TEMPLATE = "scalate/helpMenu.ssp"

  val DEFAULT_CLUSTER_NAME: String = "Test Cluster"

  def clusterName(meta: Metadata): String = {
    if (meta != null) {
      meta.getClusterName.orElse(DEFAULT_CLUSTER_NAME)
    } else {
      DEFAULT_CLUSTER_NAME
    }
  }

  object TableDisplay {

    def format(statement: String, meta: TableMetadata, withCaption: Boolean): String = {
      MenuDisplay.formatMenu(statement) + formatWithoutMenu(meta, withCaption)
    }

    protected[DisplaySystem] def formatWithoutMenu(meta: TableMetadata, withCaption: Boolean): String = {
      val tableName: String = meta.getName.asCql(true)
      val columnsDetails = MetaDataConverter.Table.tableMetaToColumnDetails(meta)
      val indicesDetails = MetaDataConverter.Table.tableMetaToIndexDetails(meta)
      val indicesAsCQL = indicesDetails.map(_.asCQL).mkString("\n")

      engine.layout(TABLE_DETAILS_TEMPLATE,
        Map[String, Any]("tableDetails" -> TableDetails(tableName, columnsDetails, indicesDetails,
          TableMetadataWrapper(meta).exportTableOnlyAsString(), indicesAsCQL), "withCaption" -> withCaption))
    }
  }

  object UDTDisplay {
    def format(statement: String, userType: UserDefinedType, withCaption: Boolean): String = {
      MenuDisplay.formatMenu(statement) ++ formatWithoutMenu(userType, withCaption)
    }

    protected[DisplaySystem] def formatWithoutMenu(userType: UserDefinedType, withCaption: Boolean): String = {
      val udtName: String = userType.getName.asCql(true)
      val columnsDetails = MetaDataConverter.UDT.userTypeToColumnDetails(userType)

      engine.layout(UDT_DETAILS_TEMPLATE,
        Map[String, Any]("udtDetails" -> UDTDetails(udtName, columnsDetails, userType.describe(true)), "withCaption" -> withCaption))
    }
  }

  object FunctionDisplay {
    def format(statement: String, functions: Seq[FunctionMetadata], withCaption: Boolean): String = {
      MenuDisplay.formatMenu(statement) ++ formatWithoutMenu(functions, withCaption)
    }

    protected[DisplaySystem] def formatWithoutMenu(functions: Seq[FunctionMetadata], withCaption: Boolean): String = {
      val functionDetails: Seq[FunctionDetails] = functions.map(MetaDataConverter.functionMetaToFunctionDetails)
      engine.layout(FUNCTION_DETAILS_TEMPLATE,
        Map[String, Any]("sameNameFunctionDetails" -> SameNameFunctionDetails(functionDetails), "withCaption" -> withCaption))
    }
  }

  object AggregateDisplay {
    def format(statement: String, aggregates: Seq[AggregateMetadata], withCaption: Boolean, codecRegistry: CodecRegistry): String = {
      MenuDisplay.formatMenu(statement) ++ formatWithoutMenu(aggregates, withCaption, codecRegistry)
    }

    protected[DisplaySystem] def formatWithoutMenu(aggregates: Seq[AggregateMetadata], withCaption: Boolean, codecRegistry: CodecRegistry): String = {
      val aggDetails: Seq[AggregateDetails] = aggregates.map(agg => MetaDataConverter.aggregateMetaToAggregateDetails(codecRegistry, agg))
      engine.layout(AGGREGATE_DETAILS_TEMPLATE,
        Map[String, Any]("sameNameAggregateDetails" -> SameNameAggregateDetails(aggDetails), "withCaption" -> withCaption))
    }
  }

  object MaterializedViewDisplay {
    def format(statement: String, mv: ViewMetadata, withCaption: Boolean): String = {
      MenuDisplay.formatMenu(statement) ++ formatWithoutMenu(mv, withCaption)
    }

    protected[DisplaySystem] def formatWithoutMenu(mv: ViewMetadata, withCaption: Boolean): String = {
      val mvDetails = MetaDataConverter.mvMetaToMaterializedViewDetails(mv)
      engine.layout(MATERIALIZED_VIEW_DETAILS_TEMPLATE,
        Map[String, Any]("mvDetails" -> mvDetails, "withCaption" -> withCaption))
    }
  }

  object KeyspaceDisplay {

    private def formatCQLQuery(cql: String): String = {
      cql.replaceAll(""" WITH REPLICATION = \{""", " WITH REPLICATION = \\{")
        .replaceAll("('[^']+'\\s*:\\s+'[^']+',?)", "\n\t$1")
        .replaceAll(""" } AND DURABLE_WRITES = """, " \\}\nAND DURABLE_WRITES = ")
    }

    protected[cassandra] def formatKeyspaceOnly(meta: KeyspaceMetadata, withCaption: Boolean): String = {
      val ksDetails = KeyspaceDetails(meta.getName.asCql(true),
        meta.getReplication.asScala.toMap,
        meta.isDurableWrites,
        formatCQLQuery(meta.describe(true)))

      engine.layout(KEYSPACE_DETAILS_TEMPLATE,
        Map[String, Any]("ksDetails" -> ksDetails, "withCaption" -> withCaption))
    }

    def formatKeyspaceContent(statement: String, meta: KeyspaceMetadata, codecRegistry: CodecRegistry): String = {
      val ksName: String = meta.getName.asCql(true)
      val ksDetails = formatKeyspaceOnly(meta, withCaption = true)

      val tableDetails: Seq[(UUID, String, String)] = meta.getTables.asScala.toSeq
        .sortBy(_._1.asCql(true))
        .map(t => (Uuids.timeBased(), t._1.asCql(true),
          TableDisplay.formatWithoutMenu(t._2, withCaption = false)))

      val viewDetails: Seq[(UUID, String, String)] = meta.getViews.asScala.toSeq
        .sortBy(_._1.asCql(true))
        .map(v => (Uuids.timeBased(), v._1.asCql(true),
          MaterializedViewDisplay.formatWithoutMenu(v._2, withCaption = false)))

      val udtDetails: Seq[(UUID, String, String)] = meta.getUserDefinedTypes.asScala.toSeq
        .sortBy(_._1.asCql(true))
        .map(udt => (Uuids.timeBased(), udt._1.asCql(true),
          UDTDisplay.formatWithoutMenu(udt._2, withCaption = false)))

      val functionDetails: Seq[(UUID, String, String)] = meta.getFunctions.asScala.toSeq
        .sortBy(_._1.getName.asCql(true))
        .map(f => (Uuids.timeBased(), f._1.getName.asCql(true), FunctionDisplay.formatWithoutMenu(List(f._2),
          withCaption = false)))

      val aggregateDetails: Seq[(UUID, String, String)] = meta.getAggregates.asScala.toSeq
        .sortBy(_._1.getName.asCql(true))
        .map(a => (Uuids.timeBased(), a._1.getName.asCql(true), AggregateDisplay.formatWithoutMenu(List(a._2),
          withCaption = false, codecRegistry)))

      val ksContent: KeyspaceContent = KeyspaceContent(ksName, ksDetails, tableDetails, viewDetails,
        udtDetails, functionDetails, aggregateDetails)

      MenuDisplay.formatMenuForKeyspace(statement, ksContent) +
        engine.layout(KEYSPACE_CONTENT_TEMPLATE,
          Map[String, Any]("statement" -> statement, "ksContent" -> ksContent))
    }
  }

  object ClusterDisplay {
    def formatClusterOnly(statement: String, meta: Metadata,
                          withMenu: Boolean = true): String = {
      val partitioner: String = if (meta.getTokenMap.isPresent)
        meta.getTokenMap.get().getPartitionerName
      else
        "Unknown partitioner"
      val clusterDetails: ClusterDetails = ClusterDetails(clusterName(meta), partitioner)
      val content: String = engine.layout(CLUSTER_DETAILS_TEMPLATE,
        Map[String, Any]("clusterDetails" -> clusterDetails))

      if (withMenu) MenuDisplay.formatMenu(statement) + content else content
    }

    def formatClusterContent(statement: String, meta: Metadata): String = {
      val clusterDetails: String = formatClusterOnly(statement, meta, withMenu = false)

      val keyspaceDetails: Seq[(UUID, String, String)] = meta.getKeyspaces.asScala.toSeq
        .sortBy(_._1.asCql(true))
        .map(ks => (Uuids.timeBased(), ks._1.asCql(true),
          KeyspaceDisplay.formatKeyspaceOnly(ks._2, withCaption = false)))

      val clusterContent: ClusterContent = ClusterContent(clusterName(meta), clusterDetails, keyspaceDetails)

      MenuDisplay.formatMenuForCluster(statement, clusterContent) +
        engine.layout(CLUSTER_CONTENT_TEMPLATE,
          Map[String, Any]("clusterContent" -> clusterContent))
    }

    def formatAllTables(statement: String, meta: Metadata): String = {
      val ksMetas: Seq[KeyspaceMetadata] = meta.getKeyspaces.asScala.toSeq
        .filter(_._2.getTables.size > 0)
        .sortBy(_._1.asCql(true))
        .map(_._2)
      if (ksMetas.isEmpty) {
        NoResultDisplay.formatNoResult
      } else {
        val allTables: Map[(UUID, String), Seq[String]] = ListMap.empty ++
          ksMetas
            .map(ks => {
              ((Uuids.timeBased(), ks.getName.asCql(true)),
                ks.getTables.asScala.toList.map(_._1.asCql(true)).sorted)
            })
            .sortBy { case ((_, name), _) => name }

        val keyspaceDetails: Seq[(UUID, String, String)] = allTables
          .keySet.toSeq.sortBy { case (_, ksName) => ksName }
          .map { case (id, ksName) => (id, ksName, "") }

        val clusterContent: ClusterContent = ClusterContent(clusterName(meta), "", keyspaceDetails)

        MenuDisplay.formatMenuForCluster(statement, clusterContent) +
          engine.layout(ALL_TABLES_TEMPLATE,
            Map[String, Any]("allTables" -> allTables))
      }
    }

    def formatAllUDTs(statement: String, meta: Metadata): String = {
      val ksMetas: Seq[KeyspaceMetadata] = meta.getKeyspaces.asScala.toSeq
        .filter(_._2.getUserDefinedTypes.size > 0)
        .sortBy(_._1.asCql(false))
        .map(_._2)

      if (ksMetas.isEmpty) {
        NoResultDisplay.formatNoResult
      } else {
        val allUDTs: Map[(UUID, String), Seq[String]] = ListMap.empty ++
          ksMetas
            .map ( ks => {
              ((Uuids.timeBased(), ks.getName.asCql(true)),
                ks.getUserDefinedTypes.asScala.toSeq.map(_._1.asCql(true)).sorted)
            })
            .sortBy { case ((_, name), _) => name }

        val keyspaceDetails: List[(UUID, String, String)] = allUDTs
          .keySet.toList.sortBy { case (_, ksName) => ksName }
          .map { case (id, ksName) => (id, ksName, "") }

        val clusterContent: ClusterContent = ClusterContent(clusterName(meta), "", keyspaceDetails)

        MenuDisplay.formatMenuForCluster(statement, clusterContent) +
          engine.layout(ALL_UDTS_TEMPLATE,
            Map[String, Any]("allUDTs" -> allUDTs))
      }
    }

    def formatAllFunctions(statement: String, meta: Metadata): String = {
      val ksMetas: Seq[KeyspaceMetadata] = meta.getKeyspaces.asScala.toSeq
        .filter(_._2.getFunctions.size > 0)
        .sortBy(_._1.asCql(true))
        .map(_._2)

      if (ksMetas.isEmpty) {
        NoResultDisplay.formatNoResult
      } else {
        val allFunctions: Map[(UUID, String), Seq[FunctionSummary]] =
          ksMetas
            .map(ks => {
              ((Uuids.timeBased(), ks.getName.asCql(true)),
                ks.getFunctions.asScala
                  .map { case (_, meta) => MetaDataConverter.functionMetaToFunctionSummary(meta) }
                  .toSeq
                  .sortBy(_.name))
            }).toMap

        val keyspaceDetails: List[(UUID, String, String)] = allFunctions
          .keySet.toList.sortBy { case (_, ksName) => ksName }
          .map { case (id, ksName) => (id, ksName, "") }

        val clusterContent: ClusterContent = ClusterContent(clusterName(meta), "", keyspaceDetails)

        MenuDisplay.formatMenuForCluster(statement, clusterContent) +
          engine.layout(ALL_FUNCTIONS_TEMPLATE,
            Map[String, Any]("allFunctions" -> allFunctions))
      }
    }

    def formatAllAggregates(statement: String, meta: Metadata): String = {
      val ksMetas = meta.getKeyspaces.asScala.toList
        .filter(_._2.getAggregates.size > 0)
        .sortBy(_._1.asCql(false))

      if (ksMetas.isEmpty) {
        NoResultDisplay.formatNoResult
      } else {
        val allAggregates: Map[(UUID, String), Seq[AggregateSummary]] =
          ksMetas
            .map { case (id, ks) =>
              ((Uuids.timeBased(), id.asCql(true)),
                ks.getAggregates.asScala
                  .map { case (sig, meta) => MetaDataConverter.aggregateMetaToAggregateSummary(sig, meta) }
                  .toSeq
                  .sortBy(_.name))
            }.toMap

        val keyspaceDetails: List[(UUID, String, String)] = allAggregates
          .keySet.toList.sortBy { case (_, ksName) => ksName }
          .map { case (id, ksName) => (id, ksName, "") }

        val clusterContent: ClusterContent = ClusterContent(clusterName(meta), "", keyspaceDetails)

        MenuDisplay.formatMenuForCluster(statement, clusterContent) +
          engine.layout(ALL_AGGREGATES_TEMPLATE,
            Map[String, Any]("allAggregates" -> allAggregates))
      }
    }

    def formatAllMaterializedViews(statement: String, meta: Metadata): String = {
      val ksMetas: Seq[KeyspaceMetadata] = meta.getKeyspaces.asScala.toSeq
        .filter(_._2.getViews.size > 0)
        .sortBy(_._1.asCql(false))
        .map(_._2)

      if (ksMetas.isEmpty) {
        NoResultDisplay.formatNoResult
      } else {
        val allMVs: Map[(UUID, String), Seq[MaterializedViewSummary]] = ListMap.empty ++
          ksMetas
            .map( ks =>
              ((Uuids.timeBased(), ks.getName.asCql(true)),
                ks.getViews.asScala.values.toSeq
                  .map(MetaDataConverter.mvMetaToMaterializedViewSummary)
                  .sortBy(_.name)))
            .sortBy { case ((_, name), _) => name }

        val keyspaceDetails: Seq[(UUID, String, String)] = allMVs
          .keySet.toList.sortBy { case (_, ksName) => ksName }
          .map { case (id, ksName) => (id, ksName, "") }

        val clusterContent: ClusterContent = ClusterContent(clusterName(meta), "", keyspaceDetails)

        MenuDisplay.formatMenuForCluster(statement, clusterContent) +
          engine.layout(ALL_MATERIALIZED_VIEWS_TEMPLATE,
            Map[String, Any]("allMVs" -> allMVs))
      }
    }
  }

  object HelpDisplay {

    lazy val driverProperties: Properties = {
      val properties = new Properties()
      try {
        val is = getClass.getClassLoader.getResourceAsStream("driver-info.properties")
        properties.load(is)
        is.close()
      } catch {
        case x: Exception => println(s"Exception: ${x.getMessage}")
      }
      properties
    }

    def formatHelp(): String = {
      engine.layout(HELP_TEMPLATE,
        Map[String, String]("driverVersion" ->
          driverProperties.getProperty("driverVersion", "Unknown")))
    }
  }

  object NoResultDisplay {

    val formatNoResult: String = engine.layout(NO_RESULT_TEMPLATE)

    def nodeToString(node: Node): String = {
      node.getEndPoint.toString
    }

    def noResultWithExecutionInfo(lastQuery: String, execInfo: ExecutionInfo): String = {
      val queriedHosts = nodeToString(execInfo.getCoordinator)
      val triedHosts = (execInfo.getErrors.asScala.map(x => nodeToString(x.getKey))
        .toSet + queriedHosts).mkString(", ")
      val schemaInAgreement = Option(execInfo.isSchemaInAgreement).map(_.toString).getOrElse("N/A")

      engine.layout(NO_RESULT_WITH_EXECINFO_TEMPLATE,
        Map[String, Any]("query" -> lastQuery,
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

  override def hashCode: Int = columnMeta.getName.asCql(true).hashCode
}

object ColumnMetaWrapper {
  def apply(columnMeta: ColumnMetadata): ColumnMetaWrapper = new ColumnMetaWrapper(columnMeta)
}

/**
 * Convert Java driver
 * meta data structure
 * to our own structure
 */
object MetaDataConverter {

  type DriverClusteringOrder = com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder

  def functionMetaToFunctionDetails(function: FunctionMetadata): FunctionDetails = {
    val signature = function.getSignature
    FunctionDetails(function.getKeyspace.asCql(true),
      signature.getName.asCql(true),
      function.getParameterNames.asScala.zip(signature.getParameterTypes.asScala)
        .map { case (paramName, dataType) => paramName + " " + dataType.asCql(true, true) }
        .toList,
      function.isCalledOnNullInput,
      function.getReturnType.asCql(true, true),
      function.getLanguage,
      function.getBody,
      function.describe(true))
  }

  def functionMetaToFunctionSummary(function: FunctionMetadata): FunctionSummary = {
    val signature = function.getSignature
    FunctionSummary(function.getKeyspace.asCql(true),
      signature.getName.asCql(true),
      function.getParameterNames.asScala.zip(signature.getParameterTypes.asScala)
        .map { case (paramName, dataType) => paramName + " " + dataType.asCql(true, true) }
        .toList,
      function.getReturnType.asCql(true, true)
    )
  }

  def aggregateMetaToAggregateDetails(codecRegistry: CodecRegistry, aggregate: AggregateMetadata): AggregateDetails = {
    val sType = aggregate.getStateType
    val initCond: Option[String] = Option(aggregate.getInitCond)
      .map(value => codecRegistry.codecFor(sType).format(value.get))
    val returnType = aggregate.getReturnType
    val finalFunc: Option[String] = aggregate.getFinalFuncSignature.asScala
      .map(x => x.getName.asCql(true) +
        x.getParameterTypes.asScala.map(_.asCql(true, true)).mkString("(", ", ", ")"))

    val sFunc = aggregate.getStateFuncSignature
    val sFuncString = sFunc.getName.asCql(true) + sFunc.getParameterTypes.asScala
      .map(_.asCql(true, true)).mkString("(", ", ", ")")

    AggregateDetails(aggregate.getKeyspace.asCql(true),
      aggregate.getSignature.getName.asCql(true),
      aggregate.getSignature.getParameterTypes.asScala.map(_.asCql(true, true)),
      sFuncString,
      sType.asCql(true, true),
      finalFunc,
      initCond,
      returnType.asCql(true, true),
      aggregate.describe(true)
    )
  }

  def aggregateMetaToAggregateSummary(signature: FunctionSignature, aggregate: AggregateMetadata): AggregateSummary = {
    val returnType: String = aggregate.getReturnType.asCql(true, true)

    AggregateSummary(aggregate.getKeyspace.asCql(true),
      signature.getName.asCql(true),
      signature.getParameterTypes.asScala.map( _.asCql(true, true)),
      returnType
    )
  }

  def mvMetaToMaterializedViewDetails(mv: ViewMetadata): MaterializedViewDetails = {
    MaterializedViewDetails(mv.getName.asCql(true), MV.mvMetaToColumnDetails(mv), mv.describe(true),
      mv.getBaseTable.asCql(true))
  }

  def mvMetaToMaterializedViewSummary(mv: ViewMetadata): MaterializedViewSummary = {
    MaterializedViewSummary(mv.getName.asCql(true), mv.getBaseTable.asCql(true))
  }

  trait TableOrView {
    protected def extractNormalColumns(columns: Seq[ColumnMetaWrapper]): Seq[ColumnDetails] = {
      columns.filter(_.columnMeta.isStatic == false)
        .map(c => ColumnDetails(c.columnMeta.getName.asCql(true), NormalColumn, c.columnMeta.getType))
    }

    protected def extractStaticColumns(columns: Seq[ColumnMetaWrapper]): Seq[ColumnDetails] = {
      columns.filter(_.columnMeta.isStatic == true)
        .map(c => ColumnDetails(c.columnMeta.getName.asCql(true), StaticColumn, c.columnMeta.getType))
    }

    protected def convertClusteringColumns(columns: Seq[ColumnMetaWrapper], orders: Seq[DriverClusteringOrder]): Seq[ColumnDetails] = {
      columns.zip(orders)
        .map { case (c, order) => ColumnDetails(c.columnMeta.getName.asCql(true),
          ClusteringColumn(OrderConverter.convert(order)), c.columnMeta.getType)
        }
    }

    protected def convertPartitionKeys(columns: Seq[ColumnMetaWrapper]): Seq[ColumnDetails] = {
      columns
        .map(c => ColumnDetails(c.columnMeta.getName.asCql(true), PartitionKey, c.columnMeta.getType))
    }

    def relationMetaToColumnDetails(meta: RelationMetadata): Seq[ColumnDetails] = {
      val partitionKeys: Seq[ColumnMetaWrapper] = meta.getPartitionKey.asScala.map(ColumnMetaWrapper(_))

      // mutable structures are used to keep the order of the columns - it's lost if we're doing .asScala, etc.
      val clusteringColumnsRaw = meta.getClusteringColumns
      val clusteringColumns = new Array[ColumnMetaWrapper](clusteringColumnsRaw.size())
      val clusteringOrders =  new Array[DriverClusteringOrder](clusteringColumnsRaw.size())
      var i = 0
      for (meta <- clusteringColumnsRaw.keySet().iterator().asScala) {
        clusteringColumns(i) = ColumnMetaWrapper(meta)
        clusteringOrders(i) = clusteringColumnsRaw.get(meta)
        i += 1
      }

      val primaryKeyNames = meta.getPrimaryKey.asScala.map(_.getName).toSet
      val columnsRaw = meta.getColumns
      val columns = new Array[ColumnMetaWrapper](columnsRaw.size() - primaryKeyNames.size)
      i = 0
      for (col <- columnsRaw.keySet().iterator().asScala) {
        if (!primaryKeyNames.contains(col)) {
          columns(i) =  ColumnMetaWrapper(columnsRaw.get(col))
          i += 1
        }
      }

      convertPartitionKeys(partitionKeys) ++
        extractStaticColumns(columns) ++
        convertClusteringColumns(clusteringColumns, clusteringOrders) ++
        extractNormalColumns(columns)
    }

  }

  object Table extends TableOrView {
    def tableMetaToColumnDetails(meta: TableMetadata): Seq[ColumnDetails] = {
      relationMetaToColumnDetails(meta)
    }

    def tableMetaToIndexDetails(meta: TableMetadata): Seq[IndexDetails] = {
      meta.getIndexes.asScala.map {
        case (name: CqlIdentifier, index: IndexMetadata) =>
          IndexDetails(name.asCql(true), index.getTarget, index.describe(true))
      }.toSeq.sortBy(_.name)
    }
  }

  object MV extends TableOrView {
    def mvMetaToColumnDetails(meta: ViewMetadata): Seq[ColumnDetails] = {
      relationMetaToColumnDetails(meta)
    }
  }

  object UDT {
    def userTypeToColumnDetails(userType: UserDefinedType): Seq[ColumnDetails] = {
      userType.getFieldNames.asScala.zip(userType.getFieldTypes.asScala)
        .map { case (name, typ) => ColumnDetails(name.asCql(true), NormalColumn, typ) }
    }
  }
}
