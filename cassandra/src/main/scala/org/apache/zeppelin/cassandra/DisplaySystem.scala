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
  val ALL_TABLES_TEMPLATE = "scalate/allTables.ssp"

  val UDT_DETAILS_TEMPLATE = "scalate/udtDetails.ssp"
  val ALL_UDTS_TEMPLATE = "scalate/allUDTs.ssp"

  val FUNCTION_DETAILS_TEMPLATE = "scalate/functionDetails.ssp"
  val ALL_FUNCTIONS_TEMPLATE = "scalate/allFunctions.ssp"

  val AGGREGATE_DETAILS_TEMPLATE = "scalate/aggregateDetails.ssp"
  val ALL_AGGREGATES_TEMPLATE = "scalate/allAggregates.ssp"

  val MATERIALIZED_VIEW_DETAILS_TEMPLATE = "scalate/materializedViewDetails.ssp"
  val ALL_MATERIALIZED_VIEWS_TEMPLATE = "scalate/allMaterializedViews.ssp"

  val MENU_TEMPLATE = "scalate/menu.ssp"
  val CLUSTER_DROPDOWN_TEMPLATE = "scalate/dropDownMenuForCluster.ssp"

  val KEYSPACE_DROPDOWN_TEMPLATE = "scalate/dropDownMenuForKeyspace.ssp"
  val CLUSTER_CONTENT_TEMPLATE = "scalate/clusterContent.ssp"
  val KEYSPACE_CONTENT_TEMPLATE = "scalate/keyspaceContent.ssp"



  object TableDisplay {

    def format(statement: String, meta: TableMetadata, withCaption: Boolean): String = {
      MenuDisplay.formatMenu(statement) + formatWithoutMenu(meta, withCaption)
    }

    protected[DisplaySystem] def formatWithoutMenu(meta: TableMetadata, withCaption: Boolean): String = {
      val tableName: String = meta.getName
      val columnsDetails = MetaDataConverter.Table.tableMetaToColumnDetails(meta)
      val indicesDetails = MetaDataConverter.Table.tableMetaToIndexDetails(meta)
      val indicesAsCQL = indicesDetails.map(_.asCQL).mkString("\n")

      engine.layout(TABLE_DETAILS_TEMPLATE,
        Map[String, Any]("tableDetails" -> TableDetails(tableName, columnsDetails, indicesDetails, TableMetadataWrapper(meta).exportTableOnlyAsString(), indicesAsCQL), "withCaption" -> withCaption))
    }
  }

  object UDTDisplay {
    def format(statement: String, userType: UserType, withCaption: Boolean): String = {
      MenuDisplay.formatMenu(statement) ++ formatWithoutMenu(userType, withCaption)
    }

    protected[DisplaySystem] def formatWithoutMenu(userType: UserType, withCaption: Boolean): String = {
      val udtName: String = userType.getTypeName
      val columnsDetails = MetaDataConverter.UDT.userTypeToColumnDetails(userType)

      engine.layout(UDT_DETAILS_TEMPLATE,
        Map[String, Any]("udtDetails" -> UDTDetails(udtName,columnsDetails,userType.exportAsString()), "withCaption" -> withCaption))
    }
  }

  object FunctionDisplay {
    def format(statement: String, functions: List[FunctionMetadata], withCaption: Boolean): String = {
      MenuDisplay.formatMenu(statement) ++ formatWithoutMenu(functions, withCaption)
    }

    protected[DisplaySystem] def formatWithoutMenu(functions: List[FunctionMetadata], withCaption: Boolean): String = {
      val functionDetails: List[FunctionDetails] = functions.map(MetaDataConverter.functionMetaToFunctionDetails(_))
      engine.layout(FUNCTION_DETAILS_TEMPLATE,
        Map[String, Any]("sameNameFunctionDetails" -> SameNameFunctionDetails(functionDetails), "withCaption" -> withCaption))
    }
  }

  object AggregateDisplay {
    def format(statement: String, aggregates: List[AggregateMetadata], withCaption: Boolean, codecRegistry: CodecRegistry): String = {
      MenuDisplay.formatMenu(statement) ++ formatWithoutMenu(aggregates, withCaption, codecRegistry)
    }

    protected[DisplaySystem] def formatWithoutMenu(aggregates: List[AggregateMetadata], withCaption: Boolean, codecRegistry: CodecRegistry): String = {
      val aggDetails: List[AggregateDetails] = aggregates.map(agg => MetaDataConverter.aggregateMetaToAggregateDetails(codecRegistry, agg))
      engine.layout(AGGREGATE_DETAILS_TEMPLATE,
        Map[String, Any]("sameNameAggregateDetails" -> SameNameAggregateDetails(aggDetails), "withCaption" -> withCaption))
    }
  }

  object MaterializedViewDisplay {
    def format(statement: String, mv: MaterializedViewMetadata, withCaption: Boolean): String = {
      MenuDisplay.formatMenu(statement) ++ formatWithoutMenu(mv, withCaption)
    }

    protected[DisplaySystem] def formatWithoutMenu(mv: MaterializedViewMetadata, withCaption: Boolean): String = {
      val mvDetails = MetaDataConverter.mvMetaToMaterializedViewDetails(mv)
      engine.layout(MATERIALIZED_VIEW_DETAILS_TEMPLATE,
        Map[String, Any]("mvDetails" -> mvDetails, "withCaption" -> withCaption))
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

    def formatKeyspaceContent(statement: String, meta: KeyspaceMetadata, codecRegistry: CodecRegistry): String = {
      val ksName: String = meta.getName
      val ksDetails = formatKeyspaceOnly(meta, true)

      val tableDetails: List[(UUID, String, String)] = meta.getTables.asScala.toList
        .sortBy(_.getName)
        .map(table => (UUIDs.timeBased(), table.getName, TableDisplay.formatWithoutMenu(table, false)))

      val viewDetails: List[(UUID, String, String)] = meta.getMaterializedViews.asScala.toList
        .sortBy(_.getName)
        .map(view => (UUIDs.timeBased(), view.getName, MaterializedViewDisplay.formatWithoutMenu(view, false)))

      val udtDetails: List[(UUID, String, String)] = meta.getUserTypes.asScala.toList
        .sortBy(_.getTypeName)
        .map(udt => (UUIDs.timeBased(), udt.getTypeName, UDTDisplay.formatWithoutMenu(udt, false)))

      val functionDetails: List[(UUID, String, String)] = meta.getFunctions.asScala.toList
        .sortBy(_.getSimpleName)
        .map(function => (UUIDs.timeBased(), function.getSimpleName, FunctionDisplay.formatWithoutMenu(List(function), false)))

      val aggregateDetails: List[(UUID, String, String)] = meta.getAggregates.asScala.toList
        .sortBy(_.getSimpleName)
        .map(agg => (UUIDs.timeBased(), agg.getSimpleName, AggregateDisplay.formatWithoutMenu(List(agg), false, codecRegistry)))

      val ksContent: KeyspaceContent = KeyspaceContent(ksName, ksDetails, tableDetails, viewDetails,
        udtDetails, functionDetails, aggregateDetails)

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
        .filter(_.getTables.size > 0)
        .sortBy(ks => ks.getName)
      if(ksMetas.isEmpty) {
        NoResultDisplay.formatNoResult
      } else {
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

    def formatAllUDTs(statement: String, meta: Metadata): String = {
      val ksMetas: List[KeyspaceMetadata] = meta.getKeyspaces.asScala.toList
        .filter(_.getUserTypes.size > 0)
        .sortBy(ks => ks.getName)

      if(ksMetas.isEmpty) {
        NoResultDisplay.formatNoResult
      } else {
        val allUDTs: Map[(UUID, String), List[String]] = ListMap.empty ++
          ksMetas
            .map(ks => {
              ((UUIDs.timeBased(), ks.getName),
                ks.getUserTypes.asScala.toList.map(udt => udt.getTypeName).sortBy(name => name))
            })
            .sortBy { case ((id, name), _) => name }


        val keyspaceDetails: List[(UUID, String, String)] = allUDTs
          .keySet.toList.sortBy { case (id, ksName) => ksName }
          .map { case (id, ksName) => (id, ksName, "") }

        val clusterContent: ClusterContent = ClusterContent(meta.getClusterName, "", keyspaceDetails)

        MenuDisplay.formatMenuForCluster(statement, clusterContent) +
          engine.layout(ALL_UDTS_TEMPLATE,
            Map[String, Any]("allUDTs" -> allUDTs))
      }
    }

    def formatAllFunctions(statement: String, meta: Metadata): String = {
      val ksMetas: List[KeyspaceMetadata] = meta.getKeyspaces.asScala.toList
        .filter(_.getFunctions.size > 0)
        .sortBy(ks => ks.getName)

      if(ksMetas.isEmpty) {
        NoResultDisplay.formatNoResult
      } else {
        val allFunctions: Map[(UUID, String), List[FunctionSummary]] = ListMap.empty ++
          ksMetas
            .map(ks => {
              ((UUIDs.timeBased(), ks.getName),
                ks.getFunctions.asScala.toList
                  .map(MetaDataConverter.functionMetaToFunctionSummary(_))
                  .sortBy(_.name))
            })
            .sortBy { case ((id, name), _) => name }

        val keyspaceDetails: List[(UUID, String, String)] = allFunctions
          .keySet.toList.sortBy { case (id, ksName) => ksName }
          .map { case (id, ksName) => (id, ksName, "") }


        val clusterContent: ClusterContent = ClusterContent(meta.getClusterName, "", keyspaceDetails)

        MenuDisplay.formatMenuForCluster(statement, clusterContent) +
          engine.layout(ALL_FUNCTIONS_TEMPLATE,
            Map[String, Any]("allFunctions" -> allFunctions))
      }
    }

    def formatAllAggregates(statement: String, meta: Metadata): String = {
      val ksMetas: List[KeyspaceMetadata] = meta.getKeyspaces.asScala.toList
        .filter(_.getAggregates.size > 0)
        .sortBy(ks => ks.getName)

      if(ksMetas.isEmpty) {
        NoResultDisplay.formatNoResult
      } else {
        val allAggregates: Map[(UUID, String), List[AggregateSummary]] = ListMap.empty ++
          ksMetas
            .map(ks => {
              ((UUIDs.timeBased(), ks.getName),
                ks.getAggregates.asScala.toList
                  .map(MetaDataConverter.aggregateMetaToAggregateSummary(_))
                  .sortBy(_.name))
            })
            .sortBy { case ((id, name), _) => name }

        val keyspaceDetails: List[(UUID, String, String)] = allAggregates
          .keySet.toList.sortBy { case (id, ksName) => ksName }
          .map { case (id, ksName) => (id, ksName, "") }


        val clusterContent: ClusterContent = ClusterContent(meta.getClusterName, "", keyspaceDetails)

        MenuDisplay.formatMenuForCluster(statement, clusterContent) +
          engine.layout(ALL_AGGREGATES_TEMPLATE,
            Map[String, Any]("allAggregates" -> allAggregates))
      }
    }

    def formatAllMaterializedViews(statement: String, meta: Metadata): String = {
      val ksMetas: List[KeyspaceMetadata] = meta.getKeyspaces.asScala.toList
        .filter(_.getMaterializedViews.size > 0)
        .sortBy(ks => ks.getName)

      if(ksMetas.isEmpty) {
        NoResultDisplay.formatNoResult
      } else {
        val allMVs: Map[(UUID, String), List[MaterializedViewSummary]] = ListMap.empty ++
          ksMetas
            .map(ks => {
              ((UUIDs.timeBased(), ks.getName),
                ks.getMaterializedViews.asScala.toList
                  .map(MetaDataConverter.mvMetaToMaterializedViewSummary(_))
                  .sortBy(_.name))
            })
            .sortBy { case ((id, name), _) => name }

        val keyspaceDetails: List[(UUID, String, String)] = allMVs
          .keySet.toList.sortBy { case (id, ksName) => ksName }
          .map { case (id, ksName) => (id, ksName, "") }


        val clusterContent: ClusterContent = ClusterContent(meta.getClusterName, "", keyspaceDetails)

        MenuDisplay.formatMenuForCluster(statement, clusterContent) +
          engine.layout(ALL_MATERIALIZED_VIEWS_TEMPLATE,
            Map[String, Any]("allMVs" -> allMVs))
      }
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

  type DriverClusteringOrder = com.datastax.driver.core.ClusteringOrder

  def functionMetaToFunctionDetails(function: FunctionMetadata): FunctionDetails = {
    new FunctionDetails(function.getKeyspace.getName,
      function.getSimpleName,
      function.getArguments.asScala
        .toMap
        .map{case(paramName, dataType) => paramName + " " + dataType.asFunctionParameterString()}
        .toList,
      function.isCalledOnNullInput,
      function.getReturnType.asFunctionParameterString(),
      function.getLanguage,
      function.getBody,
      function.exportAsString())
  }

  def functionMetaToFunctionSummary(function: FunctionMetadata): FunctionSummary = {
    new FunctionSummary(function.getKeyspace.getName,
      function.getSimpleName,
      function.getArguments.asScala.toMap
        .mapValues(dataType => dataType.asFunctionParameterString())
        .values.toList,
      function.getReturnType.asFunctionParameterString()
    )
  }

  def aggregateMetaToAggregateDetails(codecRegistry: CodecRegistry, aggregate: AggregateMetadata): AggregateDetails = {
    val sFunc: FunctionSummary = functionMetaToFunctionSummary(aggregate.getStateFunc)
    val finalFunc: Option[String] = Option(aggregate.getFinalFunc).map(func => {
      val finalFunction = functionMetaToFunctionSummary(func)
      finalFunction.name + finalFunction.arguments.mkString("(", ", ", ")")
    })
    val sType = aggregate.getStateType
    val initCond: Option[String] = Option(aggregate.getInitCond).map(codecRegistry.codecFor(sType).format(_))
    val returnType: String = Option(aggregate.getFinalFunc) match {
      case Some(finalFunc) => functionMetaToFunctionSummary(finalFunc).returnType
      case None => sFunc.returnType
    }

    new AggregateDetails(aggregate.getKeyspace.getName,
      aggregate.getSimpleName,
      aggregate.getArgumentTypes.asScala.toList.map(_.asFunctionParameterString()),
      sFunc.name + sFunc.arguments.mkString("(",", ", ")"),
      sType.asFunctionParameterString(),
      finalFunc,
      initCond,
      returnType,
      aggregate.exportAsString())
  }

  def aggregateMetaToAggregateSummary(aggregate: AggregateMetadata): AggregateSummary = {
    val returnType: String = Option(aggregate.getFinalFunc) match {
      case Some(finalFunc) => functionMetaToFunctionSummary(finalFunc).returnType
      case None => aggregate.getStateType.asFunctionParameterString()
    }

    new AggregateSummary(aggregate.getKeyspace.getName,
      aggregate.getSimpleName,
      aggregate.getArgumentTypes.asScala.toList.map(_.asFunctionParameterString()),
      returnType
    )
  }

  def mvMetaToMaterializedViewDetails(mv: MaterializedViewMetadata): MaterializedViewDetails = {
    new MaterializedViewDetails(mv.getName, MV.mvMetaToColumnDetails(mv), mv.exportAsString(), mv.getBaseTable.getName)
  }

  def mvMetaToMaterializedViewSummary(mv: MaterializedViewMetadata): MaterializedViewSummary = {
    new MaterializedViewSummary(mv.getName, mv.getBaseTable.getName)
  }

  trait TableOrView {
    protected def extractNormalColumns(columns: List[ColumnMetaWrapper]): List[ColumnDetails] = {
      columns
        .filter(_.columnMeta.isStatic == false)
        .map(c => new ColumnDetails(c.columnMeta.getName, NormalColumn, c.columnMeta.getType))
    }

    protected def extractStaticColumns(columns: List[ColumnMetaWrapper]): List[ColumnDetails] = {
      columns
        .filter(_.columnMeta.isStatic == true)
        .map(c => new ColumnDetails(c.columnMeta.getName, StaticColumn, c.columnMeta.getType))
    }

    protected def convertClusteringColumns(columns: List[ColumnMetaWrapper], orders: List[DriverClusteringOrder]): List[ColumnDetails] = {
      columns
        .zip(orders)
        .map{case(c,order) => new ColumnDetails(c.columnMeta.getName,
          new ClusteringColumn(OrderConverter.convert(order)),c.columnMeta.getType)}

    }

    protected def convertPartitionKeys(columns: List[ColumnMetaWrapper]): List[ColumnDetails] = {
      columns
        .map(c => new ColumnDetails(c.columnMeta.getName, PartitionKey, c.columnMeta.getType))
    }
  }

  object Table extends TableOrView {
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

    def tableMetaToIndexDetails(meta: TableMetadata): List[IndexDetails] = {
      meta.getIndexes.asScala.toList
        .map(index => IndexDetails(index.getName, index.getTarget, index.asCQLQuery()))
        .sortBy(index => index.name)
    }


  }

  object MV extends TableOrView {
    def mvMetaToColumnDetails(meta: MaterializedViewMetadata): List[ColumnDetails] = {
      val partitionKeys: List[ColumnMetaWrapper] = meta.getPartitionKey.asScala.toList.map(new ColumnMetaWrapper(_))
      val clusteringColumns: List[ColumnMetaWrapper] = meta.getClusteringColumns.asScala.toList.map(new ColumnMetaWrapper(_))
      val columns: List[ColumnMetaWrapper] = meta.getColumns.asScala.toList.map(new ColumnMetaWrapper(_))
        .diff(partitionKeys).diff(clusteringColumns)
      val clusteringOrders = meta.getClusteringOrder.asScala.toList

      convertPartitionKeys(partitionKeys):::
        convertClusteringColumns(clusteringColumns, clusteringOrders):::
        extractNormalColumns(columns)
    }
  }

  object UDT {
    def userTypeToColumnDetails(userType: UserType): List[ColumnDetails] = {
      userType.getFieldNames.asScala.toList
        .map(name => new ColumnDetails(name, NormalColumn, userType.getFieldType(name)))
    }
  }



}





