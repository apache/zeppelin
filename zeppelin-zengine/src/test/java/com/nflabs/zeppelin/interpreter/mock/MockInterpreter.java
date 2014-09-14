package com.nflabs.zeppelin.interpreter.mock;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterResult;

public class MockInterpreter extends Interpreter {
	
	public MockInterpreter(Properties property) {
		super(property);
	}

	static Map<String, Object> vars = new HashMap<String, Object>();

	@Override
	public void open() {
	}

	@Override
	public void close() {
	}

	@Override
	public Object getValue(String name) {
		return vars.get(name);
	}

	@Override
	public InterpreterResult interpret(String st) {
		return new InterpreterResult(InterpreterResult.Code.SUCCESS, st);
	}

	@Override
	public void bindValue(String name, Object o) {
		vars.put(name, o);
	}

	@Override
	public void cancel() {
	}

	@Override
	public FormType getFormType() {
		return FormType.SIMPLE;
	}

	@Override
	public int getProgress() {
		return 0;
	}

	@Override
	public SchedulingMode getSchedulingMode() {
		return SchedulingMode.FIFO;
	}

}
