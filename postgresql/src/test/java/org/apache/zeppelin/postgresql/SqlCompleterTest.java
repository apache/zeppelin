/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.postgresql;

import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import jline.console.completer.Completer;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.mockrunner.jdbc.BasicJDBCTestCaseAdapter;

public class SqlCompleterTest extends BasicJDBCTestCaseAdapter {

  private Logger logger = LoggerFactory.getLogger(SqlCompleterTest.class);

  private final static Set<String> EMPTY = new HashSet<>();

  private CompleterTester tester;

  private SqlCompleter sqlCompleter;

  @Before
  public void beforeTest() throws IOException, SQLException {
    Set<String> keywordsCompletions =
        SqlCompleter.getSqlKeywordsCompletions(getJDBCMockObjectFactory().getMockConnection());
    Set<String> dataModelCompletions =
        SqlCompleter
            .getDataModelMetadataCompletions(getJDBCMockObjectFactory().getMockConnection());

    sqlCompleter =
        new SqlCompleter(Sets.union(keywordsCompletions, dataModelCompletions),
            dataModelCompletions);
    tester = new CompleterTester(sqlCompleter);
  }

  @Test
  public void testAfterBufferEnd() {
    String buffer = "ORDER";
    // Up to 2 white spaces after the buffer end, the completer still uses the last argument
    tester.buffer(buffer).from(0).to(buffer.length() + 1).expect(newHashSet("ORDER ")).test();
    // 2 white spaces or more behind the buffer end the completer returns empty result
    tester.buffer(buffer).from(buffer.length() + 2).to(buffer.length() + 5).expect(EMPTY).test();
  }

  @Test
  public void testEdges() {
    String buffer = "  ORDER  ";
    tester.buffer(buffer).from(0).to(8).expect(newHashSet("ORDER ")).test();
    tester.buffer(buffer).from(9).to(15).expect(EMPTY).test();
  }

  @Test
  public void testMultipleWords() {
    String buffer = "  SELE  fro    LIM";
    tester.buffer(buffer).from(0).to(6).expect(newHashSet("SELECT ")).test();
    tester.buffer(buffer).from(7).to(11).expect(newHashSet("from ")).test();
    tester.buffer(buffer).from(12).to(19).expect(newHashSet("LIMIT ")).test();
    tester.buffer(buffer).from(20).to(24).expect(EMPTY).test();
  }

  @Test
  public void testMultiLineBuffer() {
    String buffer = " \n SELE \n fro";
    tester.buffer(buffer).from(0).to(7).expect(newHashSet("SELECT ")).test();
    tester.buffer(buffer).from(8).to(14).expect(newHashSet("from ")).test();
    tester.buffer(buffer).from(15).to(17).expect(EMPTY).test();
  }

  @Test
  public void testMultipleCompletionSuggestions() {
    String buffer = "  SU";
    tester.buffer(buffer).from(0).to(5).expect(newHashSet("SUBCLASS_ORIGIN", "SUM", "SUBSTRING"))
        .test();
    tester.buffer(buffer).from(6).to(7).expect(EMPTY).test();
  }

  @Test
  public void testDotDelimiter() {
    String buffer = "  order.select  ";
    tester.buffer(buffer).from(4).to(7).expect(newHashSet("order ")).test();
    tester.buffer(buffer).from(8).to(15).expect(newHashSet("select ")).test();
    tester.buffer(buffer).from(16).to(17).expect(EMPTY).test();
  }

  @Test
  public void testSqlDelimiterCharacters() {
    assertTrue(sqlCompleter.getSqlDelimiter().isDelimiterChar("r.", 1));
    assertTrue(sqlCompleter.getSqlDelimiter().isDelimiterChar("SS;", 2));
    assertTrue(sqlCompleter.getSqlDelimiter().isDelimiterChar(":", 0));
    assertTrue(sqlCompleter.getSqlDelimiter().isDelimiterChar("ttt,", 3));
  }

  public class CompleterTester {

    private Completer completer;

    private String buffer;
    private int fromCursor;
    private int toCursor;
    private Set<String> expectedCompletions;

    public CompleterTester(Completer completer) {
      this.completer = completer;
    }

    public CompleterTester buffer(String buffer) {
      this.buffer = buffer;
      return this;
    }

    public CompleterTester from(int fromCursor) {
      this.fromCursor = fromCursor;
      return this;
    }

    public CompleterTester to(int toCursor) {
      this.toCursor = toCursor;
      return this;
    }

    public CompleterTester expect(Set<String> expectedCompletions) {
      this.expectedCompletions = expectedCompletions;
      return this;
    }

    public void test() {
      for (int c = fromCursor; c <= toCursor; c++) {
        expectedCompletions(buffer, c, expectedCompletions);
      }
    }

    private void expectedCompletions(String buffer, int cursor, Set<String> expected) {

      ArrayList<CharSequence> candidates = new ArrayList<>();

      completer.complete(buffer, cursor, candidates);

      String explain = explain(buffer, cursor, candidates);

      logger.info(explain);

      assertEquals("Buffer [" + buffer.replace(" ", ".") + "] and Cursor[" + cursor + "] "
          + explain, expected, newHashSet(candidates));
    }

    private String explain(String buffer, int cursor, ArrayList<CharSequence> candidates) {
      StringBuffer sb = new StringBuffer();

      for (int i = 0; i <= Math.max(cursor, buffer.length()); i++) {
        if (i == cursor) {
          sb.append("(");
        }
        if (i >= buffer.length()) {
          sb.append("_");
        } else {
          if (Character.isWhitespace(buffer.charAt(i))) {
            sb.append(".");
          } else {
            sb.append(buffer.charAt(i));
          }
        }
        if (i == cursor) {
          sb.append(")");
        }
      }
      sb.append(" >> [").append(Joiner.on(",").join(candidates)).append("]");

      return sb.toString();
    }
  }
}
