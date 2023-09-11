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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static java.util.Arrays.asList;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchableStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;

import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterpreterLogicTest {

  private InterpreterContext intrContext;
  private CqlSession session;

  @BeforeEach
  public void setup() {
    intrContext = mock(InterpreterContext.class, Answers.RETURNS_DEEP_STUBS);
    session = mock(CqlSession.class);
  }

  final InterpreterLogic helper = new InterpreterLogic(session, new Properties());

  @Test
  void should_parse_input_string_block() {
    // Given
    String input = "SELECT * FROM users LIMIT 10;";

    // When
    final List<AnyBlock> anyBlocks = this.toJavaList(helper.parseInput(input));

    // Then
    assertEquals(1, anyBlocks.size());
    assertTrue(anyBlocks.get(0) instanceof SimpleStm);
  }

  @Test
  void should_parse_input_string_block_with_comment_dash() {
    // Given
    String input = "SELECT * FROM users LIMIT 10; -- this is a comment";

    // When
    final List<AnyBlock> anyBlocks = this.toJavaList(helper.parseInput(input));

    // Then
    assertEquals(2, anyBlocks.size());
    assertTrue(anyBlocks.get(0) instanceof SimpleStm);
    assertTrue(anyBlocks.get(1) instanceof TextBlockHierarchy.Comment);
  }

  @Test
  void should_parse_input_string_block_with_comment_slash() {
    // Given
    String input = "SELECT * FROM users LIMIT 10; // this is a comment";

    // When
    final List<AnyBlock> anyBlocks = this.toJavaList(helper.parseInput(input));

    // Then
    assertEquals(2, anyBlocks.size());
    assertTrue(anyBlocks.get(0) instanceof SimpleStm);
    assertTrue(anyBlocks.get(1) instanceof TextBlockHierarchy.Comment);
  }

  @Test
  void should_exception_while_parsing_input() {
    // Given
    String input = "SELECT * FROM users LIMIT 10";

    // When
    InterpreterException ex = assertThrows(InterpreterException.class, () -> {
      helper.parseInput(input);
    });

    assertEquals("Error parsing input:\n" +
        "\t'SELECT * FROM users LIMIT 10'\n" +
        "Did you forget to add ; (semi-colon) at the end of each CQL statement ?", ex.getMessage());

  }

  @Test
  void should_extract_variable_and_default_value() {
    // Given
    AngularObjectRegistry angularObjectRegistry = new AngularObjectRegistry("cassandra", null);
    when(intrContext.getAngularObjectRegistry()).thenReturn(angularObjectRegistry);
    when(intrContext.getGui().textbox("table", "zeppelin.demo")).thenReturn("zeppelin.demo");
    when(intrContext.getGui().textbox("id", "'John'")).thenReturn("'John'");

    // When
    final String actual = helper.maybeExtractVariables(
        "SELECT * FROM {{table=zeppelin.demo}} WHERE id={{id='John'}}", intrContext);

    // Then
    assertEquals("SELECT * FROM zeppelin.demo WHERE id='John'", actual);
  }

  @Test
  void should_extract_variable_and_choices() {
    // Given
    AngularObjectRegistry angularObjectRegistry = new AngularObjectRegistry("cassandra", null);
    when(intrContext.getAngularObjectRegistry()).thenReturn(angularObjectRegistry);
    ArgumentCaptor<ParamOption[]> optionsCaptor = ArgumentCaptor.forClass(ParamOption[].class);
    when(intrContext.getGui().select(any(), any(), any())).thenReturn("'Jack'");
    // When
    final String actual = helper.maybeExtractVariables(
        "SELECT * FROM zeppelin.artists WHERE name={{name='Paul'|'Jack'|'Smith'}}",
        intrContext);
    verify(intrContext.getGui()).select(eq("name"), optionsCaptor.capture(), eq("'Paul'"));
    // Then
    assertEquals("SELECT * FROM zeppelin.artists WHERE name='Jack'", actual);
    final List<ParamOption> paramOptions = asList(optionsCaptor.getValue());
    assertEquals("'Paul'", paramOptions.get(0).getValue());
    assertEquals("'Jack'", paramOptions.get(1).getValue());
    assertEquals("'Smith'", paramOptions.get(2).getValue());
  }

  @Test
  void should_extract_no_variable() {
    // Given
    GUI gui = mock(GUI.class);
    when(intrContext.getGui()).thenReturn(gui);

    // When
    final String actual = helper.maybeExtractVariables("SELECT * FROM zeppelin.demo", intrContext);

    // Then
    verifyNoInteractions(gui);
    assertEquals("SELECT * FROM zeppelin.demo", actual);
  }

  @Test
  void should_extract_variable_from_angular_object_registry() {
    // Given
    AngularObjectRegistry angularObjectRegistry = new AngularObjectRegistry("cassandra", null);
    angularObjectRegistry.add("id", "from_angular_registry", "noteId", "paragraphId");
    when(intrContext.getAngularObjectRegistry()).thenReturn(angularObjectRegistry);
    when(intrContext.getNoteId()).thenReturn("noteId");
    when(intrContext.getParagraphId()).thenReturn("paragraphId");

    // When
    final String actual = helper.maybeExtractVariables(
        "SELECT * FROM zeppelin.demo WHERE id='{{id=John}}'", intrContext);

    // Then
    assertEquals("SELECT * FROM zeppelin.demo WHERE id='from_angular_registry'", actual);
    verify(intrContext, never()).getGui();
  }

  @Test
  public void should_error_if_incorrect_variable_definition() {
    // Given

    // When
    ParsingException thrown = assertThrows(ParsingException.class, () -> {
      // Then
      helper.maybeExtractVariables("SELECT * FROM {{table?zeppelin.demo}} WHERE id={{id='John'}}",
          intrContext);
    });
    assertEquals("Invalid bound variable definition for " +
        "'{{table?zeppelin.demo}}' in 'SELECT * FROM {{table?zeppelin.demo}} " +
        "WHERE id={{id='John'}}'. It should be of form 'variable=defaultValue' " +
        "or 'variable=value1|value2|...|valueN'",
        thrown.getMessage());

  }

  @Test
  void should_extract_consistency_option() {
    // Given
    List<QueryParameters> options = Arrays.asList(new Consistency(ALL),
        new Consistency(ONE));

    // When
    final CassandraQueryOptions actual = helper.extractQueryOptions(toScalaList(options));

    // Then
    assertEquals(ALL, actual.consistency().get());
  }

  @Test
  void should_extract_serial_consistency_option() {
    // Given
    List<QueryParameters> options = Arrays.asList(new SerialConsistency(SERIAL),
        new SerialConsistency(LOCAL_SERIAL));

    // When
    final CassandraQueryOptions actual = helper.extractQueryOptions(toScalaList(options));

    // Then
    assertEquals(SERIAL, actual.serialConsistency().get());
  }

  @Test
  void should_extract_timestamp_option() {
    // Given
    List<QueryParameters> options = Arrays.asList(new Timestamp(123L),
        new Timestamp(456L));

    // When
    final CassandraQueryOptions actual = helper.extractQueryOptions(toScalaList(options));

    // Then
    assertEquals(123L, actual.timestamp().get());
  }

  @Test
  void should_extract_request_timeout_option() {
    // Given
    List<QueryParameters> options = Collections.singletonList(new RequestTimeOut(100));

    // When
    final CassandraQueryOptions actual = helper.extractQueryOptions(toScalaList(options));

    // Then
    assertEquals(100, actual.requestTimeOut().get());
  }

  @Test
  void should_generate_simple_statement() {
    // Given
    String input = "SELECT * FROM users LIMIT 10;";
    CassandraQueryOptions options = new CassandraQueryOptions(Option.apply(QUORUM),
        Option.empty(),
        Option.empty(),
        Option.empty(),
        Option.empty());

    // When
    final SimpleStatement actual = helper.generateSimpleStatement(new SimpleStm(input), options,
        intrContext);

    // Then
    assertNotNull(actual);
    assertEquals("SELECT * FROM users LIMIT 10;", actual.getQuery());
    assertSame(QUORUM, actual.getConsistencyLevel());
  }

  @Test
  void should_generate_batch_statement() {
    // Given
    SimpleStatement st1 = SimpleStatement.newInstance("SELECT * FROM users LIMIT 10;");
    SimpleStatement st2 = SimpleStatement.newInstance("INSERT INTO users(id) VALUES(10);");
    SimpleStatement st3 = SimpleStatement.newInstance(
        "UPDATE users SET name = 'John DOE' WHERE id=10;");
    CassandraQueryOptions options = new CassandraQueryOptions(Option.apply(QUORUM),
        Option.empty(),
        Option.empty(),
        Option.empty(),
        Option.empty());

    // When
    BatchStatement actual = helper.generateBatchStatement(UNLOGGED, options,
        toScalaList(asList(st1, st2, st3)));

    // Then
    assertNotNull(actual);
    List<BatchableStatement> statements = new ArrayList<>();
    for (BatchableStatement b : actual) {
      statements.add(b);
    }
    assertEquals(3, statements.size());
    assertSame(st1, statements.get(0));
    assertSame(st2, statements.get(1));
    assertSame(st3, statements.get(2));
    assertSame(QUORUM, actual.getConsistencyLevel());
  }

  @Test
  void should_parse_bound_values() {
    // Given
    String bs = "'jdoe',32,'John DOE',null, true, '2014-06-12 34:00:34'";

    // When
    final List<String> actual = this.toJavaList(helper.parseBoundValues("ps", bs));

    // Then
    assertEquals("'jdoe'", actual.get(0));
    assertEquals("32", actual.get(1));
    assertEquals("'John DOE'", actual.get(2));
    assertEquals("null", actual.get(3));
    assertEquals("true", actual.get(4));
    assertEquals("2014-06-12 34:00:34", actual.get(5));
  }

  @Test
  void should_parse_simple_date() {
    // Given
    String dateString = "2015-07-30 12:00:01";

    // When
    final Instant actual = helper.parseDate(dateString);

    // Then
    ZonedDateTime dt = actual.atZone(ZoneOffset.UTC);

    assertEquals(2015, dt.getLong(ChronoField.YEAR_OF_ERA));
    assertEquals(7, dt.getLong(ChronoField.MONTH_OF_YEAR));
    assertEquals(30, dt.getLong(ChronoField.DAY_OF_MONTH));
    assertEquals(12, dt.getLong(ChronoField.HOUR_OF_DAY));
    assertEquals(0, dt.getLong(ChronoField.MINUTE_OF_HOUR));
    assertEquals(1, dt.getLong(ChronoField.SECOND_OF_MINUTE));
  }

  @Test
  void should_parse_accurate_date() {
    // Given
    String dateString = "2015-07-30 12:00:01.123";

    // When
    final Instant actual = helper.parseDate(dateString);

    // Then
    ZonedDateTime dt = actual.atZone(ZoneOffset.UTC);

    assertEquals(2015, dt.getLong(ChronoField.YEAR_OF_ERA));
    assertEquals(7, dt.getLong(ChronoField.MONTH_OF_YEAR));
    assertEquals(30, dt.getLong(ChronoField.DAY_OF_MONTH));
    assertEquals(12, dt.getLong(ChronoField.HOUR_OF_DAY));
    assertEquals(0, dt.getLong(ChronoField.MINUTE_OF_HOUR));
    assertEquals(1, dt.getLong(ChronoField.SECOND_OF_MINUTE));
    assertEquals(123, dt.getLong(ChronoField.MILLI_OF_SECOND));
  }

  private <A> scala.collection.immutable.List<A> toScalaList(java.util.List<A> list) {
    return scala.collection.JavaConversions.collectionAsScalaIterable(list).toList();
  }

  private <A> java.util.List<A> toJavaList(scala.collection.immutable.List<A> list) {
    return scala.collection.JavaConversions.seqAsJavaList(list);
  }
}
