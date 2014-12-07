package com.nflabs.zeppelin.interpreter;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterFactory;
import com.nflabs.zeppelin.interpreter.mock.MockInterpreter1;
import com.nflabs.zeppelin.interpreter.mock.MockInterpreter2;

public class InterpreterFactoryTest {

	private InterpreterFactory factory;

  @Before
	public void setUp() throws Exception {
	  MockInterpreter1.register("mock1", "com.nflabs.zeppelin.interpreter.mock.MockInterpreter1");
	  MockInterpreter2.register("mock2", "com.nflabs.zeppelin.interpreter.mock.MockInterpreter2");
	  System.setProperty(ConfVars.ZEPPELIN_INTERPRETERS.getVarName(), "com.nflabs.zeppelin.interpreter.mock.MockInterpreter1,com.nflabs.zeppelin.interpreter.mock.MockInterpreter2");
	  factory = new InterpreterFactory(ZeppelinConfiguration.create());
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testBasic() {
	  List<String> all = factory.getDefaultInterpreterList();
	  
		// get interpreter
		Interpreter repl1 = factory.get(all.get(0)).getInterpreter();
		repl1.bindValue("a", 1);		
		assertEquals(repl1.getValue("a"), 1);
		
		// try to get unavailable interpreter
		assertNull(factory.get("unknown"));
		
		// restart interpreter
		factory.restart(all.get(0));
		repl1 = factory.get(all.get(0)).getInterpreter();
		assertEquals(repl1.getValue("a"), null);
	}

  @Test
  public void testFactoryDefaultList() {
    List<String> all = factory.getDefaultInterpreterList();
    assertEquals(2, all.size());
    assertEquals(factory.get(all.get(0)).getClassName(), "com.nflabs.zeppelin.interpreter.mock.MockInterpreter1");
  }

}
