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

import java.io.{ByteArrayOutputStream, PrintStream}
import java.net.InetAddress
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

import com.datastax.driver.core.DataType.Name._
import com.datastax.driver.core._
import com.datastax.driver.core.exceptions.DriverException
import com.datastax.driver.core.policies.{LoggingRetryPolicy, FallthroughRetryPolicy, DowngradingConsistencyRetryPolicy, Policies}
import org.apache.zeppelin.cassandra.TextBlockHierarchy._
import org.apache.zeppelin.display.AngularObjectRegistry
import org.apache.zeppelin.display.Input.ParamOption
import org.apache.zeppelin.interpreter.InterpreterResult.Code
import org.apache.zeppelin.interpreter.{InterpreterException, InterpreterResult, InterpreterContext}
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


/**
 * Value object to store runtime query parameters
  *
  * @param consistency consistency level
 * @param serialConsistency serial consistency level
 * @param timestamp timestamp
 * @param retryPolicy retry policy
 * @param fetchSize query fetch size
 * @param requestTimeOut request time out in millisecs
 */
case class CassandraQueryOptions(consistency: Option[ConsistencyLevel],
                                 serialConsistency:Option[ConsistencyLevel],
                                 timestamp: Option[Long],
                                 retryPolicy: Option[RetryPolicy],
                                 fetchSize: Option[Int],
                                 requestTimeOut: Option[Int])

/**
 * Singleton object to store constants
 */
object InterpreterLogic {
  
  val CHOICES_SEPARATOR : String = """\|"""
  val VARIABLE_PATTERN = """\{\{[^}]+\}\}""".r
  val SIMPLE_VARIABLE_DEFINITION_PATTERN = """\{\{([^=]+)=([^=]+)\}\}""".r
  val MULTIPLE_CHOICES_VARIABLE_DEFINITION_PATTERN = """\{\{([^=]+)=((?:[^=]+\|)+[^|]+)\}\}""".r

  val STANDARD_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
  val ACCURATE_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"

  val defaultRetryPolicy = Policies.defaultRetryPolicy()
  val downgradingConsistencyRetryPolicy = DowngradingConsistencyRetryPolicy.INSTANCE
  val fallThroughRetryPolicy = FallthroughRetryPolicy.INSTANCE
  val loggingDefaultRetryPolicy = new LoggingRetryPolicy(defaultRetryPolicy)
  val loggingDownGradingRetryPolicy = new LoggingRetryPolicy(downgradingConsistencyRetryPolicy)
  val loggingFallThroughRetryPolicy = new LoggingRetryPolicy(fallThroughRetryPolicy)

  val preparedStatements : mutable.Map[String,PreparedStatement] = new ConcurrentHashMap[String,PreparedStatement]().asScala

  val logger = LoggerFactory.getLogger(classOf[InterpreterLogic])

  val paragraphParser = new ParagraphParser
  val boundValuesParser = new BoundValuesParser
  
}

/**
 * Real class to implement the
 * interpreting logic of CQL statements
 * and parameters blocks
 *
 * @param session java driver session
 */
class InterpreterLogic(val session: Session)  {

  val enhancedSession: EnhancedSession = new EnhancedSession(session)

  import InterpreterLogic._

  def interpret(session:Session, stringStatements : String, context: InterpreterContext): InterpreterResult = {

    logger.info(s"Executing CQL statements : \n\n$stringStatements\n")

    try {
      val protocolVersion = session.getCluster.getConfiguration.getProtocolOptions.getProtocolVersion

      val queries:List[AnyBlock] = parseInput(stringStatements)

      val queryOptions = extractQueryOptions(queries
        .filter(_.blockType == ParameterBlock)
        .map(_.get[QueryParameters]))

      logger.info(s"Current Cassandra query options = $queryOptions")

      val queryStatements = queries.filter(_.blockType == StatementBlock).map(_.get[QueryStatement])

      //Remove prepared statements
      queryStatements
        .filter(_.statementType == RemovePrepareStatementType)
        .map(_.getStatement[RemovePrepareStm])
        .foreach(remove => {
          logger.debug(s"Removing prepared statement '${remove.name}'")
          preparedStatements.remove(remove.name)
        })

      //Update prepared statement maps
      queryStatements
        .filter(_.statementType == PrepareStatementType)
        .map(_.getStatement[PrepareStm])
        .foreach(statement => {
          logger.debug(s"Get or prepare statement '${statement.name}' : ${statement.query}")
          preparedStatements.getOrElseUpdate(statement.name,session.prepare(statement.query))
        })

      val statements: List[Any] = queryStatements
        .filter(st => (st.statementType != PrepareStatementType) && (st.statementType != RemovePrepareStatementType))
        .map{
          case x:SimpleStm => generateSimpleStatement(x, queryOptions, context)
          case x:BatchStm => {
            val builtStatements: List[Statement] = x.statements.map {
              case st:SimpleStm => generateSimpleStatement(st, queryOptions, context)
              case st:BoundStm => generateBoundStatement(session, st, queryOptions, context)
              case _ => throw new InterpreterException(s"Unknown statement type")
            }
            generateBatchStatement(x.batchType, queryOptions, builtStatements)
          }
          case x:BoundStm => generateBoundStatement(session, x, queryOptions, context)
          case x:DescribeCommandStatement => x
          case x:HelpCmd => x
          case x => throw new InterpreterException(s"Unknown statement type : ${x}")
       }

      val results: List[(Any,Any)] = for (statement <- statements) yield (enhancedSession.execute(statement),statement)

      if (results.nonEmpty) {
        results.last match {
          case(res: ResultSet, st: Statement) => buildResponseMessage((res, st), protocolVersion)
          case(output: String, _) => new InterpreterResult(Code.SUCCESS, output)
          case _ => throw new InterpreterException(s"Cannot parse result type : ${results.last}")
        }

      } else {
        new InterpreterResult(Code.SUCCESS, enhancedSession.displayNoResult)
      }

    } catch {
      case dex: DriverException => {
        logger.error(dex.getMessage, dex)
        new InterpreterResult(Code.ERROR, parseException(dex))
      }
      case pex:ParsingException => {
        logger.error(pex.getMessage, pex)
        new InterpreterResult(Code.ERROR, pex.getMessage)
      }
      case iex: InterpreterException => {
        logger.error(iex.getMessage, iex)
        new InterpreterResult(Code.ERROR, iex.getMessage)
      }
      case ex: java.lang.Exception => {
        logger.error(ex.getMessage, ex)
        new InterpreterResult(Code.ERROR, parseException(ex))
      }
    }
  }

  def buildResponseMessage(lastResultSet: (ResultSet,Statement), protocolVersion: ProtocolVersion): InterpreterResult = {
    val output = new StringBuilder()
    val rows: collection.mutable.ArrayBuffer[Row] = ArrayBuffer()

    val iterator: util.Iterator[Row] = lastResultSet._1.iterator()
    while (iterator.hasNext) {
      rows.append(iterator.next())
    }

    val columnsDefinitions: List[(String, DataType)] = lastResultSet._1
      .getColumnDefinitions
      .asList
      .toList // Java list -> Scala list
      .map(definition => (definition.getName, definition.getType))


    if (rows.nonEmpty) {
      // Create table headers
      output
        .append("%table ")
        .append(columnsDefinitions.map { case (columnName, _) => columnName }.mkString("\t")).append("\n")

      // Deserialize Data
      rows.foreach {
        row => {
          val data = columnsDefinitions.map {
            case (name, dataType) => {
              if (row.isNull(name)) null else row.getObject(name)
            }
          }
          output.append(data.mkString("\t")).append("\n")
        }
      }
    } else {
      val lastQuery: String = lastResultSet._2.toString
      val executionInfo: ExecutionInfo = lastResultSet._1.getExecutionInfo
      output.append(enhancedSession.displayExecutionStatistics(lastQuery, executionInfo))
    }

    val result: String = output.toString()
    logger.debug(s"CQL result : \n\n$result\n")
    new InterpreterResult(Code.SUCCESS, result)
  }

  def parseInput(input:String): List[AnyBlock] = {
    val parsingResult: ParagraphParser#ParseResult[List[AnyBlock]] = paragraphParser.parseAll(paragraphParser.queries, input)
    parsingResult match {
      case paragraphParser.Success(blocks,_) => blocks
      case paragraphParser.Failure(msg,next) => {
        throw new InterpreterException(s"Error parsing input:\n\t'$input'\nDid you forget to add ; (semi-colon) at the end of each CQL statement ?")
      }
      case paragraphParser.Error(msg,next) => {
        throw new InterpreterException(s"Error parsing input:\n\t'$input'\nDid you forget to add ; (semi-colon) at the end of each CQL statement ?")
      }
      case _ => throw new InterpreterException(s"Error parsing input: $input")
    }
  }

  def extractQueryOptions(parameters: List[QueryParameters]): CassandraQueryOptions = {

    logger.debug(s"Extracting query options from $parameters")

    val consistency: Option[ConsistencyLevel] = parameters
      .filter(_.paramType == ConsistencyParam)
      .map(_.getParam[Consistency])
      .flatMap(x => Option(x.value))
      .headOption


    val serialConsistency: Option[ConsistencyLevel] = parameters
      .filter(_.paramType == SerialConsistencyParam)
      .map(_.getParam[SerialConsistency])
      .flatMap(x => Option(x.value))
      .headOption

    val timestamp: Option[Long] = parameters
      .filter(_.paramType == TimestampParam)
      .map(_.getParam[Timestamp])
      .flatMap(x => Option(x.value))
      .headOption

    val retryPolicy: Option[RetryPolicy] = parameters
      .filter(_.paramType == RetryPolicyParam)
      .map(_.getParam[RetryPolicy])
      .headOption

    val fetchSize: Option[Int] = parameters
      .filter(_.paramType == FetchSizeParam)
      .map(_.getParam[FetchSize])
      .flatMap(x => Option(x.value))
      .headOption

    val requestTimeOut: Option[Int] = parameters
      .filter(_.paramType == RequestTimeOutParam)
      .map(_.getParam[RequestTimeOut])
      .flatMap(x => Option(x.value))
      .headOption

    CassandraQueryOptions(consistency,serialConsistency, timestamp, retryPolicy, fetchSize, requestTimeOut)
  }

  def generateSimpleStatement(st: SimpleStm, options: CassandraQueryOptions,context: InterpreterContext): SimpleStatement = {
    logger.debug(s"Generating simple statement : '${st.text}'")
    val statement = new SimpleStatement(maybeExtractVariables(st.text, context))
    applyQueryOptions(options, statement)
    statement
  }

  def generateBoundStatement(session: Session, st: BoundStm, options: CassandraQueryOptions,context: InterpreterContext): BoundStatement = {
    logger.debug(s"Generating bound statement with name : '${st.name}' and bound values : ${st.values}")
    preparedStatements.get(st.name) match {
      case Some(ps) => {
        val boundValues = maybeExtractVariables(st.values, context)
        createBoundStatement(session.getCluster.getConfiguration.getCodecRegistry, st.name, ps, boundValues)
      }
      case None => throw new InterpreterException(s"The statement '${st.name}' can not be bound to values. " +
          s"Are you sure you did prepare it with @prepare[${st.name}] ?")
    }
  }

  def generateBatchStatement(batchType: BatchStatement.Type, options: CassandraQueryOptions, statements: List[Statement]): BatchStatement = {
    logger.debug(s"""Generating batch statement of type '${batchType} for ${statements.mkString(",")}'""")
    val batch = new BatchStatement(batchType)
    statements.foreach(batch.add(_))
    applyQueryOptions(options, batch)
    batch
  }

  def maybeExtractVariables(statement: String, context: InterpreterContext): String = {

    def findInAngularRepository(variable: String): Option[AnyRef] = {
      val registry = context.getAngularObjectRegistry
      val noteId = context.getNoteId
      val paragraphId = context.getParagraphId
      val paragraphScoped: Option[AnyRef] = Option(registry.get(variable, noteId, paragraphId)).map[AnyRef](_.get())

      paragraphScoped
    }

    def extractVariableAndDefaultValue(statement: String, exp: String):String = {
      exp match {
        case MULTIPLE_CHOICES_VARIABLE_DEFINITION_PATTERN(variable, choices) => {
          val escapedExp: String = exp.replaceAll( """\{""", """\\{""").replaceAll( """\}""", """\\}""").replaceAll("""\|""","""\\|""")
          findInAngularRepository(variable) match {
            case Some(value) => statement.replaceAll(escapedExp,value.toString)
            case None => {
              val listChoices:List[String] = choices.trim.split(CHOICES_SEPARATOR).toList
              val paramOptions= listChoices.map(choice => new ParamOption(choice, choice))
              val selected = context.getGui.select(variable, listChoices.head, paramOptions.toArray)
              statement.replaceAll(escapedExp,selected.toString)
            }
          }
        }
        case SIMPLE_VARIABLE_DEFINITION_PATTERN(variable,defaultVal) => {
          val escapedExp: String = exp.replaceAll( """\{""", """\\{""").replaceAll( """\}""", """\\}""")
          findInAngularRepository(variable) match {
            case Some(value) => statement.replaceAll(escapedExp,value.toString)
            case None => {
              val value = context.getGui.input(variable,defaultVal)
              statement.replaceAll(escapedExp,value.toString)
            }
          }
        }
        case _ => throw new ParsingException(s"Invalid bound variable definition for '$exp' in '$statement'. It should be of form 'variable=defaultValue' or 'variable=value1|value2|...|valueN'")
      }
    }

    VARIABLE_PATTERN.findAllIn(statement).foldLeft(statement)(extractVariableAndDefaultValue _)
  }

  def applyQueryOptions(options: CassandraQueryOptions, statement: Statement): Unit = {
    options.consistency.foreach(statement.setConsistencyLevel(_))
    options.serialConsistency.foreach(statement.setSerialConsistencyLevel(_))
    options.timestamp.foreach(statement.setDefaultTimestamp(_))
    options.retryPolicy.foreach {
      case DefaultRetryPolicy => statement.setRetryPolicy(defaultRetryPolicy)
      case DowngradingRetryPolicy => statement.setRetryPolicy(downgradingConsistencyRetryPolicy)
      case FallThroughRetryPolicy => statement.setRetryPolicy(fallThroughRetryPolicy)
      case LoggingDefaultRetryPolicy => statement.setRetryPolicy(loggingDefaultRetryPolicy)
      case LoggingDowngradingRetryPolicy => statement.setRetryPolicy(loggingDownGradingRetryPolicy)
      case LoggingFallThroughRetryPolicy => statement.setRetryPolicy(loggingFallThroughRetryPolicy)
      case _ => throw new InterpreterException(s"""Unknown retry policy ${options.retryPolicy.getOrElse("???")}""")
    }
    options.fetchSize.foreach(statement.setFetchSize(_))
    options.requestTimeOut.foreach(statement.setReadTimeoutMillis(_))
  }

  private def createBoundStatement(codecRegistry: CodecRegistry, name: String, ps: PreparedStatement, rawBoundValues: String): BoundStatement = {
    val dataTypes = ps.getVariables.toList
      .map(cfDef => cfDef.getType)

    val boundValuesAsText = parseBoundValues(name,rawBoundValues)

    if(dataTypes.size != boundValuesAsText.size) throw new InterpreterException(s"Invalid @bind values for prepared statement '$name'. " +
      s"Prepared parameters has ${dataTypes.size} variables whereas bound values have ${boundValuesAsText.size} parameters ...")

    val convertedValues: List[AnyRef] = boundValuesAsText
      .zip(dataTypes).map {
        case (value, dataType) => {
          if(value.trim == "null") {
            null
          } else {
            val codec: TypeCodec[AnyRef] = codecRegistry.codecFor[AnyRef](dataType)
            dataType.getName match {
            case (ASCII | TEXT | VARCHAR) => value.trim.replaceAll("(?<!')'","")
            case (INT | VARINT) => value.trim.toInt
            case (BIGINT | COUNTER) => value.trim.toLong
            case BLOB => ByteBuffer.wrap(value.trim.getBytes)
            case BOOLEAN => value.trim.toBoolean
            case DECIMAL => BigDecimal(value.trim)
            case DOUBLE => value.trim.toDouble
            case FLOAT => value.trim.toFloat
            case INET => InetAddress.getByName(value.trim)
            case TIMESTAMP => parseDate(value.trim)
            case (UUID | TIMEUUID) => java.util.UUID.fromString(value.trim)
            case LIST => codec.parse(boundValuesParser.parse(boundValuesParser.list, value).get)
            case SET => codec.parse(boundValuesParser.parse(boundValuesParser.set, value).get)
            case MAP => codec.parse(boundValuesParser.parse(boundValuesParser.map, value).get)
            case UDT => codec.parse(boundValuesParser.parse(boundValuesParser.udt, value).get)
            case TUPLE => codec.parse(boundValuesParser.parse(boundValuesParser.tuple, value).get)
            case _ => throw new InterpreterException(s"Cannot parse data of type : ${dataType.toString}")
          }
        }
      }
    }.asInstanceOf[List[AnyRef]]

    ps.bind(convertedValues.toArray: _*)
  }

  protected def parseBoundValues(psName: String, boundValues: String): List[String] = {
    val result: BoundValuesParser#ParseResult[List[String]] = boundValuesParser.parseAll(boundValuesParser.values, boundValues)

    result match {
      case boundValuesParser.Success(list,_) => list
      case _ => throw new InterpreterException(s"Cannot parse bound values for prepared statement '$psName' : $boundValues. Did you forget to wrap text with ' (simple quote) ?")
    }
  }

  def parseDate(dateString: String): Date = {
    dateString match {
      case boundValuesParser.STANDARD_DATE_PATTERN(datePattern) => new SimpleDateFormat(STANDARD_DATE_FORMAT).parse(datePattern)
      case boundValuesParser.ACCURATE_DATE_PATTERN(datePattern) => new SimpleDateFormat(ACCURATE_DATE_FORMAT).parse(datePattern)
      case _ => throw new InterpreterException(s"Cannot parse date '$dateString'. " +
        s"Accepted formats : $STANDARD_DATE_FORMAT OR $ACCURATE_DATE_FORMAT");
    }
  }

  def parseException(ex: Exception): String = {
    val os = new ByteArrayOutputStream()
    val ps = new PrintStream(os)
    ex.printStackTrace(ps)
    os.toString("UTF-8")
  }

}
