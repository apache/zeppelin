package com.nflabs.zeppelin.spark;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.spark.SparkRepl.Result;

public class SparkReplTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();		
		SparkRepl repl = new SparkRepl(null, new PrintWriter(new OutputStreamWriter(out)));
		assertEquals(Result.SUCCESS, repl.interpret("val a = 1"));		
		repl.interpret("val ver = sc.version");
		assertEquals("1.0.2", repl.getValue("ver"));
		
	}

}
