package com.nflabs.zeppelin.interpreter.mock;

import java.util.Properties;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterFactory;

public class MockInterpreterFactory extends InterpreterFactory {

	public MockInterpreterFactory(ZeppelinConfiguration conf) {
		super(conf);
	}
	
	public Interpreter createRepl(String replName, Properties properties) {
		if("MockRepl1".equals(replName) || replName==null) {
			return new MockInterpreter1(properties);
		} else if("MockRepl2".equals(replName)) {
			return new MockInterpreter2(properties);
		} else {
			return new MockInterpreter1(properties);
		}
	}
}
