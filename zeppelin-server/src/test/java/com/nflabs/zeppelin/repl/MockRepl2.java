package com.nflabs.zeppelin.repl;

import java.util.Properties;

import com.nflabs.zeppelin.repl.Repl.FormType;

public class MockRepl2 extends Repl{

	public MockRepl2(Properties property) {
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
		return new ReplResult(ReplResult.Code.SUCCESS, "repl2: "+st);
	}

	@Override
	public void cancel() {
	}

	@Override
	public void bindValue(String name, Object o) {
	}
	
	@Override
	public FormType getFormType() {
		return FormType.SIMPLE;
	}
}
