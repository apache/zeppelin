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
import org.apache.zeppelin.cassandra.CassandraInterpreter._
import org.apache.zeppelin.interpreter.Interpreter
import org.slf4j.LoggerFactory

/**
 * Utility class to extract and configure the Java driver
 */
class JavaDriverConfig {

  val LOGGER = LoggerFactory.getLogger(classOf[JavaDriverConfig])

  def getSocketOptions(intpr: Interpreter): SocketOptions = {
    val socketOptions: SocketOptions = new SocketOptions
    val buf: StringBuilder = new StringBuilder("Socket options : \n\n")
    val connectTimeoutMillis: Int = getProperty(intpr, CASSANDRA_SOCKET_CONNECTION_TIMEOUT_MILLIS, buf).toInt
    socketOptions.setConnectTimeoutMillis(connectTimeoutMillis)

    val readTimeoutMillis: Int = getProperty(intpr, CASSANDRA_SOCKET_READ_TIMEOUT_MILLIS, buf).toInt
    socketOptions.setReadTimeoutMillis(readTimeoutMillis)

    putOption((prop: String) => {
      socketOptions.setTcpNoDelay(parseBoolean(prop))
    }, intpr, CASSANDRA_SOCKET_TCP_NO_DELAY, buf)

    putOption((prop: String) => socketOptions.setKeepAlive(parseBoolean(prop)), intpr, CASSANDRA_SOCKET_KEEP_ALIVE, buf)

    putOption((prop: String) => {
      socketOptions.setReceiveBufferSize(prop.toInt)
    }, intpr, CASSANDRA_SOCKET_RECEIVED_BUFFER_SIZE_BYTES, buf)


    putOption((prop: String) => {
      socketOptions.setSendBufferSize(prop.toInt)
    }, intpr, CASSANDRA_SOCKET_SEND_BUFFER_SIZE_BYTES, buf)

    putOption((prop: String) => {
      socketOptions.setReuseAddress(parseBoolean(prop))
    }, intpr, CASSANDRA_SOCKET_REUSE_ADDRESS, buf)

    putOption((prop: String) => socketOptions.setSoLinger(prop.toInt), intpr, CASSANDRA_SOCKET_SO_LINGER, buf)

    LOGGER.debug(buf.append("\n").toString)
    socketOptions
  }

  private def putOption(
    fn: String => SocketOptions,
    intpr: Interpreter,
    key: String,
    buf: StringBuilder
  ): Unit = {
    val value = intpr.getProperty(key)
    isNotBlank(value) match {
      case true =>
        fn(value)
        buf.append(newLine(key, value))
      case false =>
    }
  }

  private def getProperty(intpr: Interpreter, key: String, buf: StringBuilder): String = {
    val value = intpr.getProperty(key)
    buf.append(newLine(key, value))
    value
  }

  private def newLine(key: String, value: Any): String = s"\t$key : $value\n"

  def getQueryOptions(intpr: Interpreter): QueryOptions = {
    val queryOptions: QueryOptions = new QueryOptions
    val buf: StringBuilder = new StringBuilder("Query options : \n\n")

    val consistencyLevel: ConsistencyLevel = {
      ConsistencyLevel.valueOf(intpr.getProperty(CASSANDRA_QUERY_DEFAULT_CONSISTENCY))
    }
    queryOptions.setConsistencyLevel(consistencyLevel)
    buf.append(newLine(CASSANDRA_QUERY_DEFAULT_CONSISTENCY, consistencyLevel))

    val serialConsistencyLevel = ConsistencyLevel.valueOf(intpr.getProperty(CASSANDRA_QUERY_DEFAULT_SERIAL_CONSISTENCY))
    queryOptions.setSerialConsistencyLevel(serialConsistencyLevel)
    buf.append(newLine(CASSANDRA_QUERY_DEFAULT_SERIAL_CONSISTENCY, serialConsistencyLevel))

    val fetchSize: Int = intpr.getProperty(CASSANDRA_QUERY_DEFAULT_FETCH_SIZE).toInt
    queryOptions.setFetchSize(fetchSize)
    buf.append(newLine(CASSANDRA_QUERY_DEFAULT_FETCH_SIZE, fetchSize))

    val defaultIdempotence: Boolean = parseBoolean(intpr.getProperty(CASSANDRA_QUERY_DEFAULT_IDEMPOTENCE))
    queryOptions.setDefaultIdempotence(defaultIdempotence)
    buf.append(newLine(CASSANDRA_QUERY_DEFAULT_IDEMPOTENCE, defaultIdempotence))

    LOGGER.debug(buf.append("\n").toString)
    queryOptions
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
        ProtocolVersion.V1
      case "2" =>
        DEFAULT_MAX_CONNECTION_PER_HOST_LOCAL = "8"
        DEFAULT_MAX_CONNECTION_PER_HOST_REMOTE = "2"
        DEFAULT_CORE_CONNECTION_PER_HOST_LOCAL = "2"
        DEFAULT_CORE_CONNECTION_PER_HOST_REMOTE = "1"
        DEFAULT_NEW_CONNECTION_THRESHOLD_LOCAL = "100"
        DEFAULT_NEW_CONNECTION_THRESHOLD_REMOTE = "1"
        DEFAULT_MAX_REQUEST_PER_CONNECTION_LOCAL = "128"
        DEFAULT_MAX_REQUEST_PER_CONNECTION_REMOTE = "128"
        ProtocolVersion.V2
      case "3" =>
        DEFAULT_MAX_CONNECTION_PER_HOST_LOCAL = "1"
        DEFAULT_MAX_CONNECTION_PER_HOST_REMOTE = "1"
        DEFAULT_CORE_CONNECTION_PER_HOST_LOCAL = "1"
        DEFAULT_CORE_CONNECTION_PER_HOST_REMOTE = "1"
        DEFAULT_NEW_CONNECTION_THRESHOLD_LOCAL = "800"
        DEFAULT_NEW_CONNECTION_THRESHOLD_REMOTE = "200"
        DEFAULT_MAX_REQUEST_PER_CONNECTION_LOCAL = "1024"
        DEFAULT_MAX_REQUEST_PER_CONNECTION_REMOTE = "256"
        ProtocolVersion.V3
      case _ =>
        DEFAULT_MAX_CONNECTION_PER_HOST_LOCAL = "1"
        DEFAULT_MAX_CONNECTION_PER_HOST_REMOTE = "1"
        DEFAULT_CORE_CONNECTION_PER_HOST_LOCAL = "1"
        DEFAULT_CORE_CONNECTION_PER_HOST_REMOTE = "1"
        DEFAULT_NEW_CONNECTION_THRESHOLD_LOCAL = "800"
        DEFAULT_NEW_CONNECTION_THRESHOLD_REMOTE = "200"
        DEFAULT_MAX_REQUEST_PER_CONNECTION_LOCAL = "1024"
        DEFAULT_MAX_REQUEST_PER_CONNECTION_REMOTE = "256"
        ProtocolVersion.NEWEST_SUPPORTED
    }
  }

  def getPoolingOptions(intpr: Interpreter): PoolingOptions = {
    val poolingOptions: PoolingOptions = new PoolingOptions
    val buf: StringBuilder = new StringBuilder("Pooling options : \n\n")


    val maxConnPerHostLocal: Int = getProperty(intpr, CASSANDRA_POOLING_MAX_CONNECTION_PER_HOST_LOCAL, buf).toInt
    poolingOptions.setMaxConnectionsPerHost(LOCAL, maxConnPerHostLocal)

    val maxConnPerHostRemote: Int = intpr.getProperty(CASSANDRA_POOLING_MAX_CONNECTION_PER_HOST_REMOTE).toInt
    poolingOptions.setMaxConnectionsPerHost(REMOTE, maxConnPerHostRemote)

    val coreConnPerHostLocal: Int = getProperty(intpr, CASSANDRA_POOLING_CORE_CONNECTION_PER_HOST_LOCAL, buf).toInt
    poolingOptions.setCoreConnectionsPerHost(LOCAL, coreConnPerHostLocal)

    val coreConnPerHostRemote: Int = getProperty(intpr, CASSANDRA_POOLING_CORE_CONNECTION_PER_HOST_REMOTE, buf).toInt
    poolingOptions.setCoreConnectionsPerHost(REMOTE, coreConnPerHostRemote)

    val newConnThresholdLocal: Int = getProperty(intpr, CASSANDRA_POOLING_NEW_CONNECTION_THRESHOLD_LOCAL, buf).toInt
    poolingOptions.setNewConnectionThreshold(LOCAL, newConnThresholdLocal)

    val newConnThresholdRemote: Int = getProperty(intpr, CASSANDRA_POOLING_NEW_CONNECTION_THRESHOLD_REMOTE, buf).toInt
    poolingOptions.setNewConnectionThreshold(REMOTE, newConnThresholdRemote)

    val maxReqPerConnLocal: Int = getProperty(intpr, CASSANDRA_POOLING_MAX_REQUESTS_PER_CONNECTION_LOCAL, buf).toInt
    poolingOptions.setMaxRequestsPerConnection(LOCAL, maxReqPerConnLocal)

    val maxReqPerConnRemote: Int = getProperty(intpr, CASSANDRA_POOLING_MAX_REQUESTS_PER_CONNECTION_REMOTE, buf).toInt
    poolingOptions.setMaxRequestsPerConnection(REMOTE, maxReqPerConnRemote)

    val heartbeatIntervalSeconds: Int = getProperty(intpr, CASSANDRA_POOLING_HEARTBEAT_INTERVAL_SECONDS, buf).toInt
    poolingOptions.setHeartbeatIntervalSeconds(heartbeatIntervalSeconds)

    val idleTimeoutSeconds: Int = getProperty(intpr, CASSANDRA_POOLING_IDLE_TIMEOUT_SECONDS, buf).toInt
    poolingOptions.setIdleTimeoutSeconds(idleTimeoutSeconds)

    val poolTimeoutMillis: Int = getProperty(intpr, CASSANDRA_POOLING_POOL_TIMEOUT_MILLIS, buf).toInt
    poolingOptions.setPoolTimeoutMillis(poolTimeoutMillis)

    LOGGER.debug(buf.append("\n").toString)

    poolingOptions
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
    compression
  }

  def getLoadBalancingPolicy(intpr: Interpreter): LoadBalancingPolicy = {
    val loadBalancingPolicy: String = intpr.getProperty(CASSANDRA_LOAD_BALANCING_POLICY)
    LOGGER.debug("Load Balancing Policy : " + loadBalancingPolicy)

    if (isBlank(loadBalancingPolicy) || (DEFAULT_POLICY == loadBalancingPolicy)) {
      Policies.defaultLoadBalancingPolicy
    } else {
      try {
        Class.forName(loadBalancingPolicy).asInstanceOf[Class[LoadBalancingPolicy]].newInstance
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          throw new RuntimeException("Cannot instantiate " + CASSANDRA_LOAD_BALANCING_POLICY +
            " = " + loadBalancingPolicy)
      }
    }
  }

  def getRetryPolicy(intpr: Interpreter): RetryPolicy = {
    val retryPolicy: String = intpr.getProperty(CASSANDRA_RETRY_POLICY)
    LOGGER.debug("Retry Policy : " + retryPolicy)

    if (isBlank(retryPolicy) || (DEFAULT_POLICY == retryPolicy)) {
      Policies.defaultRetryPolicy
    } else {
      try {
        Class.forName(retryPolicy).asInstanceOf[Class[RetryPolicy]].newInstance
      }
      catch {
        case e: Throwable =>
          e.printStackTrace()
          throw new RuntimeException("Cannot instantiate " + CASSANDRA_RETRY_POLICY + " = " + retryPolicy)
      }
    }
  }

  def getReconnectionPolicy(intpr: Interpreter): ReconnectionPolicy = {
    val reconnectionPolicy: String = intpr.getProperty(CASSANDRA_RECONNECTION_POLICY)
    LOGGER.debug("Reconnection Policy : " + reconnectionPolicy)

    if (isBlank(reconnectionPolicy) || (DEFAULT_POLICY == reconnectionPolicy)) {
      Policies.defaultReconnectionPolicy
    }
    else {
      try {
        Class.forName(reconnectionPolicy).asInstanceOf[Class[ReconnectionPolicy]].newInstance
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          throw new RuntimeException("Cannot instantiate " + CASSANDRA_RECONNECTION_POLICY +
            " = " + reconnectionPolicy)
      }
    }
  }

  def getSpeculativeExecutionPolicy(intpr: Interpreter): SpeculativeExecutionPolicy = {
    val specExecPolicy: String = intpr.getProperty(CASSANDRA_SPECULATIVE_EXECUTION_POLICY)
    LOGGER.debug("Speculative Execution Policy : " + specExecPolicy)

    if (isBlank(specExecPolicy) || (DEFAULT_POLICY == specExecPolicy)) {
      Policies.defaultSpeculativeExecutionPolicy
    } else {
      try {
        Class.forName(specExecPolicy).asInstanceOf[Class[SpeculativeExecutionPolicy]].newInstance
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          throw new RuntimeException("Cannot instantiate " + CASSANDRA_SPECULATIVE_EXECUTION_POLICY +
            " = " + specExecPolicy)
      }
    }
  }
}
