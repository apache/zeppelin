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

package org.apache.zeppelin.markdown;

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Properties;

import static org.apache.zeppelin.markdown.FlexmarkParser.wrapWithMarkdownClassDiv;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class FlexmarkParserTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlexmarkParserTest.class);
  Markdown md;

  @Rule
  public ErrorCollector collector = new ErrorCollector();

  @Before
  public void setUp() throws Exception {
    Properties props = new Properties();
    props.put(Markdown.MARKDOWN_PARSER_TYPE, Markdown.PARSER_TYPE_FLEXMARK);
    md = new Markdown(props);
    md.open();
  }

  @After
  public void tearDown() throws Exception {
    md.close();
  }

  @Test
  public void testMultipleThread() {
    ArrayList<Thread> arrThreads = new ArrayList<Thread>();
    for (int i = 0; i < 10; i++) {
      Thread t = new Thread() {
        @Override
        public void run() {
          String r1 = null;
          try {
            r1 = md.interpret("# H1", null).code().name();
          } catch (Exception e) {
            LOGGER.error("testTestMultipleThread failed to interpret", e);
          }
          collector.checkThat("SUCCESS",
              CoreMatchers.containsString(r1));
        }
      };
      t.start();
      arrThreads.add(t);
    }

    for (int i = 0; i < 10; i++) {
      try {
        arrThreads.get(i).join();
      } catch (InterruptedException e) {
        LOGGER.error("testTestMultipleThread failed to join threads", e);
      }
    }
  }

  @Test
  public void testStrikethrough() {
    InterpreterResult result = md.interpret("This is ~~deleted~~ text", null);
    assertEquals(wrapWithMarkdownClassDiv("<p>This is <del>deleted</del> text</p>\n"),
        result.message().get(0).getData());
  }

  @Test
  public void testHeader() {
    InterpreterResult r1 = md.interpret("# H1", null);
    assertEquals(wrapWithMarkdownClassDiv("<h1>H1</h1>\n"), r1.message().get(0).getData());

    InterpreterResult r2 = md.interpret("## H2", null);
    assertEquals(wrapWithMarkdownClassDiv("<h2>H2</h2>\n"), r2.message().get(0).getData());

    InterpreterResult r3 = md.interpret("### H3", null);
    assertEquals(wrapWithMarkdownClassDiv("<h3>H3</h3>\n"), r3.message().get(0).getData());

    InterpreterResult r4 = md.interpret("#### H4", null);
    assertEquals(wrapWithMarkdownClassDiv("<h4>H4</h4>\n"), r4.message().get(0).getData());

    InterpreterResult r5 = md.interpret("##### H5", null);
    assertEquals(wrapWithMarkdownClassDiv("<h5>H5</h5>\n"), r5.message().get(0).getData());

    InterpreterResult r6 = md.interpret("###### H6", null);
    assertEquals(wrapWithMarkdownClassDiv("<h6>H6</h6>\n"), r6.message().get(0).getData());

    InterpreterResult r7 = md.interpret("Alt-H1\n" + "======", null);
    assertEquals(wrapWithMarkdownClassDiv("<h1>Alt-H1</h1>\n"), r7.message().get(0).getData());

    InterpreterResult r8 = md.interpret("Alt-H2\n" + "------", null);
    assertEquals(wrapWithMarkdownClassDiv("<h2>Alt-H2</h2>\n"), r8.message().get(0).getData());
  }

  @Test
  public void testItalics() {
    InterpreterResult result = md.interpret("This is *italics* text", null);

    assertEquals(
        wrapWithMarkdownClassDiv("<p>This is <em>italics</em> text</p>\n"),
        result.message().get(0).getData());
  }

  @Test
  public void testStrongEmphasis() {
    InterpreterResult result = md.interpret("This is **strong emphasis** text", null);
    assertEquals(
        wrapWithMarkdownClassDiv("<p>This is <strong>strong emphasis</strong> text</p>\n"),
        result.message().get(0).getData());
  }

  @Test
  public void testOrderedList() {
    String input =
        new StringBuilder()
            .append("1. First ordered list item\n")
            .append("2. Another item")
            .toString();

    String expected =
        new StringBuilder()
            .append("<ol>\n")
            .append("<li>First ordered list item</li>\n")
            .append("<li>Another item</li>\n")
            .append("</ol>\n")
            .toString();

    InterpreterResult result = md.interpret(input, null);


    assertEquals(wrapWithMarkdownClassDiv(expected), result.message().get(0).getData());
  }

  @Test
  public void testUnorderedList() {
    String input =
        new StringBuilder()
            .append("* Unordered list can use asterisks\n")
            .append("- Or minuses\n")
            .append("+ Or pluses")
            .toString();

    String expected =
        new StringBuilder()
            .append("<ul>\n")
            .append("<li>Unordered list can use asterisks</li>\n")
            .append("</ul>\n")
            .append("<ul>\n")
            .append("<li>Or minuses</li>\n")
            .append("</ul>\n")
            .append("<ul>\n")
            .append("<li>Or pluses</li>\n")
            .append("</ul>\n")
            .toString();

    InterpreterResult result = md.interpret(input, null);

    assertEquals(wrapWithMarkdownClassDiv(expected), result.message().get(0).getData());
  }

  @Test
  public void testYumlPlugin() {
    String input = new StringBuilder()
        .append("%%% yuml style=nofunky scale=120 format=svg\n")
        .append("[Customer]<>-orders>[Order]\n")
        .append("[Order]++-0..>[LineItem]\n")
        .append("[Order]-[note:Aggregate root.]\n")
        .append("  %%%  ")
        .toString();

    InterpreterResult result = md.interpret(input, null);
    assertThat(result.message().get(0).getData(), CoreMatchers
        .containsString("<img src=\"http://yuml.me/diagram/"));
  }

  @Test
  public void testWebsequencePlugin() {
    String input =
        new StringBuilder()
            .append("%%% sequence style=modern-blue\n")
            .append("title Authentication Sequence\n")
            .append("Alice->Bob: Authentication Request\n")
            .append("note right of Bob: Bob thinks about it\n")
            .append("Bob->Alice: Authentication Response\n")
            .append("  %%%  ")
            .toString();

    InterpreterResult result = md.interpret(input, null);

    final String expected = "<img src=\"https://www.websequencediagrams.com/?png=";
    boolean containsImg = result.message().get(0).getData().contains(expected);
    if (!containsImg) {
      LOGGER.error("Expected {} but found {}",
          expected, result.message().get(0).getData());
    }
    // Do not activate, because this test depends on www.websequencediagrams.com
    //assertTrue(containsImg);
  }
}

