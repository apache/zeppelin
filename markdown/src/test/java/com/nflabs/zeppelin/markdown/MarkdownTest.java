package com.nflabs.zeppelin.markdown;

import static org.junit.Assert.*;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.repl.ReplResult;

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
		md.initialize();
		ReplResult result = md.interpret("This is ~~deleted~~ text");
		assertEquals("<p>This is <s>deleted</s> text</p>\n", result.message());
	}

}
