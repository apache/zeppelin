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

import java.time.Duration
import java.util.regex.Pattern

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.{BatchStatement, BatchType, BoundStatement, ExecutionInfo, ResultSet, SimpleStatement, Statement}
import com.datastax.oss.driver.api.core.metadata.Metadata
import com.datastax.oss.driver.api.core.metadata.schema.{AggregateMetadata, FunctionMetadata, KeyspaceMetadata, ViewMetadata}
import org.apache.zeppelin.cassandra.TextBlockHierarchy._
import org.apache.zeppelin.interpreter.InterpreterException
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._

/**
 * Enhance the Java driver session
 * with special statements
 * to describe schema
 */
class EnhancedSession(val session: CqlSession) {
  val clusterDisplay: DisplaySystem.ClusterDisplay.type = DisplaySystem.ClusterDisplay
  val keyspaceDisplay: DisplaySystem.KeyspaceDisplay.type = DisplaySystem.KeyspaceDisplay
  val tableDisplay: DisplaySystem.TableDisplay.type = DisplaySystem.TableDisplay
  val udtDisplay: DisplaySystem.UDTDisplay.type = DisplaySystem.UDTDisplay
  val functionDisplay: DisplaySystem.FunctionDisplay.type = DisplaySystem.FunctionDisplay
  val aggregateDisplay: DisplaySystem.AggregateDisplay.type = DisplaySystem.AggregateDisplay
  val materializedViewDisplay: DisplaySystem.MaterializedViewDisplay.type = DisplaySystem.MaterializedViewDisplay
  val helpDisplay: DisplaySystem.HelpDisplay.type = DisplaySystem.HelpDisplay

  private val noResultDisplay = DisplaySystem.NoResultDisplay
  private val DEFAULT_CHECK_TIME: Int = 200
  private val MAX_SCHEMA_AGREEMENT_WAIT: Int = 120000 // 120 seconds
  private val defaultDDLTimeout: Duration = Duration.ofSeconds(MAX_SCHEMA_AGREEMENT_WAIT / 10000)
  private val LOGGER = LoggerFactory.getLogger(classOf[EnhancedSession])

  val HTML_MAGIC = "%html \n"

  val displayNoResult: String = HTML_MAGIC + noResultDisplay.formatNoResult

  def displayExecutionStatistics(query: String, execInfo: ExecutionInfo): String = {
    HTML_MAGIC + noResultDisplay.noResultWithExecutionInfo(query, execInfo)
  }

  private def execute(describeCluster: DescribeClusterCmd): String = {
    val metaData = session.getMetadata
    HTML_MAGIC + clusterDisplay.formatClusterOnly(describeCluster.statement, metaData)
  }

  private def execute(describeKeyspaces: DescribeKeyspacesCmd): String = {
    val metaData = session.getMetadata
    HTML_MAGIC + clusterDisplay.formatClusterContent(describeKeyspaces.statement, metaData)
  }

  private def execute(describeTables: DescribeTablesCmd): String = {
    val metadata = session.getMetadata
    HTML_MAGIC + clusterDisplay.formatAllTables(describeTables.statement,metadata)
  }

  private def execute(describeKeyspace: DescribeKeyspaceCmd): String = {
    val keyspace: String = describeKeyspace.keyspace
    val metadata: Option[KeyspaceMetadata] = session.getMetadata.getKeyspace(keyspace).asScala
    metadata match {
      case Some(ksMeta) => HTML_MAGIC + keyspaceDisplay.formatKeyspaceContent(describeKeyspace.statement, ksMeta,
        session.getContext.getCodecRegistry)
      case None => throw new InterpreterException(s"Cannot find keyspace $keyspace")
    }
  }

  private def getKeySpace(session: CqlSession): String = {
    session.getKeyspace.asScala.map(_.asCql(true)).getOrElse("system")
  }

  private def execute(describeTable: DescribeTableCmd): String = {
    val metaData = session.getMetadata
    val tableName: String = describeTable.table
    val keyspace: String = describeTable.keyspace.getOrElse(getKeySpace(session))

    metaData.getKeyspace(keyspace).asScala.flatMap(ks => ks.getTable(tableName).asScala) match {
      case Some(tableMeta) => HTML_MAGIC + tableDisplay.format(describeTable.statement, tableMeta, withCaption = true)
      case None => throw new InterpreterException(s"Cannot find table $keyspace.$tableName")
    }
  }

  private def execute(describeUDT: DescribeTypeCmd): String = {
    val metaData = session.getMetadata
    val keyspace: String = describeUDT.keyspace.getOrElse(getKeySpace(session))
    val udtName: String = describeUDT.udtName

    metaData.getKeyspace(keyspace).asScala.flatMap(ks => ks.getUserDefinedType(udtName).asScala) match {
      case Some(userType) => HTML_MAGIC + udtDisplay.format(describeUDT.statement, userType, withCaption = true)
      case None => throw new InterpreterException(s"Cannot find type $keyspace.$udtName")
    }
  }

  private def execute(describeUDTs: DescribeTypesCmd): String = {
    val metadata: Metadata = session.getMetadata
    HTML_MAGIC + clusterDisplay.formatAllUDTs(describeUDTs.statement, metadata)
  }

  private def execute(describeFunction: DescribeFunctionCmd): String = {
    val metaData = session.getMetadata
    val keyspaceName: String = describeFunction.keyspace.getOrElse(getKeySpace(session))
    val functionName: String = describeFunction.function

    metaData.getKeyspace(keyspaceName).asScala match {
      case Some(keyspace) =>
        val functionMetas: Seq[FunctionMetadata] = keyspace.getFunctions.asScala.toSeq
          .filter { case (sig, _) => sig.getName.asCql(true).toLowerCase == functionName.toLowerCase}
          .map(_._2)

        if(functionMetas.isEmpty) {
          throw new InterpreterException(s"Cannot find function $keyspaceName.$functionName")
        }
        HTML_MAGIC + functionDisplay.format(describeFunction.statement, functionMetas, withCaption = true)

      case None =>
        throw new InterpreterException(s"Cannot find function $keyspaceName.$functionName")
    }
  }

  private def execute(describeFunctions: DescribeFunctionsCmd): String = {
    val metadata: Metadata = session.getMetadata
    HTML_MAGIC + clusterDisplay.formatAllFunctions(describeFunctions.statement, metadata)
  }

  private def execute(describeAggregate: DescribeAggregateCmd): String = {
    val metaData = session.getMetadata
    val keyspaceName: String = describeAggregate.keyspace.getOrElse(getKeySpace(session))
    val aggregateName: String = describeAggregate.aggregate

    metaData.getKeyspace(keyspaceName).asScala match {
      case Some(keyspace) =>
        val aggMetas: Seq[AggregateMetadata] = keyspace.getAggregates.asScala.toSeq
          .filter { case (sig, _) => sig.getName.asCql(true).toLowerCase == aggregateName.toLowerCase }
          .map(_._2)

        if(aggMetas.isEmpty) {
          throw new InterpreterException(s"Cannot find aggregate $keyspaceName.$aggregateName")
        }
        HTML_MAGIC + aggregateDisplay.format(describeAggregate.statement, aggMetas, withCaption = true,
          session.getContext.getCodecRegistry)

      case None => throw new InterpreterException(s"Cannot find aggregate $keyspaceName.$aggregateName")
    }
  }

  private def execute(describeAggregates: DescribeAggregatesCmd): String = {
    val metadata: Metadata = session.getMetadata
    HTML_MAGIC + clusterDisplay.formatAllAggregates(describeAggregates.statement, metadata)
  }

  private def execute(describeMV: DescribeMaterializedViewCmd): String = {
    val metaData = session.getMetadata
    val keyspaceName: String = describeMV.keyspace.getOrElse(getKeySpace(session))
    val viewName: String = describeMV.view

    metaData.getKeyspace(keyspaceName).asScala match {
      case Some(keyspace) =>
        val viewMeta: Option[ViewMetadata] = keyspace.getView(viewName).asScala
        viewMeta match {
          case Some(vMeta) => HTML_MAGIC + materializedViewDisplay.format(describeMV.statement, vMeta, withCaption = true)
          case None => throw new InterpreterException(s"Cannot find materialized view $keyspaceName.$viewName")
        }

      case None => throw new InterpreterException(s"Cannot find materialized view $keyspaceName.$viewName")
    }
  }

  private def execute(describeMVs: DescribeMaterializedViewsCmd): String = {
    val metadata: Metadata = session.getMetadata
    HTML_MAGIC + clusterDisplay.formatAllMaterializedViews(describeMVs.statement, metadata)
  }

  private def execute(helpCmd: HelpCmd): String = {
    HTML_MAGIC + helpDisplay.formatHelp()
  }

  private def executeStatement[StatementT <: Statement[StatementT]](st: StatementT): Any = {
    val isDDL = EnhancedSession.isDDLStatement(st)
    val newSt = if (isDDL) {
      st.setTimeout(defaultDDLTimeout)
    } else {
      st
    }
    val rs: ResultSet = session.execute(newSt)
    if (isDDL) {
      if (!rs.getExecutionInfo.isSchemaInAgreement) {
        val startTime = System.currentTimeMillis()
        while(!session.checkSchemaAgreement()) {
          LOGGER.info("Schema is still not in agreement, waiting...")
          try {
            Thread.sleep(DEFAULT_CHECK_TIME)
          } catch {
            case _: InterruptedException => None
          }
          val sinceStart = (System.currentTimeMillis() - startTime) / 1000
          if (sinceStart > MAX_SCHEMA_AGREEMENT_WAIT) {
            throw new RuntimeException(s"Can't achieve schema agreement after $sinceStart seconds")
          }
        }
      }
    }
    rs
  }

  def execute[StatementT <: Statement[StatementT]](st: Any): Any = {
    st match {
      case x: DescribeClusterCmd => execute(x)
      case x: DescribeKeyspaceCmd => execute(x)
      case x: DescribeKeyspacesCmd => execute(x)
      case x: DescribeTableCmd => execute(x)
      case x: DescribeTablesCmd => execute(x)
      case x: DescribeTypeCmd => execute(x)
      case x: DescribeTypesCmd => execute(x)
      case x: DescribeFunctionCmd => execute(x)
      case x: DescribeFunctionsCmd => execute(x)
      case x: DescribeAggregateCmd => execute(x)
      case x: DescribeAggregatesCmd => execute(x)
      case x: DescribeMaterializedViewCmd => execute(x)
      case x: DescribeMaterializedViewsCmd => execute(x)
      case x: HelpCmd => execute(x)
      case x: StatementT => executeStatement(x)
      case _ => throw new InterpreterException(s"Cannot execute statement '$st' of type ${st.getClass}")
    }
  }
}

object EnhancedSession {
  private val DDL_REGEX = Pattern.compile("^(CREATE|DROP|ALTER) .*", Pattern.CASE_INSENSITIVE)

  def isDDLStatement(query: String): Boolean = {
    DDL_REGEX.matcher(query.trim).matches()
  }

  def isDDLStatement[StatementT <: Statement[StatementT]](st: StatementT): Boolean = {
    st match {
      case x: BoundStatement =>
        isDDLStatement(x.getPreparedStatement.getQuery)
      case x: BatchStatement =>
        x.iterator().asScala.toSeq.exists {
          case t: BoundStatement => isDDLStatement(t.getPreparedStatement.getQuery)
          case t: SimpleStatement => isDDLStatement(t.getQuery)
        }
      case x: SimpleStatement =>
        isDDLStatement(x.getQuery)
      case _ => // only should be for StatementWrapper
        true
    }
  }

  def getCqlStatement[StatementT <: Statement[StatementT]](st: StatementT): String = {
    st match {
      case x: BoundStatement =>
        x.getPreparedStatement.getQuery
      case x: BatchStatement =>
        val batchType = x.getBatchType
        val timestamp = x.getQueryTimestamp
        val timestampStr = if (timestamp == Long.MinValue) "" else " USING TIMESTAMP " + timestamp
        val batchTypeStr = if (batchType == BatchType.COUNTER) "COUNTER "
        else if (batchType == BatchType.UNLOGGED) "UNLOGGED "
        else ""

        "BEGIN " +  batchTypeStr + "BATCH" + timestampStr + "\n" +
          x.iterator().asScala.toSeq.map {
            case t: BoundStatement => t.getPreparedStatement.getQuery
            case t: SimpleStatement => t.getQuery
          }.mkString("\n") + "\nAPPLY BATCH;"
      case x: SimpleStatement =>
        x.getQuery
      case _ =>
        ""
    }
  }

}