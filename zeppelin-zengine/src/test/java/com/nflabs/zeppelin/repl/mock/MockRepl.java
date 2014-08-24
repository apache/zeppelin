package com.nflabs.zeppelin.repl.mock;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.nflabs.zeppelin.repl.Repl;
import com.nflabs.zeppelin.repl.ReplResult;

public class MockRepl extends Repl {
	
	public MockRepl(Properties property) {
		super(property);
	}

	static Map<String, Object> vars = new HashMap<String, Object>();

	@Override
	public void initialize() {
	}

	@Override
	public void destroy() {
	}

	@Override
	public Object getValue(String name) {
		return vars.get(name);
	}

	@Override
	public ReplResult interpret(String st) {
		return new ReplResult(ReplResult.Code.SUCCESS);
	}

	@Override
	public void bindValue(String name, Object o) {
		vars.put(name, o);
	}

	@Override
	public void cancel() {
	}

}
