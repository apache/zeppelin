package com.nflabs.zeppelin.repl;

import java.util.Properties;

public class MockRepl1 extends Repl{

	public MockRepl1(Properties property) {
		super(property);
	}

	@Override
	public void initialize() {
	}

	@Override
	public void destroy() {
	}

	@Override
	public Object getValue(String name) {
		return null;
	}

	@Override
	public ReplResult interpret(String st) {
		return new ReplResult(ReplResult.Code.SUCCESS, "repl1: "+st);
	}

	@Override
	public void cancel() {
	}

	@Override
	public void bindValue(String name, Object o) {
	}
}
