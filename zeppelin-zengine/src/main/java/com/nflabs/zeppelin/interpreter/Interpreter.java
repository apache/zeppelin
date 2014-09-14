package com.nflabs.zeppelin.interpreter;


import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.interpreter.InterpreterResult;


public abstract class Interpreter {
	static Logger logger = LoggerFactory.getLogger(Interpreter.class);
	private Properties property;
	
	public Interpreter(Properties property){
		this.property = property;
	}
	
	public static enum FormType {
		NATIVE,
		SIMPLE,
		NONE
	}
	
	public static enum SchedulingMode {
		FIFO,
		PARALLEL
	}
	
	public static Map<String, String> registeredInterpreters = Collections.synchronizedMap(new HashMap<String, String>());
	
	public static void register(String name, String className) {
		registeredInterpreters.put(name, className);
	}
	
	public abstract void open();
	public abstract void close();
	public abstract Object getValue(String name);
	public abstract InterpreterResult interpret(String st);
	public abstract void cancel();
	public abstract void bindValue(String name, Object o);
	public abstract FormType getFormType();
	public abstract int getProgress();
	public abstract SchedulingMode getSchedulingMode();
	public abstract List<String> completion(String buf, int cursor);

	public Properties getProperty() {
		return property;
	}

	public void setProperty(Properties property) {
		this.property = property;
	}
	
	
}
