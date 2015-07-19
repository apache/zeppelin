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
import org.apache.zeppelin.cassandra.CassandraInterpreter._
import org.apache.zeppelin.interpreter.InterpreterException
import scala.util.parsing.combinator._
import org.apache.zeppelin.cassandra.TextBlochHierarchy._

object ParagraphParser {
  val CONSISTENCY_LEVEL_PATTERN = ConsistencyLevel.values().toList
    .map(_.name()).filter(!_.contains("SERIAL")).mkString("""^\s*@consistency\s*=\s*(""", "|" , """)\s*$""").r

  val SERIAL_CONSISTENCY_LEVEL_PATTERN = ConsistencyLevel.values().toList
    .map(_.name()).filter(_.contains("SERIAL")).mkString("""^\s*@serialConsistency\s*=\s*(""", "|", """)\s*$""").r
  val TIMESTAMP_PATTERN = """^\s*@timestamp\s*=\s*([0-9]+)\s*$""".r

  val RETRY_POLICIES_PATTERN = List(DEFAULT_POLICY,DOWNGRADING_CONSISTENCY_RETRY, FALLTHROUGH_RETRY,
    LOGGING_DEFAULT_RETRY, LOGGING_DOWNGRADING_RETRY, LOGGING_FALLTHROUGH_RETRY)
    .mkString("""^\s*@retryPolicy\s*=\s*(""", "|" , """)\s*$""").r
  val FETCHSIZE_PATTERN = """^\s*@fetchSize\s*=\s*([0-9]+)\s*$""".r

  val SIMPLE_STATEMENT_PATTERN = """([^;]+;)""".r
  val PREPARE_STATEMENT_PATTERN = """^\s*@prepare\[([^]]+)\]\s*=\s*([^;]+)$""".r
  val REMOVE_PREPARE_STATEMENT_PATTERN = """^\s*@remove_prepare\[([^]]+)\]\s*$""".r

  val BIND_PATTERN = """^\s*@bind\[([^]]+)\](?:=([^;]+))?""".r
  val BATCH_PATTERN = """^\s*BEGIN\s+(UNLOGGED|COUNTER)?\s*BATCH""".r

  val GENERIC_STATEMENT_PREFIX =
    """\s*(?is)(?:INSERT|UPDATE|DELETE|SELECT|CREATE|UPDATE|
      |DROP|GRANT|REVOKE|TRUNCATE|LIST|USE)\s+""".r
}

class ParagraphParser extends RegexParsers{


  import ParagraphParser._

  def singleLineComment: Parser[Comment] = """\s*#.*""".r ^^ {case text => Comment(text.trim.replaceAll("#",""))}
  def multiLineComment: Parser[Comment] = """(?s)/\*(.*)\*/""".r ^^ {case text => Comment(text.trim.replaceAll("""/\*""","").replaceAll("""\*/""",""))}

  //Query parameters
  def consistency: Parser[Consistency] = """\s*@consistency.+""".r ^^ {case x => extractConsistency(x.trim)}
  def serialConsistency: Parser[SerialConsistency] = """\s*@serialConsistency.+""".r ^^ {case x => extractSerialConsistency(x.trim)}
  def timestamp: Parser[Timestamp] = """\s*@timestamp.+""".r ^^ {case x => extractTimestamp(x.trim)}
  def retryPolicy: Parser[RetryPolicy] = """\s*@retryPolicy.+""".r ^^ {case x => extractRetryPolicy(x.trim)}
  def fetchSize: Parser[FetchSize] = """\s*@fetchSize.+""".r ^^ {case x => extractFetchSize(x.trim)}

  //Statements
  def genericStatement: Parser[SimpleStm] = s"""$GENERIC_STATEMENT_PREFIX[^;]+;""".r ^^ {case x => extractSimpleStatement(x.trim)}
  def prepare: Parser[PrepareStm] = """\s*@prepare.+""".r ^^ {case x => extractPreparedStatement(x.trim)}
  def removePrepare: Parser[RemovePrepareStm] = """\s*@remove_prepare.+""".r ^^ {case x => extractRemovePreparedStatement(x.trim)}
  def bind: Parser[BoundStm] = """\s*@bind.+""".r ^^ {case x => extractBoundStatement(x.trim)}


  private def beginBatch: Parser[String] = """\s*BEGIN\s+(UNLOGGED|COUNTER)?\s*BATCH""".r
  private def applyBatch: Parser[String] = "APPLY BATCH;"
  private def insert: Parser[SimpleStm] = """INSERT [^;]+;""".r ^^{SimpleStm(_)}
  private def update: Parser[SimpleStm] = """UPDATE [^;]+;""".r ^^{SimpleStm(_)}
  private def delete: Parser[SimpleStm] = """DELETE [^;]+;""".r ^^{SimpleStm(_)}

  private def mutationStatements: Parser[List[QueryStatement]] = rep(insert | update | delete | bind)

  def batch: Parser[BatchStm] = beginBatch ~ mutationStatements ~ applyBatch ^^ {
    case begin ~ cqls ~ end => BatchStm(extractBatchType(begin),cqls)}

  def queries:Parser[List[AnyBlock]] = rep(singleLineComment | multiLineComment | consistency | serialConsistency |
    timestamp | retryPolicy | fetchSize | removePrepare | prepare | bind | batch | genericStatement)

  def extractConsistency(text: String): Consistency = {
    text match {
      case CONSISTENCY_LEVEL_PATTERN(consistency) => Consistency(ConsistencyLevel.valueOf(consistency))
      case _ => throw new InterpreterException(s"Invalid syntax for @consistency. " +
        s"It should comply to the pattern ${CONSISTENCY_LEVEL_PATTERN.toString}")
    }
  }

  def extractSerialConsistency(text: String): SerialConsistency = {
    text match {
      case SERIAL_CONSISTENCY_LEVEL_PATTERN(consistency) => SerialConsistency(ConsistencyLevel.valueOf(consistency))
      case _ => throw new InterpreterException(s"Invalid syntax for @serialConsistency. " +
        s"It should comply to the pattern ${SERIAL_CONSISTENCY_LEVEL_PATTERN.toString}")
    }
  }

  def extractTimestamp(text: String): Timestamp = {
    text match {
      case TIMESTAMP_PATTERN(timestamp) => Timestamp(timestamp.trim.toLong)
      case _ => throw new InterpreterException(s"Invalid syntax for @timestamp. " +
        s"It should comply to the pattern ${TIMESTAMP_PATTERN.toString}")
    }
  }

  def extractRetryPolicy(text: String): RetryPolicy = {
    text match {
      case RETRY_POLICIES_PATTERN(retry) => retry.trim match {
        case DEFAULT_POLICY => DefaultRetryPolicy
        case DOWNGRADING_CONSISTENCY_RETRY => DowngradingRetryPolicy
        case FALLTHROUGH_RETRY => FallThroughRetryPolicy
        case LOGGING_DEFAULT_RETRY => LoggingDefaultRetryPolicy
        case LOGGING_DOWNGRADING_RETRY => LoggingDowngradingRetryPolicy
        case LOGGING_FALLTHROUGH_RETRY => LoggingFallThroughRetryPolicy
      }
      case _ => throw new InterpreterException(s"Invalid syntax for @retryPolicy. " +
        s"It should comply to the pattern ${RETRY_POLICIES_PATTERN.toString}")
    }
  }

  def extractFetchSize(text: String): FetchSize = {
    text match {
      case FETCHSIZE_PATTERN(fetchSize) => FetchSize(fetchSize.trim.toInt)
      case _ => throw new InterpreterException(s"Invalid syntax for @fetchSize. " +
        s"It should comply to the pattern ${FETCHSIZE_PATTERN.toString}")
    }
  }

  def extractSimpleStatement(text: String): SimpleStm = {
    text match {
      case SIMPLE_STATEMENT_PATTERN(statement) => SimpleStm(statement)
      case _ => throw new InterpreterException(s"Invalid statement '$text'. Did you forget to add ; (semi-colon) at the end of each CQL statement ?")
    }
  }

  def extractPreparedStatement(text: String): PrepareStm = {
    text match {
      case PREPARE_STATEMENT_PATTERN(name,queryString) => PrepareStm(name.trim,queryString.trim)
      case _ => throw new InterpreterException(s"Invalid syntax for @prepare. " +
        s"It should comply to the pattern: @prepare[prepared_statement_name]=CQL Statement (without semi-colon)")
    }
  }

  def extractRemovePreparedStatement(text: String): RemovePrepareStm= {
    text match {
      case REMOVE_PREPARE_STATEMENT_PATTERN(name) => RemovePrepareStm(name.trim)
      case _ => throw new InterpreterException(s"Invalid syntax for @remove_prepare. " +
        s"It should comply to the pattern: @remove_prepare[prepared_statement_name]")
    }
  }

  def extractBoundStatement(text: String): BoundStm = {
    text match {
      case BIND_PATTERN(name,boundValues) => BoundStm(name.trim, Option(boundValues).map(_.trim).getOrElse(""))
      case _ => throw new InterpreterException("Invalid syntax for @bind. It should comply to the pattern: " +
        "@bind[prepared_statement_name]=10,'jdoe','John DOE',12345,'2015-07-32 12:04:23.234' " +
        "OR @bind[prepared_statement_name] with no bound value. No semi-colon")
    }
  }

  def extractBatchType(text: String): BatchStatement.Type = {
    text match {
      case BATCH_PATTERN(batchType) => {
        val inferredType = Option(batchType).getOrElse("LOGGED")
        BatchStatement.Type.valueOf(inferredType)
      }
      case _ => throw new InterpreterException(s"Invalid syntax for BEGIN BATCH. " +
        s"""It should comply to the pattern: ${BATCH_PATTERN.toString}""")
    }
  }
}

