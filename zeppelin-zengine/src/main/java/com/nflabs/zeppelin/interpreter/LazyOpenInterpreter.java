package com.nflabs.zeppelin.interpreter;

import java.util.Properties;

public class LazyOpenInterpreter extends Interpreter{

	private Interpreter intp;
	boolean opened = false;

	public LazyOpenInterpreter(Interpreter intp) {
		super(intp.getProperty());
		this.intp = intp;
	}

	@Override
	public void open() {
		if(opened==true) {
			return;
		}
		
		synchronized(this){
			if(opened==false){
				intp.open();
				opened = true;
			}
		}
	}

	@Override
	public void close() {
		synchronized(this){
			if(opened==true){
				intp.close();
				opened = false;
			}
		}
	}

	@Override
	public Object getValue(String name) {
		open();
		return intp.getValue(name);
	}

	@Override
	public InterpreterResult interpret(String st) {
		open();
		return intp.interpret(st);
	}

	@Override
	public void cancel() {
		open();
		intp.cancel();
	}

	@Override
	public void bindValue(String name, Object o) {
		open();
		intp.bindValue(name, o);
	}

	@Override
	public FormType getFormType() {
		return intp.getFormType();
	}

	@Override
	public int getProgress() {
		open();
		return intp.getProgress();
	}

	@Override
	public SchedulingMode getSchedulingMode() {
		return intp.getSchedulingMode();
	}

}
