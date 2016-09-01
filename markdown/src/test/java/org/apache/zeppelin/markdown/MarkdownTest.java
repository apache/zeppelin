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
import static org.apache.zeppelin.markdown.Markdown.wrapHtmlWithMarkdownClassDiv;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MarkdownTest {

  Markdown md;

  @Before
  public void setUp() throws Exception {
    md = new Markdown(new Properties());
    md.open();
  }

  @After
  public void tearDown() throws Exception {
    md.close();
  }

  @Test
  public void testHeader() {
    InterpreterResult r1 = md.interpret("# H1", null);
    assertEquals(wrapHtmlWithMarkdownClassDiv("<h1>H1</h1>"), r1.message());

    InterpreterResult r2 = md.interpret("## H2", null);
    assertEquals(wrapHtmlWithMarkdownClassDiv("<h2>H2</h2>"), r2.message());

    InterpreterResult r3 = md.interpret("### H3", null);
    assertEquals(wrapHtmlWithMarkdownClassDiv("<h3>H3</h3>"), r3.message());

    InterpreterResult r4 = md.interpret("#### H4", null);
    assertEquals(wrapHtmlWithMarkdownClassDiv("<h4>H4</h4>"), r4.message());

    InterpreterResult r5 = md.interpret("##### H5", null);
    assertEquals(wrapHtmlWithMarkdownClassDiv("<h5>H5</h5>"), r5.message());

    InterpreterResult r6 = md.interpret("###### H6", null);
    assertEquals(wrapHtmlWithMarkdownClassDiv("<h6>H6</h6>"), r6.message());

    InterpreterResult r7 = md.interpret("Alt-H1\n" + "======", null);
    assertEquals(wrapHtmlWithMarkdownClassDiv("<h1>Alt-H1</h1>"), r7.message());

    InterpreterResult r8 = md.interpret("Alt-H2\n" + "------", null);
    assertEquals(wrapHtmlWithMarkdownClassDiv("<h2>Alt-H2</h2>"), r8.message());
  }

  @Test
  public void testStrikethrough() {
    InterpreterResult result = md.interpret("This is ~~deleted~~ text", null);
    assertEquals(
        wrapHtmlWithMarkdownClassDiv("<p>This is <del>deleted</del> text</p>"), result.message());
  }

  @Test
  public void testItalics() {
    InterpreterResult result = md.interpret("This is *italics* text", null);
    assertEquals(
        wrapHtmlWithMarkdownClassDiv("<p>This is <em>italics</em> text</p>"), result.message());
  }

  @Test
  public void testStrongEmphasis() {
    InterpreterResult result = md.interpret("This is **strong emphasis** text", null);
    assertEquals(
        wrapHtmlWithMarkdownClassDiv("<p>This is <strong>strong emphasis</strong> text</p>"),
        result.message());
  }

  @Test
  public void testOrderedList() {
    InterpreterResult result =
        md.interpret("1. First ordered list item\n" + "2. Another item", null);
    assertEquals(
        wrapHtmlWithMarkdownClassDiv(
            "<ol>\n"
                + "  <li>First ordered list item</li>\n"
                + "  <li>Another item</li>\n"
                + "</ol>"),
        result.message());
  }

  @Test
  public void testUnorderedList() {
    InterpreterResult result =
        md.interpret(
            "* Unordered list can use asterisks\n" + "- Or minuses\n" + "+ Or pluses", null);
    assertEquals(
        wrapHtmlWithMarkdownClassDiv(
            "<ul>\n"
                + "  <li>Unordered list can use asterisks</li>\n"
                + "  <li>Or minuses</li>\n"
                + "  <li>Or pluses</li>\n"
                + "</ul>"),
        result.message());
  }

  @Test
  public void testLinks() {
    InterpreterResult result =
        md.interpret(
            "[I'm an inline-style link](https://www.google.com)\n"
                + "\n"
                + "[I'm an inline-style link with title](https://www.google.com \"Google's Homepage\")\n"
                + "\n"
                + "[I'm a reference-style link][Arbitrary case-insensitive reference text]\n"
                + "\n"
                + "[I'm a relative reference to a repository file](../blob/master/LICENSE)\n"
                + "\n"
                + "[You can use numbers for reference-style link definitions][1]\n"
                + "\n"
                + "Or leave it empty and use the [link text itself].\n"
                + "\n"
                + "URLs and URLs in angle brackets will automatically get turned into links. \n"
                + "http://www.example.com or <http://www.example.com> and sometimes \n"
                + "example.com (but not on Github, for example).\n"
                + "\n"
                + "Some text to show that the reference links can follow later.\n"
                + "\n"
                + "[arbitrary case-insensitive reference text]: https://www.mozilla.org\n"
                + "[1]: http://slashdot.org\n"
                + "[link text itself]: http://www.reddit.com",
            null);
    assertEquals(
        wrapHtmlWithMarkdownClassDiv(
            "<p><a href=\"https://www.google.com\">I&rsquo;m an inline-style link</a></p>\n"
                + "<p><a href=\"https://www.google.com\" title=\"Google&#39;s Homepage\">I&rsquo;m an inline-style link with title</a></p>\n"
                + "<p><a href=\"https://www.mozilla.org\">I&rsquo;m a reference-style link</a></p>\n"
                + "<p><a href=\"../blob/master/LICENSE\">I&rsquo;m a relative reference to a repository file</a></p>\n"
                + "<p><a href=\"http://slashdot.org\">You can use numbers for reference-style link definitions</a></p>\n"
                + "<p>Or leave it empty and use the <a href=\"http://www.reddit.com\">link text itself</a>.</p>\n"
                + "<p>URLs and URLs in angle brackets will automatically get turned into links.<br/><a href=\"http://www.example.com\">http://www.example.com</a> or <a href=\"http://www.example.com\">http://www.example.com</a> and sometimes<br/>example.com (but not on Github, for example).</p>\n"
                + "<p>Some text to show that the reference links can follow later.</p>"),
        result.message());
  }

  @Test
  public void testInlineCode() {
    InterpreterResult result = md.interpret("Inline `code` has `back-ticks around` it.", null);
    assertEquals(
        wrapHtmlWithMarkdownClassDiv(
            "<p>Inline <code>code</code> has <code>back-ticks around</code> it.</p>"),
        result.message());
  }

  @Test
  public void testBlockQuotes() {
    InterpreterResult r1 =
        md.interpret(
            "> Blockquotes are very handy in email to emulate reply text.\n"
                + "> This line is part of the same quote.",
            null);
    assertEquals(
        wrapHtmlWithMarkdownClassDiv(
            "<blockquote>\n"
                + "  <p>Blockquotes are very handy in email to emulate reply text.<br/>This line is part of the same quote.</p>\n"
                + "</blockquote>"),
        r1.message());

    InterpreterResult r2 =
        md.interpret(
            "> This is a very long line that will still be quoted properly when it wraps. Oh boy let's keep writing to make sure this is long enough to actually wrap for everyone. Oh, you can *put* **Markdown** into a blockquote. ",
            null);
    assertEquals(
        wrapHtmlWithMarkdownClassDiv(
            "<blockquote>\n"
                + "  <p>This is a very long line that will still be quoted properly when it wraps. Oh boy let&rsquo;s keep writing to make sure this is long enough to actually wrap for everyone. Oh, you can <em>put</em> <strong>Markdown</strong> into a blockquote. </p>\n"
                + "</blockquote>"),
        r2.message());
  }

  @Test
  public void testSimpleTable() {
    InterpreterResult result =
        md.interpret(
            "Markdown | Less | Pretty\n"
                + "--- | --- | ---\n"
                + "*Still* | `renders` | **nicely**\n"
                + "1 | 2 | 3",
            null);

    assertEquals(
        wrapHtmlWithMarkdownClassDiv(
            "<table>\n"
                + "  <thead>\n"
                + "    <tr>\n"
                + "      <th>Markdown </th>\n"
                + "      <th>Less </th>\n"
                + "      <th>Pretty</th>\n"
                + "    </tr>\n"
                + "  </thead>\n"
                + "  <tbody>\n"
                + "    <tr>\n"
                + "      <td><em>Still</em> </td>\n"
                + "      <td><code>renders</code> </td>\n"
                + "      <td><strong>nicely</strong></td>\n"
                + "    </tr>\n"
                + "    <tr>\n"
                + "      <td>1 </td>\n"
                + "      <td>2 </td>\n"
                + "      <td>3</td>\n"
                + "    </tr>\n"
                + "  </tbody>\n"
                + "</table>"),
        result.message());
  }

  @Test
  public void testAlignedTable() {
    InterpreterResult result =
        md.interpret(
            "| First Header | Second Header |         Third Header |\n"
                + "| :----------- | :-----------: | -------------------: |\n"
                + "| First row    |      Data     | Very long data entry |\n"
                + "| Second row   |    **Cell**   |               *Cell* |",
            null);

    assertEquals(
        wrapHtmlWithMarkdownClassDiv(
            "<table>\n"
                + "  <thead>\n"
                + "    <tr>\n"
                + "      <th align=\"left\">First Header </th>\n"
                + "      <th align=\"center\">Second Header </th>\n"
                + "      <th align=\"right\">Third Header </th>\n"
                + "    </tr>\n"
                + "  </thead>\n"
                + "  <tbody>\n"
                + "    <tr>\n"
                + "      <td align=\"left\">First row </td>\n"
                + "      <td align=\"center\">Data </td>\n"
                + "      <td align=\"right\">Very long data entry </td>\n"
                + "    </tr>\n"
                + "    <tr>\n"
                + "      <td align=\"left\">Second row </td>\n"
                + "      <td align=\"center\"><strong>Cell</strong> </td>\n"
                + "      <td align=\"right\"><em>Cell</em> </td>\n"
                + "    </tr>\n"
                + "  </tbody>\n"
                + "</table>"),
        result.message());
  }
}
