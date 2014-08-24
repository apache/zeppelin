package com.nflabs.zeppelin.repl;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;

public class ReplFactoryTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testBasic() {
		ReplFactory factory = new ReplFactory(ZeppelinConfiguration.create());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Repl repl1 = factory.createRepl("mock", "com.nflabs.zeppelin.repl.mock.MockRepl", null, new OutputStreamWriter(out));
		repl1.bindValue("a", 1);
		
		assertEquals(repl1.getValue("a"), 1);
	}

}
