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

import com.datastax.driver.core.HostDistance._
import com.datastax.driver.core.ProtocolOptions.Compression
import com.datastax.driver.core._
import com.datastax.driver.core.policies._
import org.apache.commons.lang3.StringUtils._
import org.apache.zeppelin.interpreter.Interpreter
import org.apache.zeppelin.cassandra.CassandraInterpreter._
import org.slf4j.LoggerFactory

/**
 * Utility class to extract and configure the Java driver
 */
class JavaDriverConfig {

  val LOGGER = LoggerFactory.getLogger(classOf[JavaDriverConfig])

  def getSocketOptions(intpr: Interpreter): SocketOptions = {
    val socketOptions: SocketOptions = new SocketOptions
    val socketOptionsInfo: StringBuilder = new StringBuilder("Socket options : \n\n")

    val connectTimeoutMillis: Int = intpr.getProperty(CASSANDRA_SOCKET_CONNECTION_TIMEOUT_MILLIS).toInt
    socketOptions.setConnectTimeoutMillis(connectTimeoutMillis)
    socketOptionsInfo
      .append("\t")
      .append(CASSANDRA_SOCKET_CONNECTION_TIMEOUT_MILLIS)
      .append(" : ")
      .append(connectTimeoutMillis).append("\n")

    val readTimeoutMillis: Int = intpr.getProperty(CASSANDRA_SOCKET_READ_TIMEOUT_MILLIS).toInt
    socketOptions.setReadTimeoutMillis(readTimeoutMillis)
    socketOptionsInfo
      .append("\t")
      .append(CASSANDRA_SOCKET_READ_TIMEOUT_MILLIS)
      .append(" : ")
      .append(readTimeoutMillis).append("\n")

    val tcpNoDelay: Boolean = parseBoolean(intpr.getProperty(CASSANDRA_SOCKET_TCP_NO_DELAY))
    socketOptions.setTcpNoDelay(tcpNoDelay)
    socketOptionsInfo
      .append("\t")
      .append(CASSANDRA_SOCKET_TCP_NO_DELAY)
      .append(" : ")
      .append(tcpNoDelay)
      .append("\n")

    val keepAlive: String = intpr.getProperty(CASSANDRA_SOCKET_KEEP_ALIVE)
    if (isNotBlank(keepAlive)) {
      val keepAliveValue: Boolean = parseBoolean(keepAlive)
      socketOptions.setKeepAlive(keepAliveValue)
      socketOptionsInfo
        .append("\t")
        .append(CASSANDRA_SOCKET_KEEP_ALIVE)
        .append(" : ")
        .append(keepAliveValue).append("\n")
    }

    val receivedBuffSize: String = intpr.getProperty(CASSANDRA_SOCKET_RECEIVED_BUFFER_SIZE_BYTES)
    if (isNotBlank(receivedBuffSize)) {
      val receiveBufferSizeValue: Int = receivedBuffSize.toInt
      socketOptions.setReceiveBufferSize(receiveBufferSizeValue)
      socketOptionsInfo
        .append("\t")
        .append(CASSANDRA_SOCKET_RECEIVED_BUFFER_SIZE_BYTES)
        .append(" : ")
        .append(receiveBufferSizeValue)
        .append("\n")
    }

    val sendBuffSize: String = intpr.getProperty(CASSANDRA_SOCKET_SEND_BUFFER_SIZE_BYTES)
    if (isNotBlank(sendBuffSize)) {
      val sendBufferSizeValue: Int = sendBuffSize.toInt
      socketOptions.setSendBufferSize(sendBufferSizeValue)
      socketOptionsInfo
        .append("\t")
        .append(CASSANDRA_SOCKET_SEND_BUFFER_SIZE_BYTES)
        .append(" : ")
        .append(sendBufferSizeValue)
        .append("\n")
    }

    val reuseAddress: String = intpr.getProperty(CASSANDRA_SOCKET_REUSE_ADDRESS)
    if (isNotBlank(reuseAddress)) {
      val reuseAddressValue: Boolean = parseBoolean(reuseAddress)
      socketOptions.setReuseAddress(reuseAddressValue)
      socketOptionsInfo
        .append("\t")
        .append(CASSANDRA_SOCKET_REUSE_ADDRESS)
        .append(" : ")
        .append(reuseAddressValue)
        .append("\n")
    }

    val soLinger: String = intpr.getProperty(CASSANDRA_SOCKET_SO_LINGER)
    if (isNotBlank(soLinger)) {
      val soLingerValue: Int = soLinger.toInt
      socketOptions.setSoLinger(soLingerValue)
      socketOptionsInfo
        .append("\t")
        .append(CASSANDRA_SOCKET_SO_LINGER)
        .append(" : ")
        .append(soLingerValue)
        .append("\n")
    }

    LOGGER.debug(socketOptionsInfo.append("\n").toString)

    return socketOptions
  }

  def getQueryOptions(intpr: Interpreter): QueryOptions = {
    val queryOptions: QueryOptions = new QueryOptions
    val queryOptionsInfo: StringBuilder = new StringBuilder("Query options : \n\n")

    val consistencyLevel: ConsistencyLevel = ConsistencyLevel.valueOf(intpr.getProperty(CASSANDRA_QUERY_DEFAULT_CONSISTENCY))
    queryOptions.setConsistencyLevel(consistencyLevel)
    queryOptionsInfo
      .append("\t")
      .append(CASSANDRA_QUERY_DEFAULT_CONSISTENCY)
      .append(" : ")
      .append(consistencyLevel)
      .append("\n")

    val serialConsistencyLevel: ConsistencyLevel = ConsistencyLevel.valueOf(intpr.getProperty(CASSANDRA_QUERY_DEFAULT_SERIAL_CONSISTENCY))
    queryOptions.setSerialConsistencyLevel(serialConsistencyLevel)
    queryOptionsInfo
      .append("\t")
      .append(CASSANDRA_QUERY_DEFAULT_SERIAL_CONSISTENCY)
      .append(" : ")
      .append(serialConsistencyLevel)
      .append("\n")

    val fetchSize: Int = intpr.getProperty(CASSANDRA_QUERY_DEFAULT_FETCH_SIZE).toInt
    queryOptions.setFetchSize(fetchSize)
    queryOptionsInfo
      .append("\t")
      .append(CASSANDRA_QUERY_DEFAULT_FETCH_SIZE)
      .append(" : ")
      .append(fetchSize)
      .append("\n")

    val defaultIdempotence: Boolean = parseBoolean(intpr.getProperty(CASSANDRA_QUERY_DEFAULT_IDEMPOTENCE))
    queryOptions.setDefaultIdempotence(defaultIdempotence)
    queryOptionsInfo
      .append("\t")
      .append(CASSANDRA_QUERY_DEFAULT_IDEMPOTENCE)
      .append(" : ")
      .append(defaultIdempotence)
      .append("\n")

    LOGGER.debug(queryOptionsInfo.append("\n").toString)

    return queryOptions
  }

  def getProtocolVersion(intpr: Interpreter): ProtocolVersion = {
    val protocolVersion: String = intpr.getProperty(CASSANDRA_PROTOCOL_VERSION)
    LOGGER.debug("Protocol version : " + protocolVersion)

    protocolVersion match {
      case "1" =>
        DEFAULT_MAX_CONNECTION_PER_HOST_LOCAL = "8"
        DEFAULT_MAX_CONNECTION_PER_HOST_REMOTE = "2"
        DEFAULT_CORE_CONNECTION_PER_HOST_LOCAL = "2"
        DEFAULT_CORE_CONNECTION_PER_HOST_REMOTE = "1"
        DEFAULT_NEW_CONNECTION_THRESHOLD_LOCAL = "100"
        DEFAULT_NEW_CONNECTION_THRESHOLD_REMOTE = "1"
        DEFAULT_MAX_REQUEST_PER_CONNECTION_LOCAL = "128"
        DEFAULT_MAX_REQUEST_PER_CONNECTION_REMOTE = "128"
        return ProtocolVersion.V1
      case "2" =>
        DEFAULT_MAX_CONNECTION_PER_HOST_LOCAL = "8"
        DEFAULT_MAX_CONNECTION_PER_HOST_REMOTE = "2"
        DEFAULT_CORE_CONNECTION_PER_HOST_LOCAL = "2"
        DEFAULT_CORE_CONNECTION_PER_HOST_REMOTE = "1"
        DEFAULT_NEW_CONNECTION_THRESHOLD_LOCAL = "100"
        DEFAULT_NEW_CONNECTION_THRESHOLD_REMOTE = "1"
        DEFAULT_MAX_REQUEST_PER_CONNECTION_LOCAL = "128"
        DEFAULT_MAX_REQUEST_PER_CONNECTION_REMOTE = "128"
        return ProtocolVersion.V2
      case "3" =>
        DEFAULT_MAX_CONNECTION_PER_HOST_LOCAL = "1"
        DEFAULT_MAX_CONNECTION_PER_HOST_REMOTE = "1"
        DEFAULT_CORE_CONNECTION_PER_HOST_LOCAL = "1"
        DEFAULT_CORE_CONNECTION_PER_HOST_REMOTE = "1"
        DEFAULT_NEW_CONNECTION_THRESHOLD_LOCAL = "800"
        DEFAULT_NEW_CONNECTION_THRESHOLD_REMOTE = "200"
        DEFAULT_MAX_REQUEST_PER_CONNECTION_LOCAL = "1024"
        DEFAULT_MAX_REQUEST_PER_CONNECTION_REMOTE = "256"
        return ProtocolVersion.V3
      case "4" =>
        DEFAULT_MAX_CONNECTION_PER_HOST_LOCAL = "1"
        DEFAULT_MAX_CONNECTION_PER_HOST_REMOTE = "1"
        DEFAULT_CORE_CONNECTION_PER_HOST_LOCAL = "1"
        DEFAULT_CORE_CONNECTION_PER_HOST_REMOTE = "1"
        DEFAULT_NEW_CONNECTION_THRESHOLD_LOCAL = "800"
        DEFAULT_NEW_CONNECTION_THRESHOLD_REMOTE = "200"
        DEFAULT_MAX_REQUEST_PER_CONNECTION_LOCAL = "1024"
        DEFAULT_MAX_REQUEST_PER_CONNECTION_REMOTE = "256"
        return ProtocolVersion.V4
      case _ =>
        DEFAULT_MAX_CONNECTION_PER_HOST_LOCAL = "1"
        DEFAULT_MAX_CONNECTION_PER_HOST_REMOTE = "1"
        DEFAULT_CORE_CONNECTION_PER_HOST_LOCAL = "1"
        DEFAULT_CORE_CONNECTION_PER_HOST_REMOTE = "1"
        DEFAULT_NEW_CONNECTION_THRESHOLD_LOCAL = "800"
        DEFAULT_NEW_CONNECTION_THRESHOLD_REMOTE = "200"
        DEFAULT_MAX_REQUEST_PER_CONNECTION_LOCAL = "1024"
        DEFAULT_MAX_REQUEST_PER_CONNECTION_REMOTE = "256"
        return ProtocolVersion.NEWEST_SUPPORTED
    }
  }

  def getPoolingOptions(intpr: Interpreter): PoolingOptions = {
    val poolingOptions: PoolingOptions = new PoolingOptions
    val poolingOptionsInfo: StringBuilder = new StringBuilder("Pooling options : \n\n")

    val maxConnPerHostLocal: Int = intpr.getProperty(CASSANDRA_POOLING_MAX_CONNECTION_PER_HOST_LOCAL).toInt
    poolingOptions.setMaxConnectionsPerHost(LOCAL, maxConnPerHostLocal)
    poolingOptionsInfo
      .append("\t")
      .append(CASSANDRA_POOLING_MAX_CONNECTION_PER_HOST_LOCAL)
      .append(" : ")
      .append(maxConnPerHostLocal)
      .append("\n")

    val maxConnPerHostRemote: Int = intpr.getProperty(CASSANDRA_POOLING_MAX_CONNECTION_PER_HOST_REMOTE).toInt
    poolingOptions.setMaxConnectionsPerHost(REMOTE, maxConnPerHostRemote)
    poolingOptionsInfo
      .append("\t")
      .append(CASSANDRA_POOLING_MAX_CONNECTION_PER_HOST_REMOTE)
      .append(" : ")
      .append(maxConnPerHostRemote)
      .append("\n")

    val coreConnPerHostLocal: Int = intpr.getProperty(CASSANDRA_POOLING_CORE_CONNECTION_PER_HOST_LOCAL).toInt
    poolingOptions.setCoreConnectionsPerHost(LOCAL, coreConnPerHostLocal)
    poolingOptionsInfo
      .append("\t")
      .append(CASSANDRA_POOLING_CORE_CONNECTION_PER_HOST_LOCAL)
      .append(" : ")
      .append(coreConnPerHostLocal)
      .append("\n")

    val coreConnPerHostRemote: Int = intpr.getProperty(CASSANDRA_POOLING_CORE_CONNECTION_PER_HOST_REMOTE).toInt
    poolingOptions.setCoreConnectionsPerHost(REMOTE, coreConnPerHostRemote)
    poolingOptionsInfo
      .append("\t")
      .append(CASSANDRA_POOLING_CORE_CONNECTION_PER_HOST_REMOTE)
      .append(" : ")
      .append(coreConnPerHostRemote)
      .append("\n")

    val newConnThresholdLocal: Int = intpr.getProperty(CASSANDRA_POOLING_NEW_CONNECTION_THRESHOLD_LOCAL).toInt
    poolingOptions.setNewConnectionThreshold(LOCAL, newConnThresholdLocal)
    poolingOptionsInfo
      .append("\t")
      .append(CASSANDRA_POOLING_NEW_CONNECTION_THRESHOLD_LOCAL)
      .append(" : ")
      .append(newConnThresholdLocal)
      .append("\n")

    val newConnThresholdRemote: Int = intpr.getProperty(CASSANDRA_POOLING_NEW_CONNECTION_THRESHOLD_REMOTE).toInt
    poolingOptions.setNewConnectionThreshold(REMOTE, newConnThresholdRemote)
    poolingOptionsInfo
      .append("\t")
      .append(CASSANDRA_POOLING_NEW_CONNECTION_THRESHOLD_REMOTE)
      .append(" : ")
      .append(newConnThresholdRemote)
      .append("\n")

    val maxReqPerConnLocal: Int = intpr.getProperty(CASSANDRA_POOLING_MAX_REQUESTS_PER_CONNECTION_LOCAL).toInt
    poolingOptions.setMaxRequestsPerConnection(LOCAL, maxReqPerConnLocal)
    poolingOptionsInfo
      .append("\t")
      .append(CASSANDRA_POOLING_MAX_REQUESTS_PER_CONNECTION_LOCAL)
      .append(" : ")
      .append(maxReqPerConnLocal)
      .append("\n")

    val maxReqPerConnRemote: Int = intpr.getProperty(CASSANDRA_POOLING_MAX_REQUESTS_PER_CONNECTION_REMOTE).toInt
    poolingOptions.setMaxRequestsPerConnection(REMOTE, maxReqPerConnRemote)
    poolingOptionsInfo
      .append("\t")
      .append(CASSANDRA_POOLING_MAX_REQUESTS_PER_CONNECTION_REMOTE)
      .append(" : ")
      .append(maxReqPerConnRemote)
      .append("\n")

    val heartbeatIntervalSeconds: Int = intpr.getProperty(CASSANDRA_POOLING_HEARTBEAT_INTERVAL_SECONDS).toInt
    poolingOptions.setHeartbeatIntervalSeconds(heartbeatIntervalSeconds)
    poolingOptionsInfo
      .append("\t")
      .append(CASSANDRA_POOLING_HEARTBEAT_INTERVAL_SECONDS)
      .append(" : ")
      .append(heartbeatIntervalSeconds)
      .append("\n")

    val idleTimeoutSeconds: Int = intpr.getProperty(CASSANDRA_POOLING_IDLE_TIMEOUT_SECONDS).toInt
    poolingOptions.setIdleTimeoutSeconds(idleTimeoutSeconds)
    poolingOptionsInfo
      .append("\t")
      .append(CASSANDRA_POOLING_IDLE_TIMEOUT_SECONDS)
      .append(" : ")
      .append(idleTimeoutSeconds)
      .append("\n")

    val poolTimeoutMillis: Int = intpr.getProperty(CASSANDRA_POOLING_POOL_TIMEOUT_MILLIS).toInt
    poolingOptions.setPoolTimeoutMillis(poolTimeoutMillis)
    poolingOptionsInfo
      .append("\t")
      .append(CASSANDRA_POOLING_POOL_TIMEOUT_MILLIS)
      .append(" : ")
      .append(poolTimeoutMillis)
      .append("\n")

    LOGGER.debug(poolingOptionsInfo.append("\n").toString)

    return poolingOptions
  }

  def getCompressionProtocol(intpr: Interpreter): ProtocolOptions.Compression = {
    var compression: ProtocolOptions.Compression = null
    val compressionProtocol: String = intpr.getProperty(CASSANDRA_COMPRESSION_PROTOCOL)

    LOGGER.debug("Compression protocol : " + compressionProtocol)

    if (compressionProtocol == null) "NONE"
    else compressionProtocol.toUpperCase match {
      case "NONE" =>
        compression = Compression.NONE
      case "SNAPPY" =>
        compression = Compression.SNAPPY
      case "LZ4" =>
        compression = Compression.LZ4
      case _ =>
        compression = Compression.NONE
    }
    return compression
  }

  def getLoadBalancingPolicy(intpr: Interpreter): LoadBalancingPolicy = {
    val loadBalancingPolicy: String = intpr.getProperty(CASSANDRA_LOAD_BALANCING_POLICY)
    LOGGER.debug("Load Balancing Policy : " + loadBalancingPolicy)

    if (isBlank(loadBalancingPolicy) || (DEFAULT_POLICY == loadBalancingPolicy)) {
      return Policies.defaultLoadBalancingPolicy
    }
    else {
      try {
        return (Class.forName(loadBalancingPolicy).asInstanceOf[Class[LoadBalancingPolicy]]).newInstance
      }
      catch {
        case e: Any => {
          e.printStackTrace
          throw new RuntimeException("Cannot instantiate " + CASSANDRA_LOAD_BALANCING_POLICY + " = " + loadBalancingPolicy)
        }
      }
    }
  }

  def getRetryPolicy(intpr: Interpreter): RetryPolicy = {
    val retryPolicy: String = intpr.getProperty(CASSANDRA_RETRY_POLICY)
    LOGGER.debug("Retry Policy : " + retryPolicy)

    if (isBlank(retryPolicy) || (DEFAULT_POLICY == retryPolicy)) {
      return Policies.defaultRetryPolicy
    }
    else {
      try {
        return (Class.forName(retryPolicy).asInstanceOf[Class[RetryPolicy]]).newInstance
      }
      catch {
        case e: Any => {
          e.printStackTrace
          throw new RuntimeException("Cannot instantiate " + CASSANDRA_RETRY_POLICY + " = " + retryPolicy)
        }
      }
    }
  }

  def getReconnectionPolicy(intpr: Interpreter): ReconnectionPolicy = {
    val reconnectionPolicy: String = intpr.getProperty(CASSANDRA_RECONNECTION_POLICY)
    LOGGER.debug("Reconnection Policy : " + reconnectionPolicy)

    if (isBlank(reconnectionPolicy) || (DEFAULT_POLICY == reconnectionPolicy)) {
      return Policies.defaultReconnectionPolicy
    }
    else {
      try {
        return (Class.forName(reconnectionPolicy).asInstanceOf[Class[ReconnectionPolicy]]).newInstance
      }
      catch {
        case e: Any => {
          e.printStackTrace
          throw new RuntimeException("Cannot instantiate " + CASSANDRA_RECONNECTION_POLICY + " = " + reconnectionPolicy)
        }
      }
    }
  }

  def getSpeculativeExecutionPolicy(intpr: Interpreter): SpeculativeExecutionPolicy = {
    val specExecPolicy: String = intpr.getProperty(CASSANDRA_SPECULATIVE_EXECUTION_POLICY)
    LOGGER.debug("Speculative Execution Policy : " + specExecPolicy)

    if (isBlank(specExecPolicy) || (DEFAULT_POLICY == specExecPolicy)) {
      return Policies.defaultSpeculativeExecutionPolicy
    }
    else {
      try {
        return (Class.forName(specExecPolicy).asInstanceOf[Class[SpeculativeExecutionPolicy]]).newInstance
      }
      catch {
        case e: Any => {
          e.printStackTrace
          throw new RuntimeException("Cannot instantiate " + CASSANDRA_SPECULATIVE_EXECUTION_POLICY + " = " + specExecPolicy)
        }
      }
    }
  }  
}
