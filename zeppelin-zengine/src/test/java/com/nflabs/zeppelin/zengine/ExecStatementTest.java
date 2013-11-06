package com.nflabs.zeppelin.zengine;

import com.nflabs.zeppelin.result.Result;

import junit.framework.TestCase;

public class ExecStatementTest extends TestCase {

	protected void setUp() throws Exception {
		super.setUp();
		Z.configure();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	
	public void testExec() throws ZException{
		ExecStatement e = new ExecStatement("!echo \"hello world\"");
		Result result = e.execute().result();
		assertEquals("hello world", result.getRows().get(0)[0]);
	}
}
