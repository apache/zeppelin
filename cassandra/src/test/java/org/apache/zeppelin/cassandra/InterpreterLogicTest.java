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

import static com.datastax.oss.driver.api.core.ConsistencyLevel.ALL;
import static com.datastax.oss.driver.api.core.ConsistencyLevel.LOCAL_SERIAL;
import static com.datastax.oss.driver.api.core.ConsistencyLevel.ONE;
import static com.datastax.oss.driver.api.core.ConsistencyLevel.QUORUM;
import static com.datastax.oss.driver.api.core.ConsistencyLevel.SERIAL;
import static com.datastax.oss.driver.api.core.cql.BatchType.UNLOGGED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import static java.util.Arrays.asList;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchableStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import scala.Option;

import org.apache.zeppelin.cassandra.TextBlockHierarchy.AnyBlock;
import org.apache.zeppelin.cassandra.TextBlockHierarchy.Consistency;
import org.apache.zeppelin.cassandra.TextBlockHierarchy.QueryParameters;
import org.apache.zeppelin.cassandra.TextBlockHierarchy.RequestTimeOut;
import org.apache.zeppelin.cassandra.TextBlockHierarchy.SerialConsistency;
import org.apache.zeppelin.cassandra.TextBlockHierarchy.SimpleStm;
import org.apache.zeppelin.cassandra.TextBlockHierarchy.Timestamp;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.GUI;
import org.apache.zeppelin.display.ui.OptionInput.ParamOption;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;

@RunWith(MockitoJUnitRunner.class)
public class InterpreterLogicTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private InterpreterContext intrContext;

  @Mock
  private CqlSession session;

  final InterpreterLogic helper = new InterpreterLogic(session, new Properties());

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
  public void should_parse_input_string_block_with_comment_dash() throws Exception {
    //Given
    String input = "SELECT * FROM users LIMIT 10; -- this is a comment";

    //When
    final List<AnyBlock> anyBlocks = this.<AnyBlock>toJavaList(helper.parseInput(input));

    //Then
    assertThat(anyBlocks).hasSize(2);
    assertThat(anyBlocks.get(0)).isInstanceOf(SimpleStm.class);
    assertThat(anyBlocks.get(1)).isInstanceOf(TextBlockHierarchy.Comment.class);
  }

  @Test
  public void should_parse_input_string_block_with_comment_slash() throws Exception {
    //Given
    String input = "SELECT * FROM users LIMIT 10; // this is a comment";

    //When
    final List<AnyBlock> anyBlocks = this.<AnyBlock>toJavaList(helper.parseInput(input));

    //Then
    assertThat(anyBlocks).hasSize(2);
    assertThat(anyBlocks.get(0)).isInstanceOf(SimpleStm.class);
    assertThat(anyBlocks.get(1)).isInstanceOf(TextBlockHierarchy.Comment.class);
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
    AngularObjectRegistry angularObjectRegistry = new AngularObjectRegistry("cassandra", null);
    when(intrContext.getAngularObjectRegistry()).thenReturn(angularObjectRegistry);
    when(intrContext.getGui().input("table", "zeppelin.demo")).thenReturn("zeppelin.demo");
    when(intrContext.getGui().input("id", "'John'")).thenReturn("'John'");

    //When
    final String actual = helper.maybeExtractVariables(
            "SELECT * FROM {{table=zeppelin.demo}} WHERE id={{id='John'}}", intrContext);

    //Then
    assertThat(actual).isEqualTo("SELECT * FROM zeppelin.demo WHERE id='John'");
  }

  @Test
  public void should_extract_variable_and_choices() throws Exception {
    //Given
    AngularObjectRegistry angularObjectRegistry = new AngularObjectRegistry("cassandra", null);
    when(intrContext.getAngularObjectRegistry()).thenReturn(angularObjectRegistry);
    when(intrContext.getGui().select(eq("name"), optionsCaptor.capture(), eq("'Paul'")))
            .thenReturn("'Jack'");

    //When
    final String actual = helper.maybeExtractVariables(
            "SELECT * FROM zeppelin.artists WHERE name={{name='Paul'|'Jack'|'Smith'}}",
            intrContext);

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
  public void should_extract_variable_from_angular_object_registry() throws Exception {
    //Given
    AngularObjectRegistry angularObjectRegistry = new AngularObjectRegistry("cassandra", null);
    angularObjectRegistry.add("id", "from_angular_registry", "noteId", "paragraphId");
    when(intrContext.getAngularObjectRegistry()).thenReturn(angularObjectRegistry);
    when(intrContext.getNoteId()).thenReturn("noteId");
    when(intrContext.getParagraphId()).thenReturn("paragraphId");

    //When
    final String actual = helper.maybeExtractVariables(
            "SELECT * FROM zeppelin.demo WHERE id='{{id=John}}'", intrContext);

    //Then
    assertThat(actual).isEqualTo("SELECT * FROM zeppelin.demo WHERE id='from_angular_registry'");
    verify(intrContext, never()).getGui();
  }

  @Test
  public void should_error_if_incorrect_variable_definition() throws Exception {
    //Given

    //When
    expectedException.expect(ParsingException.class);
    expectedException.expectMessage("Invalid bound variable definition for " +
            "'{{table?zeppelin.demo}}' in 'SELECT * FROM {{table?zeppelin.demo}} " +
            "WHERE id={{id='John'}}'. It should be of form 'variable=defaultValue'");

    //Then
    helper.maybeExtractVariables("SELECT * FROM {{table?zeppelin.demo}} WHERE id={{id='John'}}",
            intrContext);
  }

  @Test
  public void should_extract_consistency_option() throws Exception {
    //Given
    List<QueryParameters> options = Arrays.<QueryParameters>asList(new Consistency(ALL),
            new Consistency(ONE));

    //When
    final CassandraQueryOptions actual = helper.extractQueryOptions(toScalaList(options));

    //Then
    assertThat(actual.consistency().get()).isEqualTo(ALL);
  }

  @Test
  public void should_extract_serial_consistency_option() throws Exception {
    //Given
    List<QueryParameters> options = Arrays.<QueryParameters>asList(new SerialConsistency(SERIAL),
            new SerialConsistency(LOCAL_SERIAL));

    //When
    final CassandraQueryOptions actual = helper.extractQueryOptions(toScalaList(options));

    //Then
    assertThat(actual.serialConsistency().get()).isEqualTo(SERIAL);
  }

  @Test
  public void should_extract_timestamp_option() throws Exception {
    //Given
    List<QueryParameters> options = Arrays.<QueryParameters>asList(new Timestamp(123L),
            new Timestamp(456L));

    //When
    final CassandraQueryOptions actual = helper.extractQueryOptions(toScalaList(options));

    //Then
    assertThat(actual.timestamp().get()).isEqualTo(123L);
  }

  @Test
  public void should_extract_request_timeout_option() throws Exception {
    //Given
    List<QueryParameters> options = Arrays.<QueryParameters>asList(new RequestTimeOut(100));

    //When
    final CassandraQueryOptions actual = helper.extractQueryOptions(toScalaList(options));

    //Then
    assertThat(actual.requestTimeOut().get()).isEqualTo(100);
  }

  @Test
  public void should_generate_simple_statement() throws Exception {
    //Given
    String input = "SELECT * FROM users LIMIT 10;";
    CassandraQueryOptions options = new CassandraQueryOptions(Option.apply(QUORUM),
            Option.<ConsistencyLevel>empty(),
            Option.empty(),
            Option.empty(),
            Option.empty());

    //When
    final SimpleStatement actual = helper.generateSimpleStatement(new SimpleStm(input), options,
            intrContext);

    //Then
    assertThat(actual).isNotNull();
    assertThat(actual.getQuery()).isEqualTo("SELECT * FROM users LIMIT 10;");
    assertThat(actual.getConsistencyLevel()).isSameAs(QUORUM);
  }

  @Test
  public void should_generate_batch_statement() throws Exception {
    //Given
    SimpleStatement st1 = SimpleStatement.newInstance("SELECT * FROM users LIMIT 10;");
    SimpleStatement st2 = SimpleStatement.newInstance("INSERT INTO users(id) VALUES(10);");
    SimpleStatement st3 = SimpleStatement.newInstance(
            "UPDATE users SET name = 'John DOE' WHERE id=10;");
    CassandraQueryOptions options = new CassandraQueryOptions(Option.apply(QUORUM),
            Option.<ConsistencyLevel>empty(),
            Option.empty(),
            Option.empty(),
            Option.empty());

    //When
    BatchStatement actual = helper.generateBatchStatement(UNLOGGED, options,
            toScalaList(asList(st1, st2, st3)));

    //Then
    assertThat(actual).isNotNull();
    List<BatchableStatement> statements = new ArrayList<BatchableStatement>();
    for (BatchableStatement b: actual) {
      statements.add(b);
    }
    assertThat(statements).hasSize(3);
    assertThat(statements.get(0)).isSameAs(st1);
    assertThat(statements.get(1)).isSameAs(st2);
    assertThat(statements.get(2)).isSameAs(st3);
    assertThat(actual.getConsistencyLevel()).isSameAs(QUORUM);
  }

  @Test
  public void should_parse_bound_values() throws Exception {
    //Given
    String bs = "'jdoe',32,'John DOE',null, true, '2014-06-12 34:00:34'";

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
    final Instant actual = helper.parseDate(dateString);

    //Then
    ZonedDateTime dt = actual.atZone(ZoneOffset.UTC);

    assertThat(dt.getLong(ChronoField.YEAR_OF_ERA)).isEqualTo(2015);
    assertThat(dt.getLong(ChronoField.MONTH_OF_YEAR)).isEqualTo(7);
    assertThat(dt.getLong(ChronoField.DAY_OF_MONTH)).isEqualTo(30);
    assertThat(dt.getLong(ChronoField.HOUR_OF_DAY)).isEqualTo(12);
    assertThat(dt.getLong(ChronoField.MINUTE_OF_HOUR)).isEqualTo(0);
    assertThat(dt.getLong(ChronoField.SECOND_OF_MINUTE)).isEqualTo(1);
  }

  @Test
  public void should_parse_accurate_date() throws Exception {
    //Given
    String dateString = "2015-07-30 12:00:01.123";

    //When
    final Instant actual = helper.parseDate(dateString);

    //Then
    ZonedDateTime dt = actual.atZone(ZoneOffset.UTC);

    assertThat(dt.getLong(ChronoField.YEAR_OF_ERA)).isEqualTo(2015);
    assertThat(dt.getLong(ChronoField.MONTH_OF_YEAR)).isEqualTo(7);
    assertThat(dt.getLong(ChronoField.DAY_OF_MONTH)).isEqualTo(30);
    assertThat(dt.getLong(ChronoField.HOUR_OF_DAY)).isEqualTo(12);
    assertThat(dt.getLong(ChronoField.MINUTE_OF_HOUR)).isEqualTo(0);
    assertThat(dt.getLong(ChronoField.SECOND_OF_MINUTE)).isEqualTo(1);
    assertThat(dt.getLong(ChronoField.MILLI_OF_SECOND)).isEqualTo(123);
  }

  private <A> scala.collection.immutable.List<A> toScalaList(java.util.List<A> list)  {
    return scala.collection.JavaConversions.collectionAsScalaIterable(list).toList();
  }

  private  <A> java.util.List<A> toJavaList(scala.collection.immutable.List<A> list){
    return scala.collection.JavaConversions.seqAsJavaList(list);
  }
}
