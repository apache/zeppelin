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

}
