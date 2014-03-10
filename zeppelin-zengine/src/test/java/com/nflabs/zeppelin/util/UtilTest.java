package com.nflabs.zeppelin.util;

import junit.framework.TestCase;

public class UtilTest extends TestCase {

	protected void setUp() throws Exception {
		super.setUp();
	}

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
