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
import org.apache.zeppelin.cassandra.TextBlockHierarchy._

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
  val BATCH_PATTERN = """^(?i)\s*BEGIN\s+(UNLOGGED|COUNTER)?\s*BATCH""".r

  val GENERIC_STATEMENT_PREFIX =
    """(?is)\s*(?:INSERT|UPDATE|DELETE|SELECT|CREATE|UPDATE|
      |DROP|GRANT|REVOKE|TRUNCATE|LIST|USE)\s+""".r

  val VALID_IDENTIFIER = "[a-z][a-z0-9_]*"

  val DESCRIBE_CLUSTER_PATTERN = """^(?i)\s*(?:DESCRIBE|DESC)\s+CLUSTER;\s*$""".r
  val DESCRIBE_KEYSPACES_PATTERN = """^(?i)\s*(?:DESCRIBE|DESC)\s+KEYSPACES;\s*$""".r
  val DESCRIBE_TABLES_PATTERN = """^(?i)\s*(?:DESCRIBE|DESC)\s+TABLES;\s*$""".r
  val DESCRIBE_KEYSPACE_PATTERN = ("""^(?i)\s*(?:DESCRIBE|DESC)\s+KEYSPACE\s*("""+VALID_IDENTIFIER+""");\s*$""").r
  val DESCRIBE_TABLE_PATTERN = ("""^(?i)\s*(?:DESCRIBE|DESC)\s+TABLE\s*("""+VALID_IDENTIFIER+""");\s*$""").r
  val DESCRIBE_TABLE_WITH_KEYSPACE_PATTERN = ("""^(?i)\s*(?:DESCRIBE|DESC)\s+TABLE\s*(""" +
                                                VALID_IDENTIFIER +
                                                """)\.(""" +
                                                VALID_IDENTIFIER +
                                                """);\s*$""").r

  val DESCRIBE_TYPE_PATTERN = ("""^(?i)\s*(?:DESCRIBE|DESC)\s+TYPE\s*("""+VALID_IDENTIFIER+""");\s*$""").r
  val DESCRIBE_TYPE_WITH_KEYSPACE_PATTERN = ("""^(?i)\s*(?:DESCRIBE|DESC)\s+TYPE\s*(""" +
                                                VALID_IDENTIFIER +
                                                """)\.(""" +
                                                VALID_IDENTIFIER +
                                                """);\s*$""").r

  val HELP_PATTERN = """^(?i)\s*HELP;\s*$""".r
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


  //Meta data
  private def describeCluster: Parser[DescribeClusterCmd] = """(?i)\s*(?:DESCRIBE|DESC)\s+CLUSTER.*""".r ^^ {extractDescribeClusterCmd(_)}
  private def describeKeyspaces: Parser[DescribeKeyspacesCmd] = """(?i)\s*(?:DESCRIBE|DESC)\s+KEYSPACES.*""".r ^^ {extractDescribeKeyspacesCmd(_)}
  private def describeTables: Parser[DescribeTablesCmd] = """(?i)\s*(?:DESCRIBE|DESC)\s+TABLES.*""".r ^^ {extractDescribeTablesCmd(_)}
  private def describeKeyspace: Parser[DescribeKeyspaceCmd] = """\s*(?i)(?:DESCRIBE|DESC)\s+KEYSPACE\s+.+""".r ^^ {extractDescribeKeyspaceCmd(_)}
  private def describeTable: Parser[DescribeTableCmd] = """(?i)\s*(?:DESCRIBE|DESC)\s+TABLE\s+.+""".r ^^ {extractDescribeTableCmd(_)}
  private def describeType: Parser[DescribeUDTCmd] = """(?i)\s*(?:DESCRIBE|DESC)\s+TYPE\s+.*""".r ^^ {extractDescribeTypeCmd(_)}

  //Help
  private def helpCommand: Parser[HelpCmd] = """(?i)\s*HELP.*""".r ^^{extractHelpCmd(_)}

  private def beginBatch: Parser[String] = """(?i)\s*BEGIN\s+(UNLOGGED|COUNTER)?\s*BATCH""".r
  private def applyBatch: Parser[String] = """(?i)APPLY BATCH;""".r
  private def insert: Parser[SimpleStm] = """(?i)INSERT [^;]+;""".r ^^{SimpleStm(_)}
  private def update: Parser[SimpleStm] = """(?i)UPDATE [^;]+;""".r ^^{SimpleStm(_)}
  private def delete: Parser[SimpleStm] = """(?i)DELETE [^;]+;""".r ^^{SimpleStm(_)}

  private def mutationStatements: Parser[List[QueryStatement]] = rep(insert | update | delete | bind)

  def batch: Parser[BatchStm] = beginBatch ~ mutationStatements ~ applyBatch ^^ {
    case begin ~ cqls ~ end => BatchStm(extractBatchType(begin),cqls)}

  def queries:Parser[List[AnyBlock]] = rep(singleLineComment | multiLineComment | consistency | serialConsistency |
    timestamp | retryPolicy | fetchSize | removePrepare | prepare | bind | batch | describeCluster | describeKeyspaces |
    describeTables | describeKeyspace | describeTable | describeType | helpCommand | genericStatement)

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
      case BATCH_PATTERN(batchType) =>
        val inferredType = Option(batchType).getOrElse("LOGGED")
        BatchStatement.Type.valueOf(inferredType.toUpperCase)
      case _ => throw new InterpreterException(s"Invalid syntax for BEGIN BATCH. " +
        s"""It should comply to the pattern: ${BATCH_PATTERN.toString}""")
    }
  }

  def extractDescribeClusterCmd(text: String): DescribeClusterCmd = {
    text match {
      case DESCRIBE_CLUSTER_PATTERN() => new DescribeClusterCmd
      case _ => throw new InterpreterException(s"Invalid syntax for DESCRIBE CLUSTER. " +
        s"""It should comply to the pattern: ${DESCRIBE_CLUSTER_PATTERN.toString}""")
    }
  }

  def extractDescribeKeyspacesCmd(text: String): DescribeKeyspacesCmd = {
    text match {
        case DESCRIBE_KEYSPACES_PATTERN() => new DescribeKeyspacesCmd
        case _ => throw new InterpreterException(s"Invalid syntax for DESCRIBE KEYSPACES. " +
          s"""It should comply to the pattern: ${DESCRIBE_KEYSPACES_PATTERN.toString}""")
      }
  }

  def extractDescribeTablesCmd(text: String): DescribeTablesCmd = {
    text match {
      case DESCRIBE_TABLES_PATTERN() => new DescribeTablesCmd
      case _ => throw new InterpreterException(s"Invalid syntax for DESCRIBE TABLES. " +
        s"""It should comply to the pattern: ${DESCRIBE_TABLES_PATTERN.toString}""")
    }
  }

  def extractDescribeKeyspaceCmd(text: String): DescribeKeyspaceCmd = {
    text match {
      case DESCRIBE_KEYSPACE_PATTERN(keyspace) => new DescribeKeyspaceCmd(keyspace)
      case _ => throw new InterpreterException(s"Invalid syntax for DESCRIBE KEYSPACE. " +
        s"""It should comply to the pattern: ${DESCRIBE_KEYSPACE_PATTERN.toString}""")
    }
  }

  def extractDescribeTableCmd(text: String): DescribeTableCmd = {
    text match {
      case DESCRIBE_TABLE_WITH_KEYSPACE_PATTERN(keyspace,table) => new DescribeTableCmd(Option(keyspace),table)
      case DESCRIBE_TABLE_PATTERN(table) => new DescribeTableCmd(Option.empty,table)
      case _ => throw new InterpreterException(s"Invalid syntax for DESCRIBE TABLE. " +
       s"""It should comply to the patterns: ${DESCRIBE_TABLE_WITH_KEYSPACE_PATTERN.toString} or ${DESCRIBE_TABLE_PATTERN.toString}""".stripMargin)
    }
  }

  def extractDescribeTypeCmd(text: String): DescribeUDTCmd = {
    text match {
      case DESCRIBE_TYPE_WITH_KEYSPACE_PATTERN(keyspace,table) => new DescribeUDTCmd(Option(keyspace),table)
      case DESCRIBE_TYPE_PATTERN(table) => new DescribeUDTCmd(Option.empty,table)
      case _ => throw new InterpreterException(s"Invalid syntax for DESCRIBE TYPE. " +
        s"""It should comply to the patterns: ${DESCRIBE_TYPE_WITH_KEYSPACE_PATTERN.toString} or ${DESCRIBE_TYPE_PATTERN.toString}""".stripMargin)
    }
  }

  def extractHelpCmd(text: String): HelpCmd = {
    text match {
      case HELP_PATTERN() => new HelpCmd
      case _ => throw new InterpreterException(s"Invalid syntax for HELP. " +
        s"""It should comply to the patterns: ${HELP_PATTERN.toString}""".stripMargin)
    }
  }
}

