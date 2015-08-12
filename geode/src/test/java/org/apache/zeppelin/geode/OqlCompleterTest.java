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
package org.apache.zeppelin.geode;

import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;

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

public class OqlCompleterTest {

  private Logger logger = LoggerFactory.getLogger(OqlCompleterTest.class);

  private final static Set<String> EMPTY = new HashSet<String>();

  private CompleterTester tester;

  @Before
  public void beforeTest() throws IOException, SQLException {
    OqlCompleter sqlCompleter = new OqlCompleter(OqlCompleter.getOqlCompleterTokens(null));
    tester = new CompleterTester(sqlCompleter);
  }

  @Test
  public void testEdges() {
    String buffer = "  ORDER  ";
    tester.buffer(buffer).from(0).to(8).expect(newHashSet("ORDER ")).test();
    tester.buffer(buffer).from(9).to(15).expect(EMPTY).test();
  }

  @Test
  public void testMultipleWords() {
    String buffer = "  SELE  fro";
    tester.buffer(buffer).from(0).to(6).expect(newHashSet("SELECT ")).test();
    tester.buffer(buffer).from(7).to(12).expect(newHashSet("from ")).test();
    tester.buffer(buffer).from(13).to(14).expect(EMPTY).test();
  }

  @Test
  public void testMultiLineBuffer() {
    String buffer = " \n SELE \n fro";
    tester.buffer(buffer).from(0).to(7).expect(newHashSet("SELECT ")).test();
    tester.buffer(buffer).from(8).to(14).expect(newHashSet("from ")).test();
    tester.buffer(buffer).from(15).to(16).expect(EMPTY).test();
  }

  @Test
  public void testMultipleCompletionSuggestions() {
    String buffer = "  S";
    tester.buffer(buffer).from(0).to(4)
        .expect(newHashSet("STRUCT", "SHORT", "SET", "SUM", "SELECT", "SOME", "STRING")).test();
    tester.buffer(buffer).from(5).to(7).expect(EMPTY).test();
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

      ArrayList<CharSequence> candidates = new ArrayList<CharSequence>();

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
