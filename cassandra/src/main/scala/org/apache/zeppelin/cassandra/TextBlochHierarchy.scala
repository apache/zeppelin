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

import com.datastax.driver.core.{BatchStatement, ConsistencyLevel}

/**
 * Define a Scala object hierarchy
 * for input text parsing
 */
object TextBlochHierarchy {

  sealed trait BlockType
  object ParameterBlock extends BlockType
  object StatementBlock extends BlockType
  object CommentBlock extends BlockType

  abstract class AnyBlock(val blockType: BlockType) {
    def get[U <: AnyBlock](): U = {
      this.asInstanceOf[U]
    }
  }

  case class Comment(text:String) extends AnyBlock(CommentBlock)

  sealed trait ParameterType
  object CS extends ParameterType
  object SCS extends ParameterType
  object TS extends ParameterType
  object RP extends ParameterType
  object FS extends ParameterType


  abstract class QueryParameters(val paramType: ParameterType) extends AnyBlock(ParameterBlock) {
    def getParam[U <: QueryParameters](): U = {
      this.asInstanceOf[U]
    }
  }

  case class Consistency(value: ConsistencyLevel) extends QueryParameters(CS)
  case class SerialConsistency(value: ConsistencyLevel) extends QueryParameters(SCS)
  case class Timestamp(value: Long) extends QueryParameters(TS)
  case class FetchSize(value: Int) extends QueryParameters(FS)

  sealed trait StatementType
  object PS extends StatementType
  object RPS extends StatementType
  object BS extends StatementType
  object SS extends StatementType
  object BatchS extends StatementType

  abstract class QueryStatement(val statementType: StatementType) extends AnyBlock(StatementBlock) {
    def getStatement[U<: QueryStatement]: U = {
      this.asInstanceOf[U]
    }
  }

  case class SimpleStm(text:String) extends QueryStatement(SS)
  case class PrepareStm(name: String, query:String) extends QueryStatement(PS)
  case class RemovePrepareStm(name:String) extends QueryStatement(RPS)
  case class BoundStm(name: String, values:String) extends QueryStatement(BS)
  case class BatchStm(batchType: BatchStatement.Type, statements: List[QueryStatement]) extends QueryStatement(BatchS)

  abstract class RetryPolicy extends QueryParameters(RP)
  object DefaultRetryPolicy extends RetryPolicy
  object DowngradingRetryPolicy extends RetryPolicy
  object FallThroughRetryPolicy extends RetryPolicy
  object LoggingDefaultRetryPolicy extends RetryPolicy
  object LoggingDowngradingRetryPolicy extends RetryPolicy
  object LoggingFallThroughRetryPolicy extends RetryPolicy
}
