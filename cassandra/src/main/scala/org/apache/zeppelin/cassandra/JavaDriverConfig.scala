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

import java.lang.Boolean._

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.config.{DefaultDriverOption, ProgrammaticDriverConfigLoaderBuilder}
import org.apache.commons.lang3.StringUtils._
import org.apache.zeppelin.interpreter.Interpreter
import org.apache.zeppelin.cassandra.CassandraInterpreter._
import org.slf4j.{Logger, LoggerFactory}

/**
 * Utility class to extract and configure the Java driver
 */
class JavaDriverConfig {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[JavaDriverConfig])

  def setSocketOptions(intpr: Interpreter, configBuilder: ProgrammaticDriverConfigLoaderBuilder): Unit = {
    val connectTimeoutMillis: Int = intpr.getProperty(CASSANDRA_SOCKET_CONNECTION_TIMEOUT_MILLIS,
      CassandraInterpreter.DEFAULT_CONNECTION_TIMEOUT).toInt
    configBuilder.withInt(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, connectTimeoutMillis)
    configBuilder.withInt(DefaultDriverOption.CONTROL_CONNECTION_TIMEOUT, connectTimeoutMillis)

    val readTimeoutMillis: Int = intpr.getProperty(CASSANDRA_SOCKET_READ_TIMEOUT_MILLIS,
      CassandraInterpreter.DEFAULT_READ_TIMEOUT).toInt
    configBuilder.withInt(DefaultDriverOption.REQUEST_TIMEOUT, readTimeoutMillis)

    val tcpNoDelay = intpr.getProperty(CASSANDRA_SOCKET_TCP_NO_DELAY, CassandraInterpreter.DEFAULT_TCP_NO_DELAY)
    if (isNotBlank(tcpNoDelay)) {
      configBuilder.withBoolean(DefaultDriverOption.SOCKET_TCP_NODELAY, parseBoolean(tcpNoDelay))
    }

    val keepAlive: String = intpr.getProperty(CASSANDRA_SOCKET_KEEP_ALIVE)
    if (isNotBlank(keepAlive)) {
      configBuilder.withBoolean(DefaultDriverOption.SOCKET_KEEP_ALIVE, parseBoolean(keepAlive))
    }

    val receivedBuffSize: String = intpr.getProperty(CASSANDRA_SOCKET_RECEIVED_BUFFER_SIZE_BYTES)
    if (isNotBlank(receivedBuffSize)) {
      configBuilder.withInt(DefaultDriverOption.SOCKET_RECEIVE_BUFFER_SIZE, receivedBuffSize.toInt)
    }

    val sendBuffSize: String = intpr.getProperty(CASSANDRA_SOCKET_SEND_BUFFER_SIZE_BYTES)
    if (isNotBlank(sendBuffSize)) {
      configBuilder.withInt(DefaultDriverOption.SOCKET_SEND_BUFFER_SIZE, sendBuffSize.toInt)
    }

    val reuseAddress: String = intpr.getProperty(CASSANDRA_SOCKET_REUSE_ADDRESS)
    if (isNotBlank(reuseAddress)) {
      configBuilder.withBoolean(DefaultDriverOption.SOCKET_REUSE_ADDRESS, parseBoolean(reuseAddress))
    }

    val soLinger: String = intpr.getProperty(CASSANDRA_SOCKET_SO_LINGER)
    if (isNotBlank(soLinger)) {
      configBuilder.withInt(DefaultDriverOption.SOCKET_LINGER_INTERVAL, soLinger.toInt)
    }
  }

  def setQueryOptions(intpr: Interpreter, configBuilder: ProgrammaticDriverConfigLoaderBuilder): Unit = {
    val consistencyLevel = intpr.getProperty(CASSANDRA_QUERY_DEFAULT_CONSISTENCY,
      CassandraInterpreter.DEFAULT_CONSISTENCY)
    configBuilder.withString(DefaultDriverOption.REQUEST_CONSISTENCY, consistencyLevel)

    val serialConsistencyLevel = intpr.getProperty(CASSANDRA_QUERY_DEFAULT_SERIAL_CONSISTENCY,
      CassandraInterpreter.DEFAULT_SERIAL_CONSISTENCY)
    configBuilder.withString(DefaultDriverOption.REQUEST_SERIAL_CONSISTENCY, serialConsistencyLevel)

    val fetchSize = intpr.getProperty(CASSANDRA_QUERY_DEFAULT_FETCH_SIZE,
      CassandraInterpreter.DEFAULT_FETCH_SIZE).toInt
    configBuilder.withInt(DefaultDriverOption.REQUEST_PAGE_SIZE, fetchSize)

    configBuilder.withBoolean(DefaultDriverOption.REQUEST_DEFAULT_IDEMPOTENCE,
      parseBoolean(intpr.getProperty(CASSANDRA_QUERY_DEFAULT_IDEMPOTENCE)))
  }

  val PROTOCOL_MAPPING: Map[String, ProtocolVersion] = Map("3" -> ProtocolVersion.V3, "4" -> ProtocolVersion.V4,
    "5" -> ProtocolVersion.V5, "DSE1" -> ProtocolVersion.DSE_V1, "DSE2" -> ProtocolVersion.DSE_V2)

  def setProtocolVersion(intpr: Interpreter, configBuilder: ProgrammaticDriverConfigLoaderBuilder): Unit = {
    val protocolVersion: String = intpr.getProperty(CASSANDRA_PROTOCOL_VERSION,
      CassandraInterpreter.DEFAULT_PROTOCOL_VERSION)
    LOGGER.debug("Protocol version : " + protocolVersion)

    protocolVersion match {
      case "1" | "2" =>
        throw new RuntimeException(s"Protocol V${protocolVersion} isn't supported")
      case _ =>
        configBuilder.withString(DefaultDriverOption.PROTOCOL_VERSION,
          PROTOCOL_MAPPING.getOrElse(protocolVersion, ProtocolVersion.DEFAULT).name())
    }
  }

  def setPoolingOptions(intpr: Interpreter, configBuilder: ProgrammaticDriverConfigLoaderBuilder): Unit = {
    val poolingOptionsInfo: StringBuilder = new StringBuilder("Pooling options : \n\n")

    val coreConnPerHostLocal: Int = intpr.getProperty(CASSANDRA_POOLING_CONNECTION_PER_HOST_LOCAL,
      DEFAULT_CONNECTIONS_PER_HOST).toInt
    configBuilder.withInt(DefaultDriverOption.CONNECTION_POOL_LOCAL_SIZE, coreConnPerHostLocal)
    poolingOptionsInfo
      .append("\t")
      .append(CASSANDRA_POOLING_CONNECTION_PER_HOST_LOCAL)
      .append(" : ")
      .append(coreConnPerHostLocal)
      .append("\n")

    val coreConnPerHostRemote: Int = intpr.getProperty(CASSANDRA_POOLING_CONNECTION_PER_HOST_REMOTE,
      DEFAULT_CONNECTIONS_PER_HOST).toInt
    configBuilder.withInt(DefaultDriverOption.CONNECTION_POOL_REMOTE_SIZE, coreConnPerHostRemote)
    poolingOptionsInfo
      .append("\t")
      .append(CASSANDRA_POOLING_CONNECTION_PER_HOST_REMOTE)
      .append(" : ")
      .append(coreConnPerHostRemote)
      .append("\n")

    val maxReqPerConnLocal: Int = intpr.getProperty(CASSANDRA_POOLING_MAX_REQUESTS_PER_CONNECTION,
      DEFAULT_MAX_REQUEST_PER_CONNECTION).toInt
    configBuilder.withInt(DefaultDriverOption.CONNECTION_MAX_REQUESTS, maxReqPerConnLocal)
    poolingOptionsInfo
      .append("\t")
      .append(CASSANDRA_POOLING_MAX_REQUESTS_PER_CONNECTION)
      .append(" : ")
      .append(maxReqPerConnLocal)
      .append("\n")

    val heartbeatIntervalSeconds: Int = intpr.getProperty(CASSANDRA_POOLING_HEARTBEAT_INTERVAL_SECONDS,
      DEFAULT_HEARTBEAT_INTERVAL).toInt
    configBuilder.withInt(DefaultDriverOption.HEARTBEAT_INTERVAL, heartbeatIntervalSeconds)
    poolingOptionsInfo
      .append("\t")
      .append(CASSANDRA_POOLING_HEARTBEAT_INTERVAL_SECONDS)
      .append(" : ")
      .append(heartbeatIntervalSeconds)
      .append("\n")

    val idleTimeoutSeconds: Int = intpr.getProperty(CASSANDRA_POOLING_POOL_TIMEOUT_MILLIS,
      DEFAULT_POOL_TIMEOUT).toInt
    configBuilder.withInt(DefaultDriverOption.HEARTBEAT_TIMEOUT, idleTimeoutSeconds)
    poolingOptionsInfo
      .append("\t")
      .append(CASSANDRA_POOLING_POOL_TIMEOUT_MILLIS)
      .append(" : ")
      .append(idleTimeoutSeconds)
      .append("\n")

    LOGGER.debug(poolingOptionsInfo.append("\n").toString)
  }

  def setCompressionProtocol(intpr: Interpreter, configBuilder: ProgrammaticDriverConfigLoaderBuilder): Unit = {
    val compressionProtocol = intpr.getProperty(CASSANDRA_COMPRESSION_PROTOCOL,
      CassandraInterpreter.DEFAULT_COMPRESSION).toLowerCase
    LOGGER.debug("Compression protocol : " + compressionProtocol)

    compressionProtocol match {
      case "snappy" | "lz4" =>
        configBuilder.withString(DefaultDriverOption.PROTOCOL_COMPRESSION, compressionProtocol)
      case _ => ()
    }
  }

  private def isNotDefaultParameter(param: String) = {
    !(isBlank(param) || DEFAULT_POLICY == param)
  }

  def setRetryPolicy(intpr: Interpreter, configBuilder: ProgrammaticDriverConfigLoaderBuilder): Unit = {
    val retryPolicy: String = intpr.getProperty(CASSANDRA_RETRY_POLICY)
    LOGGER.debug("Retry Policy : " + retryPolicy)

    if (isNotDefaultParameter(retryPolicy)) {
      configBuilder.withString(DefaultDriverOption.RETRY_POLICY_CLASS, retryPolicy)
    }
  }

  def setReconnectionPolicy(intpr: Interpreter, configBuilder: ProgrammaticDriverConfigLoaderBuilder): Unit = {
    val reconnectionPolicy: String = intpr.getProperty(CASSANDRA_RECONNECTION_POLICY)
    LOGGER.debug("Reconnection Policy : " + reconnectionPolicy)

    if (isNotDefaultParameter(reconnectionPolicy)) {
      configBuilder.withString(DefaultDriverOption.RECONNECTION_POLICY_CLASS, reconnectionPolicy)
    }
  }

  def setSpeculativeExecutionPolicy(intpr: Interpreter, configBuilder: ProgrammaticDriverConfigLoaderBuilder): Unit = {
    val specExecPolicy: String = intpr.getProperty(CASSANDRA_SPECULATIVE_EXECUTION_POLICY)
    LOGGER.debug("Speculative Execution Policy : " + specExecPolicy)

    if (isNotDefaultParameter(specExecPolicy)) {
      configBuilder.withString(DefaultDriverOption.SPECULATIVE_EXECUTION_POLICY_CLASS, specExecPolicy)
    }
  }
}
