package com.nflabs.zeppelin.interpreter;


import java.util.Properties;

import com.nflabs.zeppelin.interpreter.InterpreterResult;


public abstract class Interpreter {
	
	private Properties property;
	
	public Interpreter(Properties property){
		this.property = property;
	}
	
	public static enum FormType {
		NATIVE,
		SIMPLE,
		NONE
	}
	
	public abstract void initialize();
	public abstract void destroy();
	public abstract Object getValue(String name);
	public abstract InterpreterResult interpret(String st);
	public abstract void cancel();
	public abstract void bindValue(String name, Object o);
	public abstract FormType getFormType();

	public Properties getProperty() {
		return property;
	}

	public void setProperty(Properties property) {
		this.property = property;
	}
	
	
}
