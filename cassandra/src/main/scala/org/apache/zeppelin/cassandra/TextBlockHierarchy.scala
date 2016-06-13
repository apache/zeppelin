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

/**
 * Define a Scala object hierarchy
 * for input text parsing
 */
object TextBlockHierarchy {

  sealed trait BlockType
  object ParameterBlock extends BlockType
  object StatementBlock extends BlockType
  object DescribeBlock extends BlockType
  object CommentBlock extends BlockType

  abstract class AnyBlock(val blockType: BlockType) {
    def get[U <: AnyBlock]: U = {
      this.asInstanceOf[U]
    }
  }

  case class Comment(text:String) extends AnyBlock(CommentBlock)

  sealed trait ParameterType
  object ConsistencyParam extends ParameterType
  object SerialConsistencyParam extends ParameterType
  object TimestampParam extends ParameterType
  object RetryPolicyParam extends ParameterType
  object FetchSizeParam extends ParameterType
  object RequestTimeOutParam extends ParameterType


  abstract class QueryParameters(val paramType: ParameterType) extends AnyBlock(ParameterBlock) {
    def getParam[U <: QueryParameters]: U = {
      this.asInstanceOf[U]
    }
  }

  case class Consistency(value: ConsistencyLevel) extends QueryParameters(ConsistencyParam)
  
  case class SerialConsistency(value: ConsistencyLevel) extends QueryParameters(SerialConsistencyParam)
  
  case class Timestamp(value: Long) extends QueryParameters(TimestampParam)
  
  case class FetchSize(value: Int) extends QueryParameters(FetchSizeParam)

  case class RequestTimeOut(value: Int) extends QueryParameters(RequestTimeOutParam)

  abstract class RetryPolicy extends QueryParameters(RetryPolicyParam)

  object DefaultRetryPolicy extends RetryPolicy
  object DowngradingRetryPolicy extends RetryPolicy
  object FallThroughRetryPolicy extends RetryPolicy
  object LoggingDefaultRetryPolicy extends RetryPolicy
  object LoggingDowngradingRetryPolicy extends RetryPolicy
  object LoggingFallThroughRetryPolicy extends RetryPolicy
  
  sealed trait StatementType
  object PrepareStatementType extends StatementType
  object RemovePrepareStatementType extends StatementType
  object BoundStatementType extends StatementType
  object SimpleStatementType extends StatementType
  object BatchStatementType extends StatementType
  object DescribeClusterStatementType extends StatementType
  object DescribeAllKeyspacesStatementType extends StatementType
  object DescribeAllTablesStatementType extends StatementType
  object DescribeAllTypesStatementType extends StatementType
  object DescribeAllFunctionsStatementType extends StatementType
  object DescribeAllAggregatesStatementType extends StatementType
  object DescribeKeyspaceStatementType extends StatementType
  object DescribeTableStatementType extends StatementType
  object DescribeTypeStatementType extends StatementType
  object DescribeFunctionStatementType extends StatementType
  object DescribeAggregateStatementType extends StatementType
  object DescribeMaterializedView extends StatementType
  object HelpStatementType extends StatementType

  abstract class QueryStatement(val statementType: StatementType) extends AnyBlock(StatementBlock) {
    def getStatement[U<: QueryStatement]: U = {
      this.asInstanceOf[U]
    }
  }

  case class SimpleStm(text:String) extends QueryStatement(SimpleStatementType)
  
  case class PrepareStm(name: String, query:String) extends QueryStatement(PrepareStatementType)
  
  case class RemovePrepareStm(name:String) extends QueryStatement(RemovePrepareStatementType)
  
  case class BoundStm(name: String, values:String) extends QueryStatement(BoundStatementType)
  
  case class BatchStm(batchType: BatchStatement.Type, statements: List[QueryStatement])
    extends QueryStatement(BatchStatementType)

  sealed trait DescribeCommandStatement {
    val statement: String
  }

  case class DescribeClusterCmd(override val statement: String = "DESCRIBE CLUSTER;")
    extends QueryStatement(DescribeClusterStatementType) with DescribeCommandStatement

  case class DescribeKeyspacesCmd(override val statement: String = "DESCRIBE KEYSPACES;")
    extends QueryStatement(DescribeAllKeyspacesStatementType) with DescribeCommandStatement

  case class DescribeTablesCmd(override val statement: String = "DESCRIBE TABLES;")
    extends QueryStatement(DescribeAllTablesStatementType) with DescribeCommandStatement

  case class DescribeTypesCmd(override val statement: String = "DESCRIBE TYPES;")
    extends QueryStatement(DescribeAllTypesStatementType) with DescribeCommandStatement

  case class DescribeFunctionsCmd(override val statement: String = "DESCRIBE FUNCTIONS;") extends QueryStatement(DescribeAllFunctionsStatementType)
    with DescribeCommandStatement

  case class DescribeAggregatesCmd(override val statement: String = "DESCRIBE AGGREGATES;") extends QueryStatement(DescribeAllAggregatesStatementType)
    with DescribeCommandStatement

  case class DescribeMaterializedViewsCmd(override val statement: String = "DESCRIBE MATERIALIZED VIEWS;") extends QueryStatement(DescribeAllAggregatesStatementType)
    with DescribeCommandStatement

  case class DescribeKeyspaceCmd(keyspace: String) extends QueryStatement(DescribeKeyspaceStatementType)
    with DescribeCommandStatement {
    override val statement: String = s"DESCRIBE KEYSPACE $keyspace;"
  }

  case class DescribeTableCmd(keyspace:Option[String],table: String) extends QueryStatement(DescribeTableStatementType)
    with DescribeCommandStatement {
    override val statement: String = keyspace match {
      case Some(ks) => s"DESCRIBE TABLE $ks.$table;"
      case None => s"DESCRIBE TABLE $table;"
    }
  }

  case class DescribeTypeCmd(keyspace:Option[String], udtName: String) extends QueryStatement(DescribeTypeStatementType)
    with DescribeCommandStatement {
    override val statement: String = keyspace match {
      case Some(ks) => s"DESCRIBE TYPE $ks.$udtName;"
      case None => s"DESCRIBE TYPE $udtName;"
    }
  }

  case class DescribeFunctionCmd(keyspace:Option[String], function: String) extends QueryStatement(DescribeFunctionStatementType)
    with DescribeCommandStatement {
    override val statement: String = keyspace match {
      case Some(ks) => s"DESCRIBE FUNCTION $ks.$function;"
      case None => s"DESCRIBE FUNCTION $function;"
    }
  }

  case class DescribeAggregateCmd(keyspace:Option[String], aggregate: String) extends QueryStatement(DescribeAggregateStatementType)
    with DescribeCommandStatement {
    override val statement: String = keyspace match {
      case Some(ks) => s"DESCRIBE AGGREGATE $ks.$aggregate;"
      case None => s"DESCRIBE AGGREGATE $aggregate;"
    }
  }

  case class DescribeMaterializedViewCmd(keyspace:Option[String], view: String) extends QueryStatement(DescribeMaterializedView)
  with DescribeCommandStatement {
    override val statement: String = keyspace match {
      case Some(ks) => s"DESCRIBE MATERIALIZED VIEW $ks.$view;"
      case None => s"DESCRIBE MATERIALIZED VIEW $view;"
    }
  }

  case class HelpCmd(val statement:String = "HELP;") extends QueryStatement(HelpStatementType)

}
