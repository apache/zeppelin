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

import static com.datastax.driver.core.ProtocolOptions.DEFAULT_MAX_SCHEMA_AGREEMENT_WAIT_SECONDS;
import static com.google.common.collect.FluentIterable.from;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_CLUSTER_NAME;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_COMPRESSION_PROTOCOL;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_CREDENTIALS_PASSWORD;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_CREDENTIALS_USERNAME;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_HOSTS;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_PORT;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.CASSANDRA_PROTOCOL_VERSION;
import static org.apache.zeppelin.cassandra.CassandraInterpreter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.Session;

import info.archinnov.achilles.embedded.CassandraEmbeddedServerBuilder;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

@RunWith(MockitoJUnitRunner.class)
public class CassandraInterpreterTest {

    private static final String ARTISTS_TABLE = "zeppelin.artists";

    public static Session session = CassandraEmbeddedServerBuilder
        .noEntityPackages()
        .withKeyspaceName("zeppelin")
        .withScript("prepare_schema.cql")
        .withScript("prepare_data.cql")
        .withProtocolVersion(ProtocolVersion.V3)
        .buildNativeSessionOnly();

    private static CassandraInterpreter interpreter;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private InterpreterContext intrContext;

    @BeforeClass
    public static void setUp() {
        Properties properties = new Properties();
        final Cluster cluster = session.getCluster();

        properties.setProperty(CASSANDRA_CLUSTER_NAME, cluster.getClusterName());
        properties.setProperty(CASSANDRA_COMPRESSION_PROTOCOL, "NONE");
        properties.setProperty(CASSANDRA_CREDENTIALS_USERNAME, "none");
        properties.setProperty(CASSANDRA_CREDENTIALS_PASSWORD, "none");

        properties.setProperty(CASSANDRA_PROTOCOL_VERSION, "3");
        properties.setProperty(CASSANDRA_LOAD_BALANCING_POLICY, "DEFAULT");
        properties.setProperty(CASSANDRA_RETRY_POLICY, "DEFAULT");
        properties.setProperty(CASSANDRA_RECONNECTION_POLICY, "DEFAULT");
        properties.setProperty(CASSANDRA_SPECULATIVE_EXECUTION_POLICY, "DEFAULT");

        properties.setProperty(CASSANDRA_MAX_SCHEMA_AGREEMENT_WAIT_SECONDS,
                DEFAULT_MAX_SCHEMA_AGREEMENT_WAIT_SECONDS + "");

        properties.setProperty(CASSANDRA_POOLING_NEW_CONNECTION_THRESHOLD_LOCAL, "100");
        properties.setProperty(CASSANDRA_POOLING_NEW_CONNECTION_THRESHOLD_REMOTE, "100");
        properties.setProperty(CASSANDRA_POOLING_CORE_CONNECTION_PER_HOST_LOCAL, "2");
        properties.setProperty(CASSANDRA_POOLING_CORE_CONNECTION_PER_HOST_REMOTE, "1");
        properties.setProperty(CASSANDRA_POOLING_MAX_CONNECTION_PER_HOST_LOCAL, "8");
        properties.setProperty(CASSANDRA_POOLING_MAX_CONNECTION_PER_HOST_REMOTE, "2");
        properties.setProperty(CASSANDRA_POOLING_MAX_REQUESTS_PER_CONNECTION_LOCAL, "1024");
        properties.setProperty(CASSANDRA_POOLING_MAX_REQUESTS_PER_CONNECTION_REMOTE, "256");

        properties.setProperty(CASSANDRA_POOLING_IDLE_TIMEOUT_SECONDS, "120");
        properties.setProperty(CASSANDRA_POOLING_POOL_TIMEOUT_MILLIS, "5000");
        properties.setProperty(CASSANDRA_POOLING_HEARTBEAT_INTERVAL_SECONDS, "30");

        properties.setProperty(CASSANDRA_QUERY_DEFAULT_CONSISTENCY, "ONE");
        properties.setProperty(CASSANDRA_QUERY_DEFAULT_SERIAL_CONSISTENCY, "SERIAL");
        properties.setProperty(CASSANDRA_QUERY_DEFAULT_FETCH_SIZE, "5000");

        properties.setProperty(CASSANDRA_SOCKET_CONNECTION_TIMEOUT_MILLIS, "5000");
        properties.setProperty(CASSANDRA_SOCKET_READ_TIMEOUT_MILLIS, "12000");
        properties.setProperty(CASSANDRA_SOCKET_TCP_NO_DELAY, "true");

        properties.setProperty(CASSANDRA_HOSTS, from(cluster.getMetadata().getAllHosts()).first().get().getAddress().getHostAddress());
        properties.setProperty(CASSANDRA_PORT, cluster.getConfiguration().getProtocolOptions().getPort()+"");
        interpreter = new CassandraInterpreter(properties);
        interpreter.open();
    }

    @AfterClass
    public static void tearDown() {
        interpreter.close();
    }

    @Before
    public void prepareContext() {
        when(intrContext.getParagraphTitle()).thenReturn("Paragraph1");
    }

    @Test
    public void should_create_cluster_and_session_upon_call_to_open() throws Exception {
        assertThat(interpreter.cluster).isNotNull();
        assertThat(interpreter.cluster.getClusterName()).isEqualTo(session.getCluster().getClusterName());
        assertThat(interpreter.session).isNotNull();
        assertThat(interpreter.helper).isNotNull();
    }

    @Test
    public void should_interpret_simple_select() throws Exception {
        //Given

        //When
        final InterpreterResult actual = interpreter.interpret("SELECT * FROM " + ARTISTS_TABLE + " LIMIT 10;", intrContext);

        //Then
        assertThat(actual).isNotNull();
        assertThat(actual.code()).isEqualTo(Code.SUCCESS);
        assertThat(actual.message().get(0).getData()).isEqualTo("name\tborn\tcountry\tdied\tgender\tstyles\ttype\n" +
                "Bogdan Raczynski\t1977-01-01\tPoland\tnull\tMale\t[Dance, Electro]\tPerson\n" +
                "Krishna Das\t1947-05-31\tUSA\tnull\tMale\t[Unknown]\tPerson\n" +
                "Sheryl Crow\t1962-02-11\tUSA\tnull\tFemale\t[Classic, Rock, Country, Blues, Pop, Folk]\tPerson\n" +
                "Doof\t1968-08-31\tUnited Kingdom\tnull\tnull\t[Unknown]\tPerson\n" +
                "House of Large Sizes\t1986-01-01\tUSA\t2003\tnull\t[Unknown]\tGroup\n" +
                "Fanfarlo\t2006-01-01\tUnited Kingdom\tnull\tnull\t[Rock, Indie, Pop, Classic]\tGroup\n" +
                "Jeff Beck\t1944-06-24\tUnited Kingdom\tnull\tMale\t[Rock, Pop, Classic]\tPerson\n" +
                "Los Paranoias\tnull\tUnknown\tnull\tnull\t[Unknown]\tnull\n" +
                "…And You Will Know Us by the Trail of Dead\t1994-01-01\tUSA\tnull\tnull\t[Rock, Pop, Classic]\tGroup\n");

    }

    @Test
    public void should_interpret_select_statement() throws Exception {
        //Given

        //When
        final InterpreterResult actual = interpreter.interpret("SELECT * FROM " + ARTISTS_TABLE + " LIMIT 2;", intrContext);

        //Then
        assertThat(actual).isNotNull();
        assertThat(actual.code()).isEqualTo(Code.SUCCESS);
        assertThat(actual.message().get(0).getData()).isEqualTo("name\tborn\tcountry\tdied\tgender\tstyles\ttype\n" +
                "Bogdan Raczynski\t1977-01-01\tPoland\tnull\tMale\t[Dance, Electro]\tPerson\n" +
                "Krishna Das\t1947-05-31\tUSA\tnull\tMale\t[Unknown]\tPerson\n");

    }

    @Test
    public void should_interpret_multiple_statements_with_single_line_logged_batch() throws Exception {
        //Given
        String statements = "CREATE TABLE IF NOT EXISTS zeppelin.albums(\n" +
                "    title text PRIMARY KEY,\n" +
                "    artist text,\n" +
                "    year int\n" +
                ");\n" +
                "BEGIN BATCH"+
                "   INSERT INTO zeppelin.albums(title,artist,year) VALUES('The Impossible Dream EP','Carter the Unstoppable Sex Machine',1992);"+
                "   INSERT INTO zeppelin.albums(title,artist,year) VALUES('The Way You Are','Tears for Fears',1983);"+
                "   INSERT INTO zeppelin.albums(title,artist,year) VALUES('Primitive','Soulfly',2003);"+
                "APPLY BATCH;\n"+
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
    public void should_throw_statement_not_having_semi_colon() throws Exception {
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
    public void should_validate_statement() throws Exception {
        //Given
        String statement = "SELECT * zeppelin.albums;";

        //When
        final InterpreterResult actual = interpreter.interpret(statement, intrContext);

        //Then
        assertThat(actual.code()).isEqualTo(Code.ERROR);
        assertThat(actual.message().get(0).getData()).contains("line 1:9 missing K_FROM at 'zeppelin' (SELECT * [zeppelin]....)");
    }

    @Test
    public void should_execute_statement_with_consistency_option() throws Exception {
        //Given
        String statement = "@consistency=THREE\n" +
                "SELECT * FROM zeppelin.artists LIMIT 1;";

        //When
        final InterpreterResult actual = interpreter.interpret(statement, intrContext);

        //Then
        assertThat(actual.code()).isEqualTo(Code.ERROR);
        assertThat(actual.message().get(0).getData())
                .contains("Not enough replicas available for query at consistency THREE (3 required but only 1 alive)");
    }

    @Test
    public void should_execute_statement_with_serial_consistency_option() throws Exception {
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

        // Insert v1 with current timestamp
        interpreter.interpret(statement1, intrContext);

        Thread.sleep(1);

        //When
        // Insert v2 with past timestamp
        interpreter.interpret(statement2, intrContext);
        final String actual = session.execute("SELECT * FROM zeppelin.ts LIMIT 1").one().getString("val");

        //Then
        assertThat(actual).isEqualTo("v1");
    }

    @Test
    public void should_execute_statement_with_retry_policy() throws Exception {
        //Given
        String statement = "@retryPolicy=" + interpreter.LOGGING_DOWNGRADING_RETRY + "\n" +
                "@consistency=THREE\n" +
                "SELECT * FROM zeppelin.artists LIMIT 1;";

        //When
        final InterpreterResult actual = interpreter.interpret(statement, intrContext);

        //Then
        assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    }

    @Test
    public void should_execute_statement_with_request_timeout() throws Exception {
        //Given
        String statement = "@requestTimeOut=10000000\n" +
                "SELECT * FROM zeppelin.artists;";

        //When
        final InterpreterResult actual = interpreter.interpret(statement, intrContext);

        //Then
        assertThat(actual.code()).isEqualTo(Code.SUCCESS);
    }

    @Test
    public void should_execute_prepared_and_bound_statements() throws Exception {
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
    public void should_execute_bound_statement() throws Exception {
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
                        "{street_number:3,street_name:'Beverly Hills Bld',zip_code:90209," +
                        "country:'USA',extra_info:['Right on the hills','Next to the post box']," +
                        "phone_numbers:{'office':2015790847,'home':2016778524}}\tnull\t" +
                        "null\t" +
                        "John\t" +
                        "null\t" +
                        "DOE\t" +
                        "('USA',90209,'Beverly Hills')\n");
    }

    @Test
    public void should_exception_when_executing_unknown_bound_statement() throws Exception {
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
    public void should_extract_variable_from_statement() throws Exception {
        //Given
        AngularObjectRegistry angularObjectRegistry = new AngularObjectRegistry("cassandra", null);
        when(intrContext.getAngularObjectRegistry()).thenReturn(angularObjectRegistry);
        when(intrContext.getGui().input("login", "hsue")).thenReturn("hsue");
        when(intrContext.getGui().input("age", "27")).thenReturn("27");

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
    public void should_just_prepare_statement() throws Exception {
        //Given
        String queries = "@prepare[just_prepare]=SELECT name,country,styles FROM zeppelin.artists LIMIT 3";
        final String expected = reformatHtml(
                readTestResource("/scalate/NoResult.html"));

        //When
        final InterpreterResult actual = interpreter.interpret(queries, intrContext);

        //Then
        assertThat(actual.code()).isEqualTo(Code.SUCCESS);
        assertThat(reformatHtml(actual.message().get(0).getData())).isEqualTo(expected);
    }

    @Test
    public void should_execute_bound_statement_with_no_bound_value() throws Exception {
        //Given
        String queries = "@prepare[select_no_bound_value]=SELECT name,country,styles FROM zeppelin.artists LIMIT 3\n" +
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
    public void should_parse_date_value() throws Exception {
        //Given
        String queries = "@prepare[parse_date]=INSERT INTO zeppelin.users(login,last_update) VALUES(?,?)\n" +
                "@bind[parse_date]='last_update','2015-07-30 12:00:01'\n" +
                "SELECT last_update FROM zeppelin.users WHERE login='last_update';";
        //When
        final InterpreterResult actual = interpreter.interpret(queries, intrContext);

        //Then
        assertThat(actual.code()).isEqualTo(Code.SUCCESS);
        assertThat(actual.message().get(0).getData()).contains("last_update\n" +
                "Thu Jul 30 12:00:01");
    }

    @Test
    public void should_bind_null_value() throws Exception {
        //Given
        String queries = "@prepare[bind_null]=INSERT INTO zeppelin.users(login,firstname,lastname) VALUES(?,?,?)\n" +
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
    public void should_bind_boolean_value() throws Exception {
        //Given
        String queries = "@prepare[bind_boolean]=INSERT INTO zeppelin.users(login,deceased) VALUES(?,?)\n" +
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
    public void should_fail_when_executing_a_removed_prepared_statement() throws Exception {
        //Given
        String prepare_first = "@prepare[to_be_removed]=INSERT INTO zeppelin.users(login,deceased) VALUES(?,?)";
        interpreter.interpret(prepare_first, intrContext);
        String remove_prepared = "@remove_prepare[to_be_removed]\n" +
                "@bind[to_be_removed]='bind_bool'";

        //When
        final InterpreterResult actual = interpreter.interpret(remove_prepared, intrContext);

        //Then
        assertThat(actual.code()).isEqualTo(Code.ERROR);
        assertThat(actual.message().get(0).getData()).isEqualTo("The statement 'to_be_removed' can not be bound to values. " +
                "Are you sure you did prepare it with @prepare[to_be_removed] ?");
    }

    @Test
    public void should_display_statistics_for_non_select_statement() throws Exception {
        //Given
        String query = "USE zeppelin;\nCREATE TABLE IF NOT EXISTS no_select(id int PRIMARY KEY);";
        final String rawResult = reformatHtml(readTestResource("/scalate/NoResultWithExecutionInfo.html"));

        //When
        final InterpreterResult actual = interpreter.interpret(query, intrContext);
        final Cluster cluster = session.getCluster();
        final int port = cluster.getConfiguration().getProtocolOptions().getPort();
        final String address = cluster.getMetadata().getAllHosts().iterator().next()
                .getAddress().getHostAddress()
                .replaceAll("/", "").replaceAll("\\[", "").replaceAll("\\]","");
        //Then
        final String expected = rawResult.replaceAll("TRIED_HOSTS", address+":"+port)
                .replaceAll("QUERIED_HOSTS", address +":"+port);


        assertThat(actual.code()).isEqualTo(Code.SUCCESS);
        assertThat(reformatHtml(actual.message().get(0).getData())).isEqualTo(expected);
    }

    @Test
    public void should_error_and_display_stack_trace() throws Exception {
        //Given
        String query = "@consistency=THREE\n" +
                "SELECT * FROM zeppelin.users LIMIT 3;";
        //When
        final InterpreterResult actual = interpreter.interpret(query, intrContext);

        //Then
        assertThat(actual.code()).isEqualTo(Code.ERROR);
        assertThat(actual.message().get(0).getData()).contains("All host(s) tried for query failed");
    }

    @Test
    public void should_describe_cluster() throws Exception {
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
    public void should_describe_keyspaces() throws Exception {
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
         public void should_describe_keyspace() throws Exception {
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
    //TODO activate test when using Java 8 and C* 3.x
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
    //TODO activate test when using Java 8 and C* 3.x
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
    //TODO activate test when using Java 8 and C* 3.x
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
    public void should_describe_table() throws Exception {
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
    public void should_describe_udt() throws Exception {
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
    public void should_describe_udt_withing_logged_in_keyspace() throws Exception {
        //Given
        String query = "USE live_data;\n" +
                "DESCRIBE TYPE address;";
        final String expected = reformatHtml(
                readTestResource("/scalate/DescribeType_live_data_address_within_current_keyspace.html"));

        //When
        final InterpreterResult actual = interpreter.interpret(query, intrContext);

        //Then
        assertThat(actual.code()).isEqualTo(Code.SUCCESS);

        assertThat(reformatHtml(actual.message().get(0).getData())).isEqualTo(expected);
    }

    @Test
    public void should_error_describing_non_existing_table() throws Exception {
        //Given
        String query = "USE system;\n" +
                "DESCRIBE TABLE complex_table;";

        //When
        final InterpreterResult actual = interpreter.interpret(query, intrContext);

        //Then
        assertThat(actual.code()).isEqualTo(Code.ERROR);
        assertThat(actual.message().get(0).getData()).contains("Cannot find table system.complex_table");
    }

    @Test
    public void should_error_describing_non_existing_udt() throws Exception {
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
    public void should_show_help() throws Exception {
        //Given
        String query = "HELP;";
        final String expected = reformatHtml(readTestResource("/scalate/Help.html"));

        //When
        final InterpreterResult actual = interpreter.interpret(query, intrContext);

        //Then
        assertThat(actual.code()).isEqualTo(Code.SUCCESS);
        assertThat(reformatHtml(actual.message().get(0).getData())).isEqualTo(expected);
    }

    private static String reformatHtml(String rawHtml) {
        return  rawHtml
                .replaceAll("\\s*\n\\s*","")
                .replaceAll(">\\s+<", "><")
                .replaceAll("(?s)data-target=\"#[a-f0-9-]+(?:_asCQL|_indices_asCQL)?\"", "")
                .replaceAll("(?s)id=\"[a-f0-9-]+(?:_asCQL|_indices_asCQL)?\"", "")
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