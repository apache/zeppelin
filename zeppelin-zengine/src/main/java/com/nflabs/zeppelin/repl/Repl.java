package com.nflabs.zeppelin.repl;


import java.util.Properties;

import com.nflabs.zeppelin.repl.ReplResult;


public abstract class Repl {
	
	private Properties property;
	
	public Repl(Properties property){
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
	public abstract ReplResult interpret(String st);
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
