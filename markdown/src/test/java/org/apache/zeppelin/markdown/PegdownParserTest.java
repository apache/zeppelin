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

import static org.junit.Assert.assertEquals;

import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterResult;

import static org.apache.zeppelin.markdown.PegdownParser.wrapWithMarkdownClassDiv;
import static org.junit.Assert.assertThat;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class PegdownParserTest {

  Markdown md;

  @Before
  public void setUp() throws Exception {
    Properties props = new Properties();
    props.put(Markdown.MARKDOWN_PARSER_TYPE, Markdown.PARSER_TYPE_PEGDOWN);
    md = new Markdown(props);
    md.open();
  }

  @After
  public void tearDown() throws Exception {
    md.close();
  }

  @Test
  public void testHeader() {
    InterpreterResult r1 = md.interpret("# H1", null);
    assertEquals(wrapWithMarkdownClassDiv("<h1>H1</h1>"), r1.message().get(0).getData());

    InterpreterResult r2 = md.interpret("## H2", null);
    assertEquals(wrapWithMarkdownClassDiv("<h2>H2</h2>"), r2.message().get(0).getData());

    InterpreterResult r3 = md.interpret("### H3", null);
    assertEquals(wrapWithMarkdownClassDiv("<h3>H3</h3>"), r3.message().get(0).getData());

    InterpreterResult r4 = md.interpret("#### H4", null);
    assertEquals(wrapWithMarkdownClassDiv("<h4>H4</h4>"), r4.message().get(0).getData());

    InterpreterResult r5 = md.interpret("##### H5", null);
    assertEquals(wrapWithMarkdownClassDiv("<h5>H5</h5>"), r5.message().get(0).getData());

    InterpreterResult r6 = md.interpret("###### H6", null);
    assertEquals(wrapWithMarkdownClassDiv("<h6>H6</h6>"), r6.message().get(0).getData());

    InterpreterResult r7 = md.interpret("Alt-H1\n" + "======", null);
    assertEquals(wrapWithMarkdownClassDiv("<h1>Alt-H1</h1>"), r7.message().get(0).getData());

    InterpreterResult r8 = md.interpret("Alt-H2\n" + "------", null);
    assertEquals(wrapWithMarkdownClassDiv("<h2>Alt-H2</h2>"), r8.message().get(0).getData());
  }

  @Test
  public void testStrikethrough() {
    InterpreterResult result = md.interpret("This is ~~deleted~~ text", null);
    assertEquals(
        wrapWithMarkdownClassDiv("<p>This is <del>deleted</del> text</p>"), result.message().get(0).getData());
  }

  @Test
  public void testItalics() {
    InterpreterResult result = md.interpret("This is *italics* text", null);
    assertEquals(
        wrapWithMarkdownClassDiv("<p>This is <em>italics</em> text</p>"), result.message().get(0).getData());
  }

  @Test
  public void testStrongEmphasis() {
    InterpreterResult result = md.interpret("This is **strong emphasis** text", null);
    assertEquals(
        wrapWithMarkdownClassDiv("<p>This is <strong>strong emphasis</strong> text</p>"),
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
            .append("  <li>First ordered list item</li>\n")
            .append("  <li>Another item</li>\n")
            .append("</ol>")
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
            .append("  <li>Unordered list can use asterisks</li>\n")
            .append("  <li>Or minuses</li>\n")
            .append("  <li>Or pluses</li>\n")
            .append("</ul>")
            .toString();

    InterpreterResult result = md.interpret(input, null);
    assertEquals(wrapWithMarkdownClassDiv(expected), result.message().get(0).getData());
  }

  @Test
  public void testLinks() {
    String input =
        new StringBuilder()
            .append("[I'm an inline-style link](https://www.google.com)\n")
            .append("\n")
            .append(
                "[I'm an inline-style link with title](https://www.google.com \"Google's Homepage\")\n")
            .append("\n")
            .append("[I'm a reference-style link][Arbitrary case-insensitive reference text]\n")
            .append("\n")
            .append("[I'm a relative reference to a repository file](../blob/master/LICENSE)\n")
            .append("\n")
            .append("[You can use numbers for reference-style link definitions][1]\n")
            .append("\n")
            .append("Or leave it empty and use the [link text itself].\n")
            .append("\n")
            .append("URLs and URLs in angle brackets will automatically get turned into links. \n")
            .append("http://www.example.com or <http://www.example.com> and sometimes \n")
            .append("example.com (but not on Github, for example).\n")
            .append("\n")
            .append("Some text to show that the reference links can follow later.\n")
            .append("\n")
            .append("[arbitrary case-insensitive reference text]: https://www.mozilla.org\n")
            .append("[1]: http://slashdot.org\n")
            .append("[link text itself]: http://www.reddit.com")
            .toString();

    String expected =
        new StringBuilder()
            .append(
                "<p><a href=\"https://www.google.com\">I&rsquo;m an inline-style link</a></p>\n")
            .append(
                "<p><a href=\"https://www.google.com\" title=\"Google&#39;s Homepage\">I&rsquo;m an inline-style link with title</a></p>\n")
            .append(
                "<p><a href=\"https://www.mozilla.org\">I&rsquo;m a reference-style link</a></p>\n")
            .append(
                "<p><a href=\"../blob/master/LICENSE\">I&rsquo;m a relative reference to a repository file</a></p>\n")
            .append(
                "<p><a href=\"http://slashdot.org\">You can use numbers for reference-style link definitions</a></p>\n")
            .append(
                "<p>Or leave it empty and use the <a href=\"http://www.reddit.com\">link text itself</a>.</p>\n")
            .append(
                "<p>URLs and URLs in angle brackets will automatically get turned into links.<br/><a href=\"http://www.example.com\">http://www.example.com</a> or <a href=\"http://www.example.com\">http://www.example.com</a> and sometimes<br/>example.com (but not on Github, for example).</p>\n")
            .append("<p>Some text to show that the reference links can follow later.</p>")
            .toString();

    InterpreterResult result = md.interpret(input, null);
    assertEquals(wrapWithMarkdownClassDiv(expected), result.message().get(0).getData());
  }

  @Test
  public void testInlineCode() {
    InterpreterResult result = md.interpret("Inline `code` has `back-ticks around` it.", null);
    assertEquals(
        wrapWithMarkdownClassDiv(
            "<p>Inline <code>code</code> has <code>back-ticks around</code> it.</p>"),
        result.message().get(0).getData());
  }

  @Test
  public void testBlockQuotes() {
    InterpreterResult r1 =
        md.interpret(
            "> Blockquotes are very handy in email to emulate reply text.\n"
                + "> This line is part of the same quote.",
            null);
    assertEquals(
        wrapWithMarkdownClassDiv(
            "<blockquote>\n"
                + "  <p>Blockquotes are very handy in email to emulate reply text.<br/>This line is part of the same quote.</p>\n"
                + "</blockquote>"),
        r1.message().get(0).getData());

    InterpreterResult r2 =
        md.interpret(
            "> This is a very long line that will still be quoted properly when it wraps. Oh boy let's keep writing to make sure this is long enough to actually wrap for everyone. Oh, you can *put* **MarkdownInterpreter** into a blockquote. ",
            null);
    assertEquals(
        wrapWithMarkdownClassDiv(
            "<blockquote>\n"
                + "  <p>This is a very long line that will still be quoted properly when it wraps. Oh boy let&rsquo;s keep writing to make sure this is long enough to actually wrap for everyone. Oh, you can <em>put</em> <strong>MarkdownInterpreter</strong> into a blockquote. </p>\n"
                + "</blockquote>"),
        r2.message().get(0).getData());
  }

  @Test
  public void testSimpleTable() {
    String input =
        new StringBuilder()
            .append("MarkdownInterpreter | Less | Pretty\n")
            .append("--- | --- | ---\n")
            .append("*Still* | `renders` | **nicely**\n")
            .append("1 | 2 | 3")
            .toString();

    String expected =
        new StringBuilder()
            .append("<table>\n")
            .append("  <thead>\n")
            .append("    <tr>\n")
            .append("      <th>MarkdownInterpreter </th>\n")
            .append("      <th>Less </th>\n")
            .append("      <th>Pretty</th>\n")
            .append("    </tr>\n")
            .append("  </thead>\n")
            .append("  <tbody>\n")
            .append("    <tr>\n")
            .append("      <td><em>Still</em> </td>\n")
            .append("      <td><code>renders</code> </td>\n")
            .append("      <td><strong>nicely</strong></td>\n")
            .append("    </tr>\n")
            .append("    <tr>\n")
            .append("      <td>1 </td>\n")
            .append("      <td>2 </td>\n")
            .append("      <td>3</td>\n")
            .append("    </tr>\n")
            .append("  </tbody>\n")
            .append("</table>")
            .toString();

    InterpreterResult result = md.interpret(input, null);
    assertEquals(wrapWithMarkdownClassDiv(expected), result.message().get(0).getData());
  }

  @Test
  public void testAlignedTable() {
    String input =
        new StringBuilder()
            .append("| First Header | Second Header |         Third Header |\n")
            .append("| :----------- | :-----------: | -------------------: |\n")
            .append("| First row    |      Data     | Very long data entry |\n")
            .append("| Second row   |    **Cell**   |               *Cell* |")
            .toString();

    String expected =
        new StringBuilder()
            .append("<table>\n")
            .append("  <thead>\n")
            .append("    <tr>\n")
            .append("      <th align=\"left\">First Header </th>\n")
            .append("      <th align=\"center\">Second Header </th>\n")
            .append("      <th align=\"right\">Third Header </th>\n")
            .append("    </tr>\n")
            .append("  </thead>\n")
            .append("  <tbody>\n")
            .append("    <tr>\n")
            .append("      <td align=\"left\">First row </td>\n")
            .append("      <td align=\"center\">Data </td>\n")
            .append("      <td align=\"right\">Very long data entry </td>\n")
            .append("    </tr>\n")
            .append("    <tr>\n")
            .append("      <td align=\"left\">Second row </td>\n")
            .append("      <td align=\"center\"><strong>Cell</strong> </td>\n")
            .append("      <td align=\"right\"><em>Cell</em> </td>\n")
            .append("    </tr>\n")
            .append("  </tbody>\n")
            .append("</table>")
            .toString();

    InterpreterResult result = md.interpret(input, null);
    assertEquals(wrapWithMarkdownClassDiv(expected), result.message().get(0).getData());
  }

  @Test
  public void testWebsequencePlugin() {
    String input =
        new StringBuilder()
            .append("\n \n %%% sequence style=modern-blue\n")
            .append("title Authentication Sequence\n")
            .append("Alice->Bob: Authentication Request\n")
            .append("note right of Bob: Bob thinks about it\n")
            .append("Bob->Alice: Authentication Response\n")
            .append("  %%%  ")
            .toString();

    InterpreterResult result = md.interpret(input, null);
    assertThat(result.message().get(0).getData(), CoreMatchers.containsString("<img src=\"http://www.websequencediagrams.com/?png="));
  }

  @Test
  public void testYumlPlugin() {
    String input = new StringBuilder()
        .append("\n \n %%% yuml style=nofunky scale=120 format=svg\n")
        .append("[Customer]<>-orders>[Order]\n")
        .append("[Order]++-0..>[LineItem]\n")
        .append("[Order]-[note:Aggregate root.]\n")
        .append("  %%%  ")
        .toString();

    InterpreterResult result = md.interpret(input, null);
    assertThat(result.message().get(0).getData(), CoreMatchers.containsString("<img src=\"http://yuml.me/diagram/"));
  }
}
