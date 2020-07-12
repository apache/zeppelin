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
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Properties;

import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_CLUSTER_NAME;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_COMPRESSION_PROTOCOL;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_CREDENTIALS_PASSWORD;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_CREDENTIALS_USERNAME;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_HOSTS;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_LOAD_BALANCING_POLICY;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_POOLING_HEARTBEAT_INTERVAL_SECONDS;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_POOLING_CONNECTION_PER_HOST_LOCAL;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_POOLING_CONNECTION_PER_HOST_REMOTE;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_POOLING_MAX_REQUESTS_PER_CONNECTION;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_POOLING_POOL_TIMEOUT_MILLIS;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_PORT;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_QUERY_DEFAULT_CONSISTENCY;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_QUERY_DEFAULT_FETCH_SIZE;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_QUERY_DEFAULT_SERIAL_CONSISTENCY;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_RECONNECTION_POLICY;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_RETRY_POLICY;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_SOCKET_CONNECTION_TIMEOUT_MILLIS;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_SOCKET_READ_TIMEOUT_MILLIS;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_SOCKET_TCP_NO_DELAY;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_SPECULATIVE_EXECUTION_POLICY;
import static org.assertj.core.api.Assertions.assertThat;

public class CassandraInterpreterTest { //extends AbstractCassandraUnit4CQLTestCase {
  private static final String ARTISTS_TABLE = "zeppelin.artists";

  private static volatile CassandraInterpreter interpreter;

  private final InterpreterContext intrContext = InterpreterContext.builder()
          .setParagraphTitle("Paragraph1")
          .build();

  @BeforeClass
  public static synchronized void setUp() throws IOException, InterruptedException {
    System.setProperty("cassandra.skip_wait_for_gossip_to_settle", "0");
    System.setProperty("cassandra.load_ring_state", "false");
    System.setProperty("cassandra.initial_token", "0");
    System.setProperty("cassandra.num_tokens", "nil");
    System.setProperty("cassandra.allocate_tokens_for_local_replication_factor", "nil");
    EmbeddedCassandraServerHelper.startEmbeddedCassandra();
    CqlSession session = EmbeddedCassandraServerHelper.getSession();
    new CQLDataLoader(session).load(new ClassPathCQLDataSet("prepare_all.cql", "zeppelin"));

    Properties properties = new Properties();
    properties.setProperty(CASSANDRA_CLUSTER_NAME, EmbeddedCassandraServerHelper.getClusterName());
    properties.setProperty(CASSANDRA_COMPRESSION_PROTOCOL, "NONE");
    properties.setProperty(CASSANDRA_CREDENTIALS_USERNAME, "none");
    properties.setProperty(CASSANDRA_CREDENTIALS_PASSWORD, "none");

    properties.setProperty(CASSANDRA_LOAD_BALANCING_POLICY, "DEFAULT");
    properties.setProperty(CASSANDRA_RETRY_POLICY, "DEFAULT");
    properties.setProperty(CASSANDRA_RECONNECTION_POLICY, "DEFAULT");
    properties.setProperty(CASSANDRA_SPECULATIVE_EXECUTION_POLICY, "DEFAULT");

    properties.setProperty(CASSANDRA_POOLING_CONNECTION_PER_HOST_LOCAL, "2");
    properties.setProperty(CASSANDRA_POOLING_CONNECTION_PER_HOST_REMOTE, "1");
    properties.setProperty(CASSANDRA_POOLING_MAX_REQUESTS_PER_CONNECTION, "1024");

    properties.setProperty(CASSANDRA_POOLING_POOL_TIMEOUT_MILLIS, "5000");
    properties.setProperty(CASSANDRA_POOLING_HEARTBEAT_INTERVAL_SECONDS, "30");

    properties.setProperty(CASSANDRA_QUERY_DEFAULT_CONSISTENCY, "ONE");
    properties.setProperty(CASSANDRA_QUERY_DEFAULT_SERIAL_CONSISTENCY, "SERIAL");
    properties.setProperty(CASSANDRA_QUERY_DEFAULT_FETCH_SIZE, "5000");

    properties.setProperty(CASSANDRA_SOCKET_CONNECTION_TIMEOUT_MILLIS, "5000");
    properties.setProperty(CASSANDRA_SOCKET_READ_TIMEOUT_MILLIS, "12000");
    properties.setProperty(CASSANDRA_SOCKET_TCP_NO_DELAY, "true");

    properties.setProperty(CASSANDRA_HOSTS, EmbeddedCassandraServerHelper.getHost());
    properties.setProperty(CASSANDRA_PORT,
            Integer.toString(EmbeddedCassandraServerHelper.getNativeTransportPort()));
    properties.setProperty("datastax-java-driver.advanced.connection.pool.local.size", "1");
    interpreter = new CassandraInterpreter(properties);
    interpreter.open();
  }

  @AfterClass
  public static void tearDown() {
    interpreter.close();
  }

  @Test
  public void should_create_cluster_and_session_upon_call_to_open(){
    assertThat(interpreter.session).isNotNull();
    assertThat(interpreter.helper).isNotNull();
  }

  @Test
  public void should_set_custom_option() {
    assertThat(interpreter.session).isNotNull();
    DriverExecutionProfile config = interpreter.session.getContext()
            .getConfig().getDefaultProfile();
    assertThat(config.getInt(DefaultDriverOption.CONNECTION_POOL_LOCAL_SIZE, 10))
            .isEqualTo(1);
  }

  @Test
  public void should_interpret_simple_select() {
    //Given

    //When
    final InterpreterResult actual = interpreter.interpret("SELECT * FROM " + ARTISTS_TABLE +
            " LIMIT 10;", intrContext);

    //Then
    assertThat(actual).isNotNull();
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(actual.message().get(0).getData()).isEqualTo("name\tborn\tcountry\tdied\tgender\t" +
        "styles\ttype\n" +
        "Bogdan Raczynski\t1977-01-01\tPoland\tnull\tMale\t" +
        "[Dance, Electro]\tPerson\n" +
        "Krishna Das\t1947-05-31\tUSA\tnull\tMale\t[Unknown]\tPerson\n" +
        "Sheryl Crow\t1962-02-11\tUSA\tnull\tFemale\t" +
        "[Classic, Rock, Country, Blues, Pop, Folk]\tPerson\n" +
        "Doof\t1968-08-31\tUnited Kingdom\tnull\tnull\t[Unknown]\tPerson\n" +
        "House of Large Sizes\t1986-01-01\tUSA\t2003\tnull\t[Unknown]\tGroup\n" +
        "Fanfarlo\t2006-01-01\tUnited Kingdom\tnull\tnull\t" +
        "[Rock, Indie, Pop, Classic]\tGroup\n" +
        "Jeff Beck\t1944-06-24\tUnited Kingdom\tnull\tMale\t" +
        "[Rock, Pop, Classic]\tPerson\n" +
        "Los Paranoias\tnull\tUnknown\tnull\tnull\t[Unknown]\tnull\n" +
        "…And You Will Know Us by the Trail of Dead\t1994-01-01\tUSA\tnull\tnull\t" +
        "[Rock, Pop, Classic]\tGroup\n");
  }

  @Test
  public void should_interpret_select_statement() {
    //Given

    //When
    final InterpreterResult actual = interpreter.interpret("SELECT * FROM " + ARTISTS_TABLE +
            " LIMIT 2;", intrContext);

    //Then
    assertThat(actual).isNotNull();
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(actual.message().get(0).getData())
        .isEqualTo("name\tborn\tcountry\tdied\tgender\tstyles\ttype\n" +
        "Bogdan Raczynski\t1977-01-01\tPoland\tnull\tMale\t" +
        "[Dance, Electro]\tPerson\n" +
        "Krishna Das\t1947-05-31\tUSA\tnull\tMale\t[Unknown]\tPerson\n");
  }

  @Test
  public void should_interpret_select_statement_with_cql_format() {
    //When
    intrContext.getLocalProperties().put("outputFormat", "cql");
    final InterpreterResult actual = interpreter.interpret(
            "SELECT * FROM " + ARTISTS_TABLE + " LIMIT 2;", intrContext);
    intrContext.getLocalProperties().remove("outputFormat");

    //Then
    assertThat(actual).isNotNull();
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(actual.message().get(0).getData())
            .isEqualTo("name\tborn\tcountry\tdied\tgender\tstyles\ttype\n" +
                    "'Bogdan Raczynski'\t'1977-01-01'\t'Poland'\tnull\t'Male'\t" +
                    "['Dance','Electro']\t'Person'\n" +
                    "'Krishna Das'\t'1947-05-31'\t'USA'\tnull\t'Male'\t['Unknown']\t'Person'\n");
  }

  @Test
  public void should_interpret_select_statement_with_formatting_options() {
    //When
    Map<String, String> props = intrContext.getLocalProperties();
    props.put("outputFormat", "human");
    props.put("locale", "de_DE");
    props.put("floatPrecision", "2");
    props.put("doublePrecision", "4");
    props.put("timeFormat", "hh:mma");
    props.put("timestampFormat", "MM/dd/yy HH:mm");
    props.put("dateFormat", "E, d MMM yy");
    props.put("timezone", "Etc/GMT+2");
    String query =
            "select date, time, timestamp, double, float, tuple, udt from zeppelin.test_format;";
    final InterpreterResult actual = interpreter.interpret(query, intrContext);
    props.remove("outputFormat");
    props.remove("locale");
    props.remove("floatPrecision");
    props.remove("doublePrecision");
    props.remove("timeFormat");
    props.remove("timestampFormat");
    props.remove("dateFormat");
    props.remove("timezone");

    //Then
    assertThat(actual).isNotNull();
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    String expected = "date\ttime\ttimestamp\tdouble\tfloat\ttuple\tudt\n" +
            "Di, 29 Jan 19\t04:05AM\t06/16/20 21:59\t10,0153\t20,03\t(1, text, 10)\t" +
            "{id: 1, t: text, lst: [1, 2, 3]}\n";
    assertThat(actual.message().get(0).getData()).isEqualTo(expected);
  }

  @Test
  public void should_interpret_multiple_statements_with_single_line_logged_batch() {
    //Given
    String statements = "CREATE TABLE IF NOT EXISTS zeppelin.albums(\n" +
            "    title text PRIMARY KEY,\n" +
            "    artist text,\n" +
            "    year int\n" +
            ");\n" +
            "BEGIN BATCH" +
            "   INSERT INTO zeppelin.albums(title,artist,year) " +
            "VALUES('The Impossible Dream EP','Carter the Unstoppable Sex Machine',1992);" +
            "   INSERT INTO zeppelin.albums(title,artist,year) " +
            "VALUES('The Way You Are','Tears for Fears',1983);" +
            "   INSERT INTO zeppelin.albums(title,artist,year) " +
            "VALUES('Primitive','Soulfly',2003);" +
            "APPLY BATCH;\n" +
            "SELECT * FROM zeppelin.albums;";
    //When
    final InterpreterResult actual = interpreter.interpret(statements, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(actual.message().get(0).getData()).isEqualTo("title\tartist\tyear\n" +
            "The Impossible Dream EP\tCarter the Unstoppable Sex Machine\t1992\n" +
            "The Way You Are\tTears for Fears\t1983\n" +
            "Primitive\tSoulfly\t2003\n");
  }
    
  @Test
  public void should_throw_statement_not_having_semi_colon() {
    //Given
    String statement = "SELECT * zeppelin.albums";

    //When
    final InterpreterResult actual = interpreter.interpret(statement, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.ERROR);
    assertThat(actual.message().get(0).getData())
            .contains("Error parsing input:\n" +
                    "\t'SELECT * zeppelin.albums'\n" +
                    "Did you forget to add ; (semi-colon) at the end of each CQL statement ?");
  }

  @Test
  public void should_validate_statement() {
    //Given
    String statement = "SELECT * zeppelin.albums;";

    //When
    final InterpreterResult actual = interpreter.interpret(statement, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.ERROR);
    String s = "line 1:9 mismatched input 'zeppelin' expecting K_FROM (SELECT * [zeppelin]...)";
    assertThat(actual.message().get(0).getData())
            .contains(s);
  }

  @Test
  public void should_execute_statement_with_consistency_option() {
    //Given
    String statement = "@consistency=THREE\n" +
            "SELECT * FROM zeppelin.artists LIMIT 1;";

    //When
    final InterpreterResult actual = interpreter.interpret(statement, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.ERROR);
    assertThat(actual.message().get(0).getData())
            .contains("Not enough replicas available for query at consistency THREE (3 required " +
                    "but only 1 alive)");
  }

  @Test
  public void should_execute_statement_with_serial_consistency_option() {
    //Given
    String statement = "@serialConsistency=SERIAL\n" +
            "SELECT * FROM zeppelin.artists LIMIT 1;";

    //When
    final InterpreterResult actual = interpreter.interpret(statement, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
  }

  @Test
  public void should_execute_statement_with_timestamp_option() throws Exception {
    //Given
    String statement1 = "INSERT INTO zeppelin.ts(key,val) VALUES('k','v1');";
    String statement2 = "@timestamp=15\n" +
            "INSERT INTO zeppelin.ts(key,val) VALUES('k','v2');";

    CqlSession session = EmbeddedCassandraServerHelper.getSession();
    // Insert v1 with current timestamp
    interpreter.interpret(statement1, intrContext);
    System.out.println("going to read data from zeppelin.ts;");
    session.execute("SELECT val FROM zeppelin.ts LIMIT 1")
            .forEach(x -> System.out.println("row " + x ));

    Thread.sleep(1);

    //When
    // Insert v2 with past timestamp
    interpreter.interpret(statement2, intrContext);
    System.out.println("going to read data from zeppelin.ts;");
    session.execute("SELECT val FROM zeppelin.ts LIMIT 1")
            .forEach(x -> System.out.println("row " + x ));
    final String actual = session.execute("SELECT val FROM zeppelin.ts LIMIT 1").one()
            .getString("val");

    //Then
    assertThat(actual).isEqualTo("v1");
  }

  @Test
  public void should_execute_statement_with_request_timeout() {
    //Given
    String statement = "@requestTimeOut=10000000\n" +
            "SELECT * FROM zeppelin.artists;";

    //When
    final InterpreterResult actual = interpreter.interpret(statement, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
  }

  @Test
  public void should_execute_prepared_and_bound_statements() {
    //Given
    String queries = "@prepare[ps]=INSERT INTO zeppelin.prepared(key,val) VALUES(?,?)\n" +
            "@prepare[select]=SELECT * FROM zeppelin.prepared WHERE key=:key\n" +
            "@bind[ps]='myKey','myValue'\n" +
            "@bind[select]='myKey'";

    //When
    final InterpreterResult actual = interpreter.interpret(queries, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(actual.message().get(0).getData()).isEqualTo("key\tval\n" +
            "myKey\tmyValue\n");
  }

  @Test
  public void should_execute_bound_statement() {
    //Given
    String queries = "@prepare[users_insert]=INSERT INTO zeppelin.users" +
            "(login,firstname,lastname,addresses,location)" +
            "VALUES(:login,:fn,:ln,:addresses,:loc)\n" +
            "@bind[users_insert]='jdoe','John','DOE'," +
            "{street_number: 3, street_name: 'Beverly Hills Bld', zip_code: 90209," +
            " country: 'USA', extra_info: ['Right on the hills','Next to the post box']," +
            " phone_numbers: {'home': 2016778524, 'office': 2015790847}}," +
            "('USA', 90209, 'Beverly Hills')\n" +
            "SELECT * FROM zeppelin.users WHERE login='jdoe';";
    //When
    final InterpreterResult actual = interpreter.interpret(queries, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(actual.message().get(0).getData()).isEqualTo(
            "login\taddresses\tage\tdeceased\tfirstname\tlast_update\tlastname\tlocation\n" +
                    "jdoe\t" +
                    "{street_number: 3, street_name: Beverly Hills Bld, zip_code: 90209, " +
                    "country: USA, extra_info: [Right on the hills, Next to the post box], " +
                    "phone_numbers: {home: 2016778524, office: 2015790847}}\tnull\t" +
                    "null\t" +
                    "John\t" +
                    "null\t" +
                    "DOE\t" +
                    "(USA, 90209, Beverly Hills)\n");
  }

  @Test
  public void should_exception_when_executing_unknown_bound_statement() {
    //Given
    String queries = "@bind[select_users]='jdoe'";

    //When
    final InterpreterResult actual = interpreter.interpret(queries, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.ERROR);
    assertThat(actual.message().get(0).getData())
            .isEqualTo("The statement 'select_users' can not be bound to values. " +
                    "Are you sure you did prepare it with @prepare[select_users] ?");
  }

  @Test
  public void should_extract_variable_from_statement() {
    //Given
    AngularObjectRegistry angularObjectRegistry = new AngularObjectRegistry("cassandra", null);
    GUI gui = new GUI();
    gui.textbox("login", "hsue");
    gui.textbox("age", "27");
    InterpreterContext intrContext = InterpreterContext.builder()
            .setParagraphTitle("Paragraph1")
            .setAngularObjectRegistry(angularObjectRegistry)
            .setGUI(gui)
            .build();

    String queries = "@prepare[test_insert_with_variable]=" +
            "INSERT INTO zeppelin.users(login,firstname,lastname,age) VALUES(?,?,?,?)\n" +
            "@bind[test_insert_with_variable]='{{login=hsue}}','Helen','SUE',{{age=27}}\n" +
            "SELECT firstname,lastname,age FROM zeppelin.users WHERE login='hsue';";
    //When
    final InterpreterResult actual = interpreter.interpret(queries, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(actual.message().get(0).getData()).isEqualTo("firstname\tlastname\tage\n" +
            "Helen\tSUE\t27\n");
  }

  @Test
  public void should_just_prepare_statement() {
    //Given
    String queries = "@prepare[just_prepare]=SELECT name,country,styles " +
            "FROM zeppelin.artists LIMIT 3";
    final String expected = reformatHtml(
            readTestResource("/scalate/NoResult.html"));

    //When
    final InterpreterResult actual = interpreter.interpret(queries, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(reformatHtml(actual.message().get(0).getData())).isEqualTo(expected);
  }

  @Test
  public void should_execute_bound_statement_with_no_bound_value() {
    //Given
    String queries = "@prepare[select_no_bound_value]=SELECT name,country,styles " +
            "FROM zeppelin.artists LIMIT 3\n" +
            "@bind[select_no_bound_value]";

    //When
    final InterpreterResult actual = interpreter.interpret(queries, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(actual.message().get(0).getData()).isEqualTo("name\tcountry\tstyles\n" +
            "Bogdan Raczynski\tPoland\t[Dance, Electro]\n" +
            "Krishna Das\tUSA\t[Unknown]\n" +
            "Sheryl Crow\tUSA\t[Classic, Rock, Country, Blues, Pop, Folk]\n");
  }

  @Test
  public void should_parse_date_value() {
    //Given
    String queries = "@prepare[parse_date]=INSERT INTO zeppelin.users(login,last_update) " +
            "VALUES(?,?)\n" +
            "@bind[parse_date]='last_update','2015-07-30 12:00:01'\n" +
            "SELECT last_update FROM zeppelin.users WHERE login='last_update';";
    //When
    final InterpreterResult actual = interpreter.interpret(queries, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(actual.message().get(0).getData()).contains("last_update\n2015-07-30T12:00:01.000Z");
  }

  @Test
  public void should_bind_null_value() {
    //Given
    String queries = "@prepare[bind_null]=INSERT INTO zeppelin.users(login,firstname,lastname) " +
            "VALUES(?,?,?)\n" +
            "@bind[bind_null]='bind_null',null,'NULL'\n" +
            "SELECT firstname,lastname FROM zeppelin.users WHERE login='bind_null';";
    //When
    final InterpreterResult actual = interpreter.interpret(queries, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(actual.message().get(0).getData()).isEqualTo("firstname\tlastname\n" +
            "null\tNULL\n");
  }

  @Test
  public void should_bind_boolean_value() {
    //Given
    String queries = "@prepare[bind_boolean]=INSERT INTO zeppelin.users(login,deceased) " +
            "VALUES(?,?)\n" +
            "@bind[bind_boolean]='bind_bool',false\n" +
            "SELECT login,deceased FROM zeppelin.users WHERE login='bind_bool';";
    //When
    final InterpreterResult actual = interpreter.interpret(queries, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(actual.message().get(0).getData()).isEqualTo("login\tdeceased\n" +
            "bind_bool\tfalse\n");
  }

  @Test
  public void should_fail_when_executing_a_removed_prepared_statement() {
    //Given
    String prepareFirst = "@prepare[to_be_removed]=INSERT INTO zeppelin.users(login,deceased) " +
            "VALUES(?,?)";
    interpreter.interpret(prepareFirst, intrContext);
    String removePrepared = "@remove_prepare[to_be_removed]\n" +
            "@bind[to_be_removed]='bind_bool'";

    //When
    final InterpreterResult actual = interpreter.interpret(removePrepared, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.ERROR);
    assertThat(actual.message().get(0).getData()).isEqualTo("The statement 'to_be_removed' can " +
            "not be bound to values. Are you sure you did prepare it with " +
            "@prepare[to_be_removed] ?");
  }

  @Test
  public void should_display_statistics_for_non_select_statement() {
    //Given
    String query = "USE zeppelin;\nCREATE TABLE IF NOT EXISTS no_select(id int PRIMARY KEY);";
    final String rawResult = reformatHtml(readTestResource(
            "/scalate/NoResultWithExecutionInfo.html"));

    //When
    final InterpreterResult actual = interpreter.interpret(query, intrContext);
    final int port = EmbeddedCassandraServerHelper.getNativeTransportPort();
    final String address = EmbeddedCassandraServerHelper.getHost();
    //Then
    final String expected = rawResult.replaceAll("TRIED_HOSTS", address + ":" + port)
            .replaceAll("QUERIED_HOSTS", address + ":" + port);

    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(reformatHtml(actual.message().get(0).getData())).isEqualTo(expected);
  }

  @Test
  public void should_error_and_display_stack_trace() {
    //Given
    String query = "@consistency=THREE\n" +
            "SELECT * FROM zeppelin.users LIMIT 3;";
    //When
    final InterpreterResult actual = interpreter.interpret(query, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.ERROR);
    assertThat(actual.message().get(0).getData())
            .contains("All 1 node(s) tried for the query failed");
  }

  @Test
  public void should_describe_cluster() {
    //Given

    String query = "DESCRIBE CLUSTER;";
    final String expected = reformatHtml(
            readTestResource("/scalate/DescribeCluster.html"));

    //When
    final InterpreterResult actual = interpreter.interpret(query, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(reformatHtml(actual.message().get(0).getData())).isEqualTo(expected);
  }

  @Test
  public void should_describe_keyspaces() {
    //Given
    String query = "DESCRIBE KEYSPACES;";
    final String expected = reformatHtml(
            readTestResource("/scalate/DescribeKeyspaces.html"));

    //When
    final InterpreterResult actual = interpreter.interpret(query, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(reformatHtml(actual.message().get(0).getData())).isEqualTo(expected);
  }

  @Test
  public void should_describe_keyspace() {
    //Given
    String query = "DESCRIBE KEYSPACE live_data;";
    final String expected = reformatHtml(
            readTestResource("/scalate/DescribeKeyspace_live_data.html"));

    //When
    final InterpreterResult actual = interpreter.interpret(query, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(reformatHtml(actual.message().get(0).getData())).isEqualTo(expected);
  }

  @Test
  @Ignore
  //TODO(n.a.) activate test when using Java 8 and C* 3.x
  public void should_describe_function() throws Exception {
    //Given
    Properties properties = new Properties();
    properties.setProperty(CASSANDRA_HOSTS, "127.0.0.1");
    properties.setProperty(CASSANDRA_PORT,  "9042");
    Interpreter interpreter = new CassandraInterpreter(properties);
    interpreter.open();

    String createFunction = "CREATE FUNCTION zeppelin.maxof(val1 int,val2 int) " +
            "RETURNS NULL ON NULL INPUT " +
            "RETURNS int " +
            "LANGUAGE java " +
            "AS $$" +
            "    return Math.max(val1, val2);\n" +
            "$$;";
    interpreter.interpret(createFunction, intrContext);
    String query = "DESCRIBE FUNCTION zeppelin.maxOf;";

    //When
    final InterpreterResult actual = interpreter.interpret(query, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(actual.message()).isEqualTo("xxxxx");
  }

  @Test
  @Ignore
  //TODO(n.a.) activate test when using Java 8 and C* 3.x
  public void should_describe_aggregate() throws Exception {
    //Given
    Properties properties = new Properties();
    properties.setProperty(CASSANDRA_HOSTS, "127.0.0.1");
    properties.setProperty(CASSANDRA_PORT,  "9042");
    Interpreter interpreter = new CassandraInterpreter(properties);
    interpreter.open();

    final String query = "DESCRIBE AGGREGATES;";

    //When
    final InterpreterResult actual = interpreter.interpret(query, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
  }

  @Test
  @Ignore
  //TODO(n.a.) activate test when using Java 8 and C* 3.x
  public void should_describe_materialized_view() throws Exception {
    //Given
    Properties properties = new Properties();
    properties.setProperty(CASSANDRA_HOSTS, "127.0.0.1");
    properties.setProperty(CASSANDRA_PORT,  "9042");
    Interpreter interpreter = new CassandraInterpreter(properties);
    interpreter.open();

    final String query = "DESCRIBE MATERIALIZED VIEWS;";

    //When
    final InterpreterResult actual = interpreter.interpret(query, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
  }

  @Test
  public void should_describe_table() {
    //Given
    String query = "DESCRIBE TABLE live_data.complex_table;";
    final String expected = reformatHtml(
            readTestResource("/scalate/DescribeTable_live_data_complex_table.html"));

    //When
    final InterpreterResult actual = interpreter.interpret(query, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(reformatHtml(actual.message().get(0).getData())).isEqualTo(expected);
  }

  @Test
  public void should_describe_udt() {
    //Given
    String query = "DESCRIBE TYPE live_data.address;";
    final String expected = reformatHtml(
            readTestResource("/scalate/DescribeType_live_data_address.html"));

    //When
    final InterpreterResult actual = interpreter.interpret(query, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(reformatHtml(actual.message().get(0).getData())).isEqualTo(expected);
  }

  @Test
  public void should_describe_udt_withing_logged_in_keyspace() {
    //Given
    String query = "USE live_data;\n" +
            "DESCRIBE TYPE address;";
    final String expected = reformatHtml(readTestResource(
            "/scalate/DescribeType_live_data_address_within_current_keyspace.html"));

    //When
    final InterpreterResult actual = interpreter.interpret(query, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(reformatHtml(actual.message().get(0).getData())).isEqualTo(expected);
  }

  @Test
  public void should_describe_all_tables() {
    //Given
    String query = "DESCRIBE TABLES;";
    final String expected = reformatHtml(readTestResource(
            "/scalate/DescribeTables.html"));

    //When
    final InterpreterResult actual = interpreter.interpret(query, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(reformatHtml(actual.message().get(0).getData())).isEqualTo(expected);
  }

  @Test
  public void should_describe_all_udts() {
    //Given
    String query = "DESCRIBE TYPES;";
    final String expected = reformatHtml(readTestResource(
            "/scalate/DescribeTypes.html"));

    //When
    final InterpreterResult actual = interpreter.interpret(query, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(reformatHtml(actual.message().get(0).getData())).isEqualTo(expected);
  }


  @Test
  public void should_error_describing_non_existing_table() {
    //Given
    String query = "USE system;\n" +
            "DESCRIBE TABLE complex_table;";

    //When
    final InterpreterResult actual = interpreter.interpret(query, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.ERROR);
    assertThat(actual.message().get(0).getData())
            .contains("Cannot find table system.complex_table");
  }

  @Test
  public void should_error_describing_non_existing_udt() {
    //Given
    String query = "USE system;\n" +
            "DESCRIBE TYPE address;";

    //When
    final InterpreterResult actual = interpreter.interpret(query, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.ERROR);
    assertThat(actual.message().get(0).getData()).contains("Cannot find type system.address");
  }

  @Test
  public void should_show_help() {
    //Given
    String query = "HELP;";
    final String expected = reformatHtml(readTestResource("/scalate/Help.html"));

    //When
    final InterpreterResult actual = interpreter.interpret(query, intrContext);

    //Then
    assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    assertThat(reformatHtml(actual.message().get(0).getData())).contains(expected);
  }

  private static String reformatHtml(String rawHtml) {
    return  rawHtml
            .replaceAll("\\s*\n\\s*", "")
            .replaceAll(">\\s+<", "><")
            .replaceAll("(?s)data-target=\"#[a-f0-9-]+(?:_asCQL|_indices_asCQL)?\"", "")
            .replaceAll("(?s)id=\"[a-f0-9-]+(?:_asCQL|_indices_asCQL)?\"", "")
            .replaceAll("AND memtable_flush_period_in_ms = 0", "")
            .trim();
  }

  private static String readTestResource(String testResource) {
    StringBuilder builder = new StringBuilder();
    InputStream stream = testResource.getClass().getResourceAsStream(testResource);

    try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
      String line;
      while ((line = br.readLine()) != null) {
        builder.append(line).append("\n");
      }
    } catch (Exception ex) {
      throw  new RuntimeException(ex);
    }

    return builder.toString();
  }
}
