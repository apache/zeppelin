package com.nflabs.zeppelin.repl.mock;

import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import com.nflabs.zeppelin.repl.Repl;

public class MockRepl extends Repl {
	
	public MockRepl(Reader reader, Writer writer) {
		super(reader, writer);
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
	public Result interpret(String st) {
		return Result.SUCCESS;
	}

	@Override
	public void bindValue(String name, Object o) {
		vars.put(name, o);
	}

	@Override
	public void cancel() {
	}

}
