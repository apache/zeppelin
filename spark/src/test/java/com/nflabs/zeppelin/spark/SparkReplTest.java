package com.nflabs.zeppelin.spark;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.repl.Repl.Result;


public class SparkReplTest {

	private SparkRepl repl;

	@Before
	public void setUp() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();		
		repl = new SparkRepl(new StringReader(""), new PrintWriter(new OutputStreamWriter(out)));
		repl.initialize();
	}

	@After
	public void tearDown() throws Exception {
		repl.destroy();
	}

	@Test
	public void testBasicRepl() {
		assertEquals(Result.SUCCESS, repl.interpret("val a = 1\nval b = 2"));
		assertEquals(1, repl.getValue("a"));
		assertEquals(2, repl.getValue("b"));
		repl.interpret("val ver = sc.version");
		assertEquals("1.0.2", repl.getValue("ver"));
	}
}
