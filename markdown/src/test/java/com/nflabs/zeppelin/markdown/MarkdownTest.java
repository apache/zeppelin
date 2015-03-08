package com.nflabs.zeppelin.markdown;

import static org.junit.Assert.assertEquals;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.interpreter.InterpreterResult;

public class MarkdownTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() {
		Markdown md = new Markdown(new Properties());
		md.open();
		InterpreterResult result = md.interpret("This is ~~deleted~~ text", null);
		assertEquals("<p>This is <s>deleted</s> text</p>\n", result.message());
		System.out.println(MarkdownTest.class.getName());
	}

}
