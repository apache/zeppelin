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
import org.apache.zeppelin.markdown.Markdown;
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
	public void testStrikethrough() {
		InterpreterResult result = md.interpret("This is ~~deleted~~ text", null);
		assertEquals("<p>This is <del>deleted</del> text</p>", result.message());
	}

	@Test
	public void testItalics() {
		InterpreterResult result = md.interpret("This is *italics* text", null);
		assertEquals("<p>This is <em>italics</em> text</p>", result.message());
	}

	@Test
	public void testStrongEmphasis() {
		InterpreterResult result = md.interpret("This is **strong emphasis** text", null);
		assertEquals("<p>This is <strong>strong emphasis</strong> text</p>", result.message());
	}

	@Test
	public void testSimpleTable() {
		InterpreterResult result = md.interpret(
			"Markdown | Less | Pretty\n" +
			"--- | --- | ---\n" +
			"*Still* | `renders` | **nicely**\n" +
			"1 | 2 | 3", null);

		assertEquals(
			"<table>\n" +
				"  <thead>\n" +
				"    <tr>\n" +
				"      <th>Markdown </th>\n" +
				"      <th>Less </th>\n" +
				"      <th>Pretty</th>\n" +
				"    </tr>\n" +
				"  </thead>\n" +
				"  <tbody>\n" +
				"    <tr>\n" +
				"      <td><em>Still</em> </td>\n" +
				"      <td><code>renders</code> </td>\n" +
				"      <td><strong>nicely</strong></td>\n" +
				"    </tr>\n" +
				"    <tr>\n" +
				"      <td>1 </td>\n" +
				"      <td>2 </td>\n" +
				"      <td>3</td>\n" +
				"    </tr>\n" +
				"  </tbody>\n" +
				"</table>",
			result.message());
	}

	@Test
	public void testAlignedTable() {
		InterpreterResult result = md.interpret(
			"| First Header | Second Header |         Third Header |\n" +
			"| :----------- | :-----------: | -------------------: |\n" +
			"| First row    |      Data     | Very long data entry |\n" +
			"| Second row   |    **Cell**   |               *Cell* |",
			null
		);

		assertEquals(
			"<table>\n" +
				"  <thead>\n" +
				"    <tr>\n" +
				"      <th align=\"left\">First Header </th>\n" +
				"      <th align=\"center\">Second Header </th>\n" +
				"      <th align=\"right\">Third Header </th>\n" +
				"    </tr>\n" +
				"  </thead>\n" +
				"  <tbody>\n" +
				"    <tr>\n" +
				"      <td align=\"left\">First row </td>\n" +
				"      <td align=\"center\">Data </td>\n" +
				"      <td align=\"right\">Very long data entry </td>\n" +
				"    </tr>\n" +
				"    <tr>\n" +
				"      <td align=\"left\">Second row </td>\n" +
				"      <td align=\"center\"><strong>Cell</strong> </td>\n" +
				"      <td align=\"right\"><em>Cell</em> </td>\n" +
				"    </tr>\n" +
				"  </tbody>\n" +
				"</table>",
			result.message()
		);
	}
}
