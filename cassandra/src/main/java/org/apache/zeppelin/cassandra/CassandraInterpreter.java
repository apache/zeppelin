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
package org.apache.zeppelin.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import com.datastax.oss.driver.internal.core.loadbalancing.DcInferringLoadBalancingPolicy;
import com.datastax.oss.driver.shaded.guava.common.net.InetAddresses;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import static java.lang.Integer.parseInt;

/**
 * Interpreter for Apache Cassandra CQL query language.
 */
public class CassandraInterpreter extends Interpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(CassandraInterpreter.class);

  public static final String CASSANDRA_INTERPRETER_PARALLELISM =
          "cassandra.interpreter.parallelism";
  public static final String CASSANDRA_HOSTS = "cassandra.hosts";
  public static final String CASSANDRA_PORT = "cassandra.native.port";
  public static final String CASSANDRA_PROTOCOL_VERSION = "cassandra.protocol.version";
  public static final String CASSANDRA_CLUSTER_NAME = "cassandra.cluster";
  public static final String CASSANDRA_KEYSPACE_NAME = "cassandra.keyspace";
  public static final String CASSANDRA_COMPRESSION_PROTOCOL = "cassandra.compression.protocol";
  public static final String CASSANDRA_CREDENTIALS_USERNAME = "cassandra.credentials.username";
  public static final String CASSANDRA_CREDENTIALS_PASSWORD = "cassandra.credentials.password";
  public static final String CASSANDRA_LOAD_BALANCING_POLICY = "cassandra.load.balancing.policy";
  public static final String CASSANDRA_RETRY_POLICY = "cassandra.retry.policy";
  public static final String CASSANDRA_RECONNECTION_POLICY = "cassandra.reconnection.policy";
  public static final String CASSANDRA_SPECULATIVE_EXECUTION_POLICY =
          "cassandra.speculative.execution.policy";
  public static final String CASSANDRA_MAX_SCHEMA_AGREEMENT_WAIT_SECONDS =
          "cassandra.max.schema.agreement.wait.second";
  public static final String CASSANDRA_POOLING_CONNECTION_PER_HOST_LOCAL =
          "cassandra.pooling.connection.per.host.local";
  public static final String CASSANDRA_POOLING_CONNECTION_PER_HOST_REMOTE =
          "cassandra.pooling.connection.per.host.remote";
  public static final String CASSANDRA_POOLING_MAX_REQUESTS_PER_CONNECTION =
          "cassandra.pooling.max.request.per.connection";
  public static final String CASSANDRA_POOLING_POOL_TIMEOUT_MILLIS =
          "cassandra.pooling.pool.timeout.millisecs";
  public static final String CASSANDRA_POOLING_HEARTBEAT_INTERVAL_SECONDS =
          "cassandra.pooling.heartbeat.interval.seconds";
  public static final String CASSANDRA_QUERY_DEFAULT_CONSISTENCY =
          "cassandra.query.default.consistency";
  public static final String CASSANDRA_QUERY_DEFAULT_SERIAL_CONSISTENCY =
          "cassandra.query.default.serial.consistency";
  public static final String CASSANDRA_QUERY_DEFAULT_FETCH_SIZE =
          "cassandra.query.default.fetchSize";
  public static final String CASSANDRA_QUERY_DEFAULT_IDEMPOTENCE =
          "cassandra.query.default.idempotence";
  public static final String CASSANDRA_SOCKET_CONNECTION_TIMEOUT_MILLIS =
          "cassandra.socket.connection.timeout.millisecs";
  public static final String CASSANDRA_SOCKET_KEEP_ALIVE =
          "cassandra.socket.keep.alive";
  public static final String CASSANDRA_SOCKET_READ_TIMEOUT_MILLIS =
          "cassandra.socket.read.timeout.millisecs";
  public static final String CASSANDRA_SOCKET_RECEIVED_BUFFER_SIZE_BYTES =
          "cassandra.socket.received.buffer.size.bytes";
  public static final String CASSANDRA_SOCKET_REUSE_ADDRESS =
          "cassandra.socket.reuse.address";
  public static final String CASSANDRA_SOCKET_SEND_BUFFER_SIZE_BYTES =
          "cassandra.socket.send.buffer.size.bytes";
  public static final String CASSANDRA_SOCKET_SO_LINGER =
          "cassandra.socket.soLinger";
  public static final String CASSANDRA_SOCKET_TCP_NO_DELAY =
          "cassandra.socket.tcp.no_delay";
  public static final String CASSANDRA_WITH_SSL =
          "cassandra.ssl.enabled";
  public static final String CASSANDRA_TRUSTSTORE_PATH =
          "cassandra.ssl.truststore.path";
  public static final String CASSANDRA_TRUSTSTORE_PASSWORD =
          "cassandra.ssl.truststore.password";


  public static final String DEFAULT_HOST = "127.0.0.1";
  public static final String DEFAULT_PORT = "9042";
  public static final String DEFAULT_KEYSPACE = "system";
  public static final String DEFAULT_PROTOCOL_VERSION = "DEFAULT";
  public static final String DEFAULT_COMPRESSION = "none";
  public static final String DEFAULT_CONNECTIONS_PER_HOST = "1";
  public static final String DEFAULT_MAX_REQUEST_PER_CONNECTION = "1024";
  public static final String DEFAULT_POLICY = "DEFAULT";
  public static final String DEFAULT_PARALLELISM = "10";
  public static final String DEFAULT_POOL_TIMEOUT = "5000";
  public static final String DEFAULT_HEARTBEAT_INTERVAL = "30";
  public static final String DEFAULT_CONSISTENCY = "ONE";
  public static final String DEFAULT_SERIAL_CONSISTENCY = "SERIAL";
  public static final String DEFAULT_FETCH_SIZE = "5000";
  public static final String DEFAULT_CONNECTION_TIMEOUT = "5000";
  public static final String DEFAULT_READ_TIMEOUT = "12000";
  public static final String DEFAULT_TCP_NO_DELAY = "true";
  public static final String DEFAULT_MAX_SCHEMA_AGREEMENT_WAIT_SECONDS = "12";

  static final List NO_COMPLETION = new ArrayList<>();

  InterpreterLogic helper;
  CqlSession session;
  private JavaDriverConfig driverConfig = new JavaDriverConfig();

  public CassandraInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public void open() {

    final String[] addresses = getProperty(CASSANDRA_HOSTS, DEFAULT_HOST).split(",");
    final int port = parseInt(getProperty(CASSANDRA_PORT, DEFAULT_PORT));
    Collection<InetSocketAddress> hosts = new ArrayList<>();
    for (String address : addresses) {
      if (InetAddresses.isInetAddress(address)) {
        hosts.add(new InetSocketAddress(address, port));
      } else {
        // TODO(alex): maybe it won't be necessary in 4.4
        hosts.add(InetSocketAddress.createUnresolved(address, port));
      }
    }

    LOGGER.info("Bootstrapping Cassandra Java Driver to connect to " +
            getProperty(CASSANDRA_HOSTS) + "on port " + port);

    // start generation of the config
    ProgrammaticDriverConfigLoaderBuilder configBuilder = DriverConfigLoader.programmaticBuilder();

    driverConfig.setCompressionProtocol(this, configBuilder);
    driverConfig.setPoolingOptions(this, configBuilder);
    driverConfig.setProtocolVersion(this, configBuilder);
    driverConfig.setQueryOptions(this, configBuilder);
    driverConfig.setReconnectionPolicy(this, configBuilder);
    driverConfig.setRetryPolicy(this, configBuilder);
    driverConfig.setSocketOptions(this, configBuilder);
    driverConfig.setSpeculativeExecutionPolicy(this, configBuilder);

    //
    configBuilder.withClass(DefaultDriverOption.LOAD_BALANCING_POLICY_CLASS,
            DcInferringLoadBalancingPolicy.class);
    configBuilder.withBoolean(DefaultDriverOption.RESOLVE_CONTACT_POINTS, false);

    configBuilder.withInt(DefaultDriverOption.CONTROL_CONNECTION_AGREEMENT_TIMEOUT,
            parseInt(getProperty(CASSANDRA_MAX_SCHEMA_AGREEMENT_WAIT_SECONDS,
                    DEFAULT_MAX_SCHEMA_AGREEMENT_WAIT_SECONDS)));

    DriverConfigLoader loader = configBuilder.endProfile().build();
    // TODO(alex): think how to dump built configuration...
    logger.debug(loader.toString());
    // end generation of config

    CqlSessionBuilder clusterBuilder = CqlSession.builder()
            .addContactPoints(hosts)
            .withAuthCredentials(getProperty(CASSANDRA_CREDENTIALS_USERNAME),
                    getProperty(CASSANDRA_CREDENTIALS_PASSWORD))
            .withApplicationName("")
            .withApplicationVersion("");

    String keyspace = getProperty(CASSANDRA_KEYSPACE_NAME, DEFAULT_KEYSPACE);
    if (StringUtils.isNotBlank(keyspace) && !DEFAULT_KEYSPACE.equalsIgnoreCase(keyspace)) {
      clusterBuilder.withKeyspace(keyspace);
    }

    final String runWithSSL = getProperty(CASSANDRA_WITH_SSL);
    if (runWithSSL != null && runWithSSL.equals("true")) {
      LOGGER.debug("Cassandra Interpreter: Using SSL");

      try {
        final SSLContext sslContext;
        {
          final KeyStore trustStore = KeyStore.getInstance("JKS");
          final InputStream stream = Files.newInputStream(Paths.get(
                  getProperty(CASSANDRA_TRUSTSTORE_PATH)));
          trustStore.load(stream, getProperty(CASSANDRA_TRUSTSTORE_PASSWORD).toCharArray());

          final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                  TrustManagerFactory.getDefaultAlgorithm());
          trustManagerFactory.init(trustStore);

          sslContext = SSLContext.getInstance("TLS");
          sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        }
        clusterBuilder = clusterBuilder.withSslContext(sslContext);
      } catch (Exception e) {
        LOGGER.error(e.toString());
      }
    } else {
      LOGGER.debug("Cassandra Interpreter: Not using SSL");
    }

    session = clusterBuilder.withConfigLoader(loader).build();
    helper = new InterpreterLogic(session);
  }

  @Override
  public void close() {
    session.close();
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    return helper.interpret(session, st, context);
  }

  @Override
  public void cancel(InterpreterContext context) {
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) {
    return NO_COMPLETION;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton()
            .createOrGetParallelScheduler(CassandraInterpreter.class.getName() + this.hashCode(),
                    parseInt(getProperty(CASSANDRA_INTERPRETER_PARALLELISM, DEFAULT_PARALLELISM)));
  }
}
