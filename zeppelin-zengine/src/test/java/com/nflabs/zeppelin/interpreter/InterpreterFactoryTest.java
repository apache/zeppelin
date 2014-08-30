package com.nflabs.zeppelin.interpreter;

import static org.junit.Assert.*;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterFactory;

public class InterpreterFactoryTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testBasic() {
		InterpreterFactory factory = new InterpreterFactory(ZeppelinConfiguration.create());
		Interpreter repl1 = factory.createRepl("mock", "com.nflabs.zeppelin.interpreter.mock.MockInterpreter", new Properties());
		repl1.bindValue("a", 1);
		
		assertEquals(repl1.getValue("a"), 1);
	}

}
