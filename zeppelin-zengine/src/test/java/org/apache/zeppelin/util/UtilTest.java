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

package org.apache.zeppelin.util;

import org.apache.zeppelin.util.Util;

import junit.framework.TestCase;

public class UtilTest extends TestCase {

	@Override
  protected void setUp() throws Exception {
		super.setUp();
	}

	@Override
  protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testSplitIncludingToken() {
		String[] token = Util.split("hello | \"world '>|hehe\" > next >> sink", new String[]{"|", ">>",  ">"}, true);
		assertEquals(7, token.length);
		assertEquals(" \"world '>|hehe\" ", token[2]);
	}

	public void testSplitExcludingToken() {
		String[] token = Util.split("hello | \"world '>|hehe\" > next >> sink", new String[]{"|", ">>",  ">"}, false);
		assertEquals(4, token.length);
		assertEquals(" \"world '>|hehe\" ", token[1]);
	}

	public void testSplitWithSemicolonEnd(){
		String[] token = Util.split("show tables;", ';');
		assertEquals(1, token.length);
		assertEquals("show tables", token[0]);
	}

	public void testEscapeTemplate(){
		String[] token = Util.split("select * from <%=table%> limit 1 > output", '>');
		assertEquals(2, token.length);
		assertEquals("output", token[1]);
	}

	public void testSplit(){
		String [] op = new String[]{";", "|", ">>", ">"};

		String str = "CREATE external table news20b_train (\n"+
			"	rowid int,\n"+
			"   label int,\n"+
			"   features ARRAY<STRING>\n"+
			")\n"+
			"ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t' \n"+
			"COLLECTION ITEMS TERMINATED BY \",\" \n"+
			"STORED AS TEXTFILE;\n";
		Util.split(str, op, true);

	}

	public void testSplitDifferentBlockStartEnd(){
		String [] op = new String[]{";", "|", ">>", ">"};
		String escapeSeq = "\"',;<%>!";
		char escapeChar = '\\';
		String [] blockStart = new String[]{ "\"", "'", "<%", "<", "!"};
		String [] blockEnd = new String[]{ "\"", "'", "%>", ">", ";" };
		String [] t = Util.split("!echo a;!echo b;", escapeSeq, escapeChar, blockStart, blockEnd, op, true);
		assertEquals(4, t.length);
		assertEquals("!echo a;", t[0]);
		assertEquals(";", t[1]);
		assertEquals("!echo b;", t[2]);
		assertEquals(";", t[3]);
	}

	public void testNestedBlock(){
		String [] op = new String[]{";", "|", ">>", ">"};
		String escapeSeq = "\"',;<%>!";
		char escapeChar = '\\';
		String [] blockStart = new String[]{ "\"", "'", "<%", "N_<", "<", "!"};
		String [] blockEnd = new String[]{ "\"", "'", "%>", "N_>", ";", ";" };
		String [] t = Util.split("array <STRUCT<STRING>> tags|aa", escapeSeq, escapeChar, blockStart, blockEnd, op, true);
		assertEquals(3, t.length);
		assertEquals("array <STRUCT<STRING>> tags", t[0]);
		assertEquals("aa", t[2]);
	}
}
