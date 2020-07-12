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
import com.datastax.oss.driver.api.core.config.DriverOption;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  public static final String CASSANDRA_FORMAT_FLOAT_PRECISION =
          "cassandra.format.float_precision";
  public static final String CASSANDRA_FORMAT_DOUBLE_PRECISION =
          "cassandra.format.double_precision";
  public static final String CASSANDRA_FORMAT_TIMESTAMP =
          "cassandra.format.timestamp";
  public static final String CASSANDRA_FORMAT_TIME =
          "cassandra.format.time";
  public static final String CASSANDRA_FORMAT_DATE =
          "cassandra.format.date";
  public static final String CASSANDRA_FORMAT_TYPE =
          "cassandra.format.output";
  public static final String CASSANDRA_FORMAT_TIMEZONE =
          "cassandra.format.timezone";
  public static final String CASSANDRA_FORMAT_LOCALE =
          "cassandra.format.locale";

  public static final String NONE_VALUE = "none";
  public static final String DEFAULT_VALUE = "DEFAULT";
  public static final String DEFAULT_HOST = "127.0.0.1";
  public static final String DEFAULT_PORT = "9042";
  public static final String DEFAULT_KEYSPACE = "system";
  public static final String DEFAULT_PROTOCOL_VERSION = "DEFAULT";
  public static final String DEFAULT_COMPRESSION = NONE_VALUE;
  public static final String DEFAULT_CONNECTIONS_PER_HOST = "1";
  public static final String DEFAULT_MAX_REQUEST_PER_CONNECTION = "1024";
  public static final String DEFAULT_POLICY = DEFAULT_VALUE;
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

  static final List<InterpreterCompletion> NO_COMPLETION = new ArrayList<>();
  public static final String DATASTAX_JAVA_DRIVER_PREFIX = "datastax-java-driver.";
  public static final String MILLISECONDS_STR = " milliseconds";
  public static final String SECONDS_STR = " seconds";

  InterpreterLogic helper;
  CqlSession session;
  private static final Map<String, DriverOption> optionMap = new HashMap<>();

  static {
    for (DefaultDriverOption opt: DefaultDriverOption.values()) {
      optionMap.put(opt.getPath(), opt);
    }
  }

  public CassandraInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public void open() {
    final String[] addresses = getProperty(CASSANDRA_HOSTS, DEFAULT_HOST)
            .trim().split(",");
    final int port = parseInt(getProperty(CASSANDRA_PORT, DEFAULT_PORT));
    Collection<InetSocketAddress> hosts = new ArrayList<>();
    for (String address : addresses) {
      if (!StringUtils.isBlank(address)) {
        logger.debug("Adding contact point: {}", address);
        if (InetAddresses.isInetAddress(address)) {
          hosts.add(new InetSocketAddress(address, port));
        } else {
          hosts.add(InetSocketAddress.createUnresolved(address, port));
        }
      }
    }

    LOGGER.info("Bootstrapping Cassandra Java Driver to connect to {} on port {}",
            getProperty(CASSANDRA_HOSTS), port);

    DriverConfigLoader loader = createLoader();

    LOGGER.debug("Creating cluster builder");
    CqlSessionBuilder clusterBuilder = CqlSession.builder()
            .withApplicationName("Zeppelin")
            .withApplicationVersion("");
    if (!hosts.isEmpty()) {
      LOGGER.debug("Adding contact points");
      clusterBuilder.addContactPoints(hosts);
    }

    String username = getProperty(CASSANDRA_CREDENTIALS_USERNAME, NONE_VALUE).trim();
    String password = getProperty(CASSANDRA_CREDENTIALS_PASSWORD, NONE_VALUE).trim();
    if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password) &&
            !NONE_VALUE.equalsIgnoreCase(username) && !NONE_VALUE.equalsIgnoreCase(password)) {
      LOGGER.debug("Adding credentials. Username = {}", username);
      clusterBuilder.withAuthCredentials(username, password);
    }

    String keyspace = getProperty(CASSANDRA_KEYSPACE_NAME, DEFAULT_KEYSPACE);
    if (StringUtils.isNotBlank(keyspace) && !DEFAULT_KEYSPACE.equalsIgnoreCase(keyspace)) {
      LOGGER.debug("Set default keyspace");
      clusterBuilder.withKeyspace(keyspace);
    }

    final String runWithSSL = getProperty(CASSANDRA_WITH_SSL, "false");
    if ("true".equalsIgnoreCase(runWithSSL)) {
      LOGGER.debug("Using SSL");
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
        LOGGER.error("Exception initializing SSL {}", e.toString());
      }
    } else {
      LOGGER.debug("Not using SSL");
    }

    LOGGER.debug("Creating CqlSession");
    session = clusterBuilder.withConfigLoader(loader).build();
    LOGGER.debug("Session configuration");
    for (Map.Entry<String, Object> entry:
            session.getContext().getConfig().getDefaultProfile().entrySet()) {
      logger.debug("{} = {}", entry.getKey(), entry.getValue().toString());
    }
    LOGGER.debug("Creating helper");
    helper = new InterpreterLogic(session, properties);
  }

  private DriverConfigLoader createLoader() {
    logger.debug("Creating programmatic config loader");
    // start generation of the config
    ProgrammaticDriverConfigLoaderBuilder configBuilder = DriverConfigLoader.programmaticBuilder();

    Map<DriverOption, String> allOptions = new HashMap<>();

    // set options from main configuration
    String ts = getProperty(CASSANDRA_SOCKET_CONNECTION_TIMEOUT_MILLIS,
            CassandraInterpreter.DEFAULT_CONNECTION_TIMEOUT) + MILLISECONDS_STR;
    allOptions.put(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, ts);
    allOptions.put(DefaultDriverOption.CONTROL_CONNECTION_TIMEOUT, ts);
    allOptions.put(DefaultDriverOption.REQUEST_TIMEOUT,
            getProperty(CASSANDRA_SOCKET_READ_TIMEOUT_MILLIS,
                    CassandraInterpreter.DEFAULT_READ_TIMEOUT) + MILLISECONDS_STR);
    addIfNotBlank(allOptions,
            getProperty(CASSANDRA_SOCKET_TCP_NO_DELAY, CassandraInterpreter.DEFAULT_TCP_NO_DELAY),
            DefaultDriverOption.SOCKET_TCP_NODELAY);
    addIfNotBlank(allOptions, getProperty(CASSANDRA_SOCKET_KEEP_ALIVE),
            DefaultDriverOption.SOCKET_KEEP_ALIVE);
    addIfNotBlank(allOptions, getProperty(CASSANDRA_SOCKET_RECEIVED_BUFFER_SIZE_BYTES),
            DefaultDriverOption.SOCKET_RECEIVE_BUFFER_SIZE);
    addIfNotBlank(allOptions, getProperty(CASSANDRA_SOCKET_SEND_BUFFER_SIZE_BYTES),
            DefaultDriverOption.SOCKET_SEND_BUFFER_SIZE);
    addIfNotBlank(allOptions, getProperty(CASSANDRA_SOCKET_REUSE_ADDRESS),
            DefaultDriverOption.SOCKET_REUSE_ADDRESS);
    addIfNotBlank(allOptions, getProperty(CASSANDRA_SOCKET_SO_LINGER),
            DefaultDriverOption.SOCKET_LINGER_INTERVAL);
    addIfNotBlank(allOptions,
            getProperty(CASSANDRA_QUERY_DEFAULT_IDEMPOTENCE),
            DefaultDriverOption.REQUEST_DEFAULT_IDEMPOTENCE);
    allOptions.put(DefaultDriverOption.REQUEST_CONSISTENCY,
            getProperty(CASSANDRA_QUERY_DEFAULT_CONSISTENCY,
                    CassandraInterpreter.DEFAULT_CONSISTENCY));
    allOptions.put(DefaultDriverOption.REQUEST_SERIAL_CONSISTENCY,
            getProperty(CASSANDRA_QUERY_DEFAULT_SERIAL_CONSISTENCY,
                    CassandraInterpreter.DEFAULT_SERIAL_CONSISTENCY));
    allOptions.put(DefaultDriverOption.REQUEST_PAGE_SIZE,
            getProperty(CASSANDRA_QUERY_DEFAULT_FETCH_SIZE,
                    CassandraInterpreter.DEFAULT_FETCH_SIZE));
    ts = getProperty(CASSANDRA_PROTOCOL_VERSION, DEFAULT_PROTOCOL_VERSION);
    if (!DEFAULT_VALUE.equalsIgnoreCase(ts)) {
      // for compatibility with previous configurations
      if (ts.equals("4") || ts.equals("3")) {
        ts = "V" + ts;
      }
      allOptions.put(DefaultDriverOption.PROTOCOL_VERSION, ts);
    }
    addIfNotBlank(allOptions, getProperty(CASSANDRA_COMPRESSION_PROTOCOL,
            CassandraInterpreter.DEFAULT_COMPRESSION).toLowerCase(),
            DefaultDriverOption.PROTOCOL_COMPRESSION);
    addIfNotBlankOrDefault(allOptions, getProperty(CASSANDRA_RETRY_POLICY, DEFAULT_POLICY),
            DefaultDriverOption.RETRY_POLICY_CLASS);
    addIfNotBlankOrDefault(allOptions,
            getProperty(CASSANDRA_RECONNECTION_POLICY, DEFAULT_POLICY),
            DefaultDriverOption.RECONNECTION_POLICY_CLASS);
    addIfNotBlankOrDefault(allOptions,
            getProperty(CASSANDRA_SPECULATIVE_EXECUTION_POLICY, DEFAULT_POLICY),
            DefaultDriverOption.SPECULATIVE_EXECUTION_POLICY_CLASS);
    allOptions.put(DefaultDriverOption.CONNECTION_POOL_LOCAL_SIZE,
            getProperty(CASSANDRA_POOLING_CONNECTION_PER_HOST_LOCAL,
                    DEFAULT_CONNECTIONS_PER_HOST));
    allOptions.put(DefaultDriverOption.CONNECTION_POOL_REMOTE_SIZE,
            getProperty(CASSANDRA_POOLING_CONNECTION_PER_HOST_REMOTE,
                    DEFAULT_CONNECTIONS_PER_HOST));
    allOptions.put(DefaultDriverOption.CONNECTION_MAX_REQUESTS,
            getProperty(CASSANDRA_POOLING_MAX_REQUESTS_PER_CONNECTION,
            DEFAULT_MAX_REQUEST_PER_CONNECTION));
    allOptions.put(DefaultDriverOption.HEARTBEAT_INTERVAL,
            getProperty(CASSANDRA_POOLING_HEARTBEAT_INTERVAL_SECONDS,
                    DEFAULT_HEARTBEAT_INTERVAL) + SECONDS_STR);
    ts = getProperty(CASSANDRA_POOLING_POOL_TIMEOUT_MILLIS,
            DEFAULT_POOL_TIMEOUT) + MILLISECONDS_STR;
    allOptions.put(DefaultDriverOption.HEARTBEAT_TIMEOUT, ts);
    allOptions.put(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, ts);
    allOptions.put(DefaultDriverOption.LOAD_BALANCING_POLICY_CLASS,
            "DcInferringLoadBalancingPolicy");
    allOptions.put(DefaultDriverOption.RESOLVE_CONTACT_POINTS, "false");
    allOptions.put(DefaultDriverOption.CONTROL_CONNECTION_AGREEMENT_TIMEOUT,
            getProperty(CASSANDRA_MAX_SCHEMA_AGREEMENT_WAIT_SECONDS,
                    DEFAULT_MAX_SCHEMA_AGREEMENT_WAIT_SECONDS) + SECONDS_STR);

    // extract additional options that may override values set by main configuration
    for (String pname: properties.stringPropertyNames()) {
      if (pname.startsWith(DATASTAX_JAVA_DRIVER_PREFIX)) {
        String pvalue = properties.getProperty(pname);
        logger.info("Custom config values: {} = {}", pname, pvalue);
        String shortName = pname.substring(DATASTAX_JAVA_DRIVER_PREFIX.length());
        if (optionMap.containsKey(shortName)) {
          allOptions.put(optionMap.get(shortName), pvalue);
        } else {
          logger.warn("Incorrect option name: {}", pname);
        }
      }
    }

    for (Map.Entry<DriverOption, String> entry: allOptions.entrySet()) {
      configBuilder.withString(entry.getKey(), entry.getValue());
    }

    DriverConfigLoader loader = configBuilder.endProfile().build();
    logger.debug("Config loader is created");

    return loader;
  }

  private static void addIfNotBlank(Map<DriverOption, String> allOptions,
                                    String value,
                                    DefaultDriverOption option) {
    if (!StringUtils.isBlank(value)) {
      allOptions.put(option, value);
    }
  }

  private static void addIfNotBlankOrDefault(Map<DriverOption, String> allOptions,
                                             String value,
                                    DefaultDriverOption option) {
    if (!StringUtils.isBlank(value) && !DEFAULT_VALUE.equalsIgnoreCase(value)) {
      allOptions.put(option, value);
    }
  }

  @Override
  public void close() {
    if (session != null)
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
