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

import java.util.regex.Pattern

import com.datastax.driver.core._
import org.apache.zeppelin.cassandra.TextBlockHierarchy._
import org.apache.zeppelin.interpreter.InterpreterException
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/**
 * Enhance the Java driver session
 * with special statements
 * to describe schema
 */
class EnhancedSession(val session: Session) {

  val clusterDisplay = DisplaySystem.ClusterDisplay
  val keyspaceDisplay = DisplaySystem.KeyspaceDisplay
  val tableDisplay = DisplaySystem.TableDisplay
  val udtDisplay = DisplaySystem.UDTDisplay
  val functionDisplay = DisplaySystem.FunctionDisplay
  val aggregateDisplay = DisplaySystem.AggregateDisplay
  val materializedViewDisplay = DisplaySystem.MaterializedViewDisplay
  val helpDisplay = DisplaySystem.HelpDisplay
  private val noResultDisplay = DisplaySystem.NoResultDisplay
  private val DEFAULT_CHECK_TIME = 200 // half second
  private val LOGGER = LoggerFactory.getLogger(classOf[EnhancedSession])

  val HTML_MAGIC = "%html \n"

  val displayNoResult: String = HTML_MAGIC + noResultDisplay.formatNoResult

  def displayExecutionStatistics(query: String, execInfo: ExecutionInfo): String = {
    HTML_MAGIC + noResultDisplay.noResultWithExecutionInfo(query, execInfo)
  }

  private def execute(describeCluster: DescribeClusterCmd): String = {
    val metaData = session.getCluster.getMetadata
    HTML_MAGIC + clusterDisplay.formatClusterOnly(describeCluster.statement, metaData)
  }

  private def execute(describeKeyspaces: DescribeKeyspacesCmd): String = {
    val metaData = session.getCluster.getMetadata
    HTML_MAGIC + clusterDisplay.formatClusterContent(describeKeyspaces.statement, metaData)
  }

  private def execute(describeTables: DescribeTablesCmd): String = {
    val metadata: Metadata = session.getCluster.getMetadata
    HTML_MAGIC + clusterDisplay.formatAllTables(describeTables.statement,metadata)
  }

  private def execute(describeKeyspace: DescribeKeyspaceCmd): String = {
    val keyspace: String = describeKeyspace.keyspace
    val metadata: Option[KeyspaceMetadata] = Option(session.getCluster.getMetadata.getKeyspace(keyspace))
    metadata match {
      case Some(ksMeta) => HTML_MAGIC + keyspaceDisplay.formatKeyspaceContent(describeKeyspace.statement, ksMeta,
        session.getCluster.getConfiguration.getCodecRegistry)
      case None => throw new InterpreterException(s"Cannot find keyspace $keyspace")
    }
  }

  private def execute(describeTable: DescribeTableCmd): String = {
    val metaData = session.getCluster.getMetadata
    val tableName: String = describeTable.table
    val keyspace: String = describeTable.keyspace.orElse(Option(session.getLoggedKeyspace)).getOrElse("system")

    Option(metaData.getKeyspace(keyspace)).flatMap(ks => Option(ks.getTable(tableName))) match {
      case Some(tableMeta) => HTML_MAGIC + tableDisplay.format(describeTable.statement, tableMeta, true)
      case None => throw new InterpreterException(s"Cannot find table $keyspace.$tableName")
    }
  }

  private def execute(describeUDT: DescribeTypeCmd): String = {
    val metaData = session.getCluster.getMetadata
    val keyspace: String = describeUDT.keyspace.orElse(Option(session.getLoggedKeyspace)).getOrElse("system")
    val udtName: String = describeUDT.udtName

    Option(metaData.getKeyspace(keyspace)).flatMap(ks => Option(ks.getUserType(udtName))) match {
      case Some(userType) => HTML_MAGIC + udtDisplay.format(describeUDT.statement, userType, true)
      case None => throw new InterpreterException(s"Cannot find type $keyspace.$udtName")
    }
  }

  private def execute(describeUDTs: DescribeTypesCmd): String = {
    val metadata: Metadata = session.getCluster.getMetadata
    HTML_MAGIC + clusterDisplay.formatAllUDTs(describeUDTs.statement, metadata)
  }

  private def execute(describeFunction: DescribeFunctionCmd): String = {
    val metaData = session.getCluster.getMetadata
    val keyspaceName: String = describeFunction.keyspace.orElse(Option(session.getLoggedKeyspace)).getOrElse("system")
    val functionName: String = describeFunction.function;

    Option(metaData.getKeyspace(keyspaceName)) match {
      case Some(keyspace) => {
        val functionMetas: List[FunctionMetadata] = keyspace.getFunctions.asScala.toList
          .filter(func => func.getSimpleName.toLowerCase == functionName.toLowerCase)

        if(functionMetas.isEmpty) {
          throw new InterpreterException(s"Cannot find function ${keyspaceName}.$functionName")
        } else {
          HTML_MAGIC + functionDisplay.format(describeFunction.statement, functionMetas, true)
        }
      }
      case None => throw new InterpreterException(s"Cannot find function ${keyspaceName}.$functionName")
    }
  }

  private def execute(describeFunctions: DescribeFunctionsCmd): String = {
    val metadata: Metadata = session.getCluster.getMetadata
    HTML_MAGIC + clusterDisplay.formatAllFunctions(describeFunctions.statement, metadata)
  }

  private def execute(describeAggregate: DescribeAggregateCmd): String = {
    val metaData = session.getCluster.getMetadata
    val keyspaceName: String = describeAggregate.keyspace.orElse(Option(session.getLoggedKeyspace)).getOrElse("system")
    val aggregateName: String = describeAggregate.aggregate;

    Option(metaData.getKeyspace(keyspaceName)) match {
      case Some(keyspace) => {
        val aggMetas: List[AggregateMetadata] = keyspace.getAggregates.asScala.toList
          .filter(agg => agg.getSimpleName.toLowerCase == aggregateName.toLowerCase)

        if(aggMetas.isEmpty) {
          throw new InterpreterException(s"Cannot find aggregate ${keyspaceName}.$aggregateName")
        } else {
          HTML_MAGIC + aggregateDisplay.format(describeAggregate.statement, aggMetas, true,
            session
            .getCluster
            .getConfiguration
            .getCodecRegistry)
        }
      }
      case None => throw new InterpreterException(s"Cannot find aggregate ${keyspaceName}.$aggregateName")
    }
  }

  private def execute(describeAggregates: DescribeAggregatesCmd): String = {
    val metadata: Metadata = session.getCluster.getMetadata
    HTML_MAGIC + clusterDisplay.formatAllAggregates(describeAggregates.statement, metadata)
  }

  private def execute(describeMV: DescribeMaterializedViewCmd): String = {
    val metaData = session.getCluster.getMetadata
    val keyspaceName: String = describeMV.keyspace.orElse(Option(session.getLoggedKeyspace)).getOrElse("system")
    val viewName: String = describeMV.view

    Option(metaData.getKeyspace(keyspaceName)) match {
      case Some(keyspace) => {
        val viewMeta: Option[MaterializedViewMetadata] = Option(keyspace.getMaterializedView(viewName))
        viewMeta match {
          case Some(vMeta) => HTML_MAGIC + materializedViewDisplay.format(describeMV.statement, vMeta, true)
          case None => throw new InterpreterException(s"Cannot find materialized view ${keyspaceName}.$viewName")
        }
      }
      case None => throw new InterpreterException(s"Cannot find materialized view ${keyspaceName}.$viewName")
    }
  }

  private def execute(describeMVs: DescribeMaterializedViewsCmd): String = {
    val metadata: Metadata = session.getCluster.getMetadata
    HTML_MAGIC + clusterDisplay.formatAllMaterializedViews(describeMVs.statement, metadata)
  }

  private def execute(helpCmd: HelpCmd): String = {
    HTML_MAGIC + helpDisplay.formatHelp()
  }


  private def execute(st: Statement): Any = {
    val rs = session.execute(st)
    if (EnhancedSession.isDDLStatement(st)) {
      if (!rs.getExecutionInfo.isSchemaInAgreement) {
        val metadata = session.getCluster.getMetadata
        while(!metadata.checkSchemaAgreement) {
          LOGGER.info("Schema is still not in agreement, waiting...")
          Thread.sleep(DEFAULT_CHECK_TIME)
        }
      }
    }
    rs
  }

  def execute(st: Any): Any = {
    st match {
      case x:DescribeClusterCmd => execute(x)
      case x:DescribeKeyspaceCmd => execute(x)
      case x:DescribeKeyspacesCmd => execute(x)
      case x:DescribeTableCmd => execute(x)
      case x:DescribeTablesCmd => execute(x)
      case x:DescribeTypeCmd => execute(x)
      case x:DescribeTypesCmd => execute(x)
      case x:DescribeFunctionCmd => execute(x)
      case x:DescribeFunctionsCmd => execute(x)
      case x:DescribeAggregateCmd => execute(x)
      case x:DescribeAggregatesCmd => execute(x)
      case x:DescribeMaterializedViewCmd => execute(x)
      case x:DescribeMaterializedViewsCmd => execute(x)
      case x:HelpCmd => execute(x)
      case x:Statement => execute(x)
      case _ => throw new InterpreterException(s"Cannot execute statement '$st' of type ${st.getClass}")
    }
  }
}

object EnhancedSession {
  private val DDL_REGEX = Pattern.compile("^(CREATE|DROP|ALTER) .*", Pattern.CASE_INSENSITIVE)

  def isDDLStatement(query: String): Boolean = {
    DDL_REGEX.matcher(query.trim).matches()
  }

  def isDDLStatement(st: Statement): Boolean = {
    st match {
      case x:BoundStatement =>
        isDDLStatement(x.preparedStatement.getQueryString)
      case x:BatchStatement =>
        x.getStatements.asScala.seq.exists(isDDLStatement)
      case x:RegularStatement =>
        isDDLStatement(x.getQueryString)
      case _ => // only should be for StatementWrapper
        true
    }
  }
}