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

import com.datastax.driver.core._
import org.apache.zeppelin.cassandra.TextBlockHierarchy._
import org.apache.zeppelin.interpreter.InterpreterException


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
  val helpDisplay = DisplaySystem.HelpDisplay
  private val noResultDisplay = DisplaySystem.NoResultDisplay


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
    val metadata: KeyspaceMetadata = session.getCluster.getMetadata.getKeyspace(keyspace)
    HTML_MAGIC + keyspaceDisplay.formatKeyspaceContent(describeKeyspace.statement, metadata)
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

  private def execute(describeUDT: DescribeUDTCmd): String = {
    val metaData = session.getCluster.getMetadata
    val keyspace: String = describeUDT.keyspace.orElse(Option(session.getLoggedKeyspace)).getOrElse("system")
    val udtName: String = describeUDT.udtName

    Option(metaData.getKeyspace(keyspace)).flatMap(ks => Option(ks.getUserType(udtName))) match {
      case Some(userType) => HTML_MAGIC + udtDisplay.format(describeUDT.statement, userType, true)
      case None => throw new InterpreterException(s"Cannot find type $keyspace.$udtName")
    }
  }

  private def execute(helpCmd: HelpCmd): String = {
    HTML_MAGIC + helpDisplay.formatHelp()
  }


  def execute(st: Any): Any = {
    st match {
      case x:DescribeClusterCmd => execute(x)
      case x:DescribeKeyspacesCmd => execute(x)
      case x:DescribeTablesCmd => execute(x)
      case x:DescribeKeyspaceCmd => execute(x)
      case x:DescribeTableCmd => execute(x)
      case x:DescribeUDTCmd => execute(x)
      case x:HelpCmd => execute(x)
      case x:Statement => session.execute(x)
      case _ => throw new InterpreterException(s"Cannot execute statement '$st' of type ${st.getClass}")
    }
  }
}
