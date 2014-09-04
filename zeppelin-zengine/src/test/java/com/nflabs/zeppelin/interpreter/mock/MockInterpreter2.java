package com.nflabs.zeppelin.interpreter.mock;

import java.util.Properties;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.Interpreter.FormType;

public class MockInterpreter2 extends Interpreter{

	public MockInterpreter2(Properties property) {
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
	public InterpreterResult interpret(String st) {
		return new InterpreterResult(InterpreterResult.Code.SUCCESS, "repl2: "+st);
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
