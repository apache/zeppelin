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

import static com.datastax.driver.core.BatchStatement.Type.UNLOGGED;
import static com.datastax.driver.core.ConsistencyLevel.ALL;
import static com.datastax.driver.core.ConsistencyLevel.LOCAL_SERIAL;
import static com.datastax.driver.core.ConsistencyLevel.ONE;
import static com.datastax.driver.core.ConsistencyLevel.QUORUM;
import static com.datastax.driver.core.ConsistencyLevel.SERIAL;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.Input.ParamOption;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import scala.Option;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.zeppelin.cassandra.TextBlockHierarchy.*;

@RunWith(MockitoJUnitRunner.class)
public class InterpreterLogicTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private InterpreterContext intrContext;

    @Mock
    private Session session;

    final InterpreterLogic helper = new InterpreterLogic(session);

    @Captor
    ArgumentCaptor<ParamOption[]> optionsCaptor;

    @Test
    public void should_parse_input_string_block() throws Exception {
        //Given
        String input = "SELECT * FROM users LIMIT 10;";

        //When
        final List<AnyBlock> anyBlocks = this.<AnyBlock>toJavaList(helper.parseInput(input));

        //Then
        assertThat(anyBlocks).hasSize(1);
        assertThat(anyBlocks.get(0)).isInstanceOf(SimpleStm.class);
    }

    @Test
    public void should_exception_while_parsing_input() throws Exception {
        //Given
        String input = "SELECT * FROM users LIMIT 10";

        //When
        expectedException.expect(InterpreterException.class);
        expectedException.expectMessage("Error parsing input:\n" +
                "\t'SELECT * FROM users LIMIT 10'\n" +
                "Did you forget to add ; (semi-colon) at the end of each CQL statement ?");

        helper.parseInput(input);
    }

    @Test
    public void should_extract_variable_and_default_value() throws Exception {
        //Given
        when(intrContext.getGui().input("table", "zeppelin.demo")).thenReturn("zeppelin.demo");
        when(intrContext.getGui().input("id", "'John'")).thenReturn("'John'");

        //When
        final String actual = helper.maybeExtractVariables("SELECT * FROM {{table=zeppelin.demo}} WHERE id={{id='John'}}", intrContext);

        //Then
        assertThat(actual).isEqualTo("SELECT * FROM zeppelin.demo WHERE id='John'");
    }

    @Test
    public void should_extract_variable_and_choices() throws Exception {
        //Given
        when(intrContext.getGui().select(eq("name"), eq("'Paul'"), optionsCaptor.capture())).thenReturn("'Jack'");

        //When
        final String actual = helper.maybeExtractVariables("SELECT * FROM zeppelin.artists WHERE name={{name='Paul'|'Jack'|'Smith'}}", intrContext);

        //Then
        assertThat(actual).isEqualTo("SELECT * FROM zeppelin.artists WHERE name='Jack'");
        final List<ParamOption> paramOptions = asList(optionsCaptor.getValue());
        assertThat(paramOptions.get(0).getValue()).isEqualTo("'Paul'");
        assertThat(paramOptions.get(1).getValue()).isEqualTo("'Jack'");
        assertThat(paramOptions.get(2).getValue()).isEqualTo("'Smith'");
    }

    @Test
    public void should_extract_no_variable() throws Exception {
        //Given
        GUI gui = mock(GUI.class);
        when(intrContext.getGui()).thenReturn(gui);

        //When
        final String actual = helper.maybeExtractVariables("SELECT * FROM zeppelin.demo", intrContext);

        //Then
        verifyZeroInteractions(gui);
        assertThat(actual).isEqualTo("SELECT * FROM zeppelin.demo");
    }

    @Test
    public void should_error_if_incorrect_variable_definition() throws Exception {
        //Given

        //When
        expectedException.expect(ParsingException.class);
        expectedException.expectMessage("Invalid bound variable definition for '{{table?zeppelin.demo}}' in 'SELECT * FROM {{table?zeppelin.demo}} WHERE id={{id='John'}}'. It should be of form 'variable=defaultValue'");

        //Then
        helper.maybeExtractVariables("SELECT * FROM {{table?zeppelin.demo}} WHERE id={{id='John'}}", intrContext);
    }


    @Test
    public void should_extract_consistency_option() throws Exception {
        //Given
        List<QueryParameters> options = Arrays.<QueryParameters>asList(new Consistency(ALL), new Consistency(ONE));

        //When
        final CassandraQueryOptions actual = helper.extractQueryOptions(toScalaList(options));

        //Then
        assertThat(actual.consistency().get()).isEqualTo(ALL);
    }


    @Test
    public void should_extract_serial_consistency_option() throws Exception {
        //Given
        List<QueryParameters> options = Arrays.<QueryParameters>asList(new SerialConsistency(SERIAL), new SerialConsistency(LOCAL_SERIAL));

        //When
        final CassandraQueryOptions actual = helper.extractQueryOptions(toScalaList(options));

        //Then
        assertThat(actual.serialConsistency().get()).isEqualTo(SERIAL);
    }

    @Test
    public void should_extract_timestamp_option() throws Exception {
        //Given
        List<QueryParameters> options = Arrays.<QueryParameters>asList(new Timestamp(123L), new Timestamp(456L));

        //When
        final CassandraQueryOptions actual = helper.extractQueryOptions(toScalaList(options));

        //Then
        assertThat(actual.timestamp().get()).isEqualTo(123L);
    }

    @Test
    public void should_extract_retry_policy_option() throws Exception {
        //Given
        List<QueryParameters> options = Arrays.<QueryParameters>asList(DowngradingRetryPolicy$.MODULE$, LoggingDefaultRetryPolicy$.MODULE$);

        //When
        final CassandraQueryOptions actual = helper.extractQueryOptions(toScalaList(options));

        //Then
        assertThat(actual.retryPolicy().get()).isSameAs(DowngradingRetryPolicy$.MODULE$);
    }

    @Test
    public void should_generate_simple_statement() throws Exception {
        //Given
        String input = "SELECT * FROM users LIMIT 10;";
        CassandraQueryOptions options = new CassandraQueryOptions(Option.apply(QUORUM),
                Option.<ConsistencyLevel>empty(),
                Option.empty(),
                Option.<RetryPolicy>empty(),
                Option.empty());

        //When
        final SimpleStatement actual = helper.generateSimpleStatement(new SimpleStm(input), options, intrContext);

        //Then
        assertThat(actual).isNotNull();
        assertThat(actual.getQueryString()).isEqualTo("SELECT * FROM users LIMIT 10;");
        assertThat(actual.getConsistencyLevel()).isSameAs(QUORUM);
    }

    @Test
    public void should_generate_batch_statement() throws Exception {
        //Given
        Statement st1 = new SimpleStatement("SELECT * FROM users LIMIT 10;");
        Statement st2 = new SimpleStatement("INSERT INTO users(id) VALUES(10);");
        Statement st3 = new SimpleStatement("UPDATE users SET name = 'John DOE' WHERE id=10;");
        CassandraQueryOptions options = new CassandraQueryOptions(Option.apply(QUORUM),
                Option.<ConsistencyLevel>empty(),
                Option.empty(),
                Option.<RetryPolicy>empty(),
                Option.empty());

        //When
        BatchStatement actual = helper.generateBatchStatement(UNLOGGED, options, toScalaList(asList(st1, st2, st3)));

        //Then
        assertThat(actual).isNotNull();
        final List<Statement> statements = new ArrayList<>(actual.getStatements());
        assertThat(statements).hasSize(3);
        assertThat(statements.get(0)).isSameAs(st1);
        assertThat(statements.get(1)).isSameAs(st2);
        assertThat(statements.get(2)).isSameAs(st3);
        assertThat(actual.getConsistencyLevel()).isSameAs(QUORUM);
    }

    @Test
    public void should_parse_bound_values() throws Exception {
        //Given
        String bs="'jdoe',32,'John DOE',null, true, '2014-06-12 34:00:34'";

        //When
        final List<String> actual = this.<String>toJavaList(helper.parseBoundValues("ps", bs));

        //Then
        assertThat(actual).containsExactly("'jdoe'", "32", "'John DOE'",
                "null", "true", "2014-06-12 34:00:34");
    }

    @Test
    public void should_parse_simple_date() throws Exception {
        //Given
        String dateString = "2015-07-30 12:00:01";

        //When
        final Date actual = helper.parseDate(dateString);

        //Then
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(actual);

        assertThat(calendar.get(Calendar.YEAR)).isEqualTo(2015);
        assertThat(calendar.get(Calendar.MONTH)).isEqualTo(Calendar.JULY);
        assertThat(calendar.get(Calendar.DAY_OF_MONTH)).isEqualTo(30);
        assertThat(calendar.get(Calendar.HOUR_OF_DAY)).isEqualTo(12);
        assertThat(calendar.get(Calendar.MINUTE)).isEqualTo(0);
        assertThat(calendar.get(Calendar.SECOND)).isEqualTo(1);
    }

    @Test
    public void should_parse_accurate_date() throws Exception {
        //Given
        String dateString = "2015-07-30 12:00:01.123";

        //When
        final Date actual = helper.parseDate(dateString);

        //Then
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(actual);

        assertThat(calendar.get(Calendar.YEAR)).isEqualTo(2015);
        assertThat(calendar.get(Calendar.MONTH)).isEqualTo(Calendar.JULY);
        assertThat(calendar.get(Calendar.DAY_OF_MONTH)).isEqualTo(30);
        assertThat(calendar.get(Calendar.HOUR_OF_DAY)).isEqualTo(12);
        assertThat(calendar.get(Calendar.MINUTE)).isEqualTo(0);
        assertThat(calendar.get(Calendar.SECOND)).isEqualTo(1);
        assertThat(calendar.get(Calendar.MILLISECOND)).isEqualTo(123);
    }

    private <A> scala.collection.immutable.List<A> toScalaList(java.util.List<A> list)  {
        return scala.collection.JavaConverters.asScalaBufferConverter(list).asScala().toList();
    }

    private  <A> java.util.List<A> toJavaList(scala.collection.immutable.List<A> list){
        return scala.collection.JavaConverters.seqAsJavaListConverter(list).asJava();
    }
}