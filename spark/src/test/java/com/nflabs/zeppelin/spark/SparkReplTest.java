package com.nflabs.zeppelin.spark;

import static org.junit.Assert.*;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.repl.ReplResult;


public class SparkReplTest {

	private SparkRepl repl;

	@Before
	public void setUp() throws Exception {
		repl = new SparkRepl(new Properties());
		repl.initialize();
	}

	@After
	public void tearDown() throws Exception {
		repl.destroy();
	}

	@Test
	public void testBasicRepl() {
		assertEquals(ReplResult.Code.SUCCESS, repl.interpret("val a = 1\nval b = 2").code());
		assertEquals(1, repl.getValue("a"));
		assertEquals(2, repl.getValue("b"));
		repl.interpret("val ver = sc.version");
		assertEquals("1.0.2", repl.getValue("ver"));
		assertEquals("res0: String = 1.0.2\n", repl.interpret("sc.version").message());
	}
}
