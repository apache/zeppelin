package com.nflabs.zeppelin.interpreter.mock;

import java.util.List;
import java.util.Properties;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.Interpreter.FormType;
import com.nflabs.zeppelin.interpreter.Interpreter.SchedulingMode;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;

public class MockInterpreter2 extends Interpreter{

	public MockInterpreter2(Properties property) {
		super(property);
	}

	@Override
	public void open() {
	}

	@Override
	public void close() {
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

	@Override
	public int getProgress() {
		return 0;
	}
	
	@Override
	public Scheduler getScheduler() {
		return SchedulerFactory.singleton().createOrGetFIFOScheduler("test_"+this.hashCode());
	}

	@Override
	public List<String> completion(String buf, int cursor) {
		return null;
	}
}
