package com.nflabs.zeppelin.repl;

import java.io.Reader;
import java.io.Writer;


public abstract class Repl {
	public static enum Result {
		SUCCESS,
		INCOMPLETE,
		ERROR
	}
	private Reader reader;
	private Writer writer;
	
	public Repl(Reader reader, Writer writer){
		this.reader = reader;
		this.writer = writer;
	}
	
	public abstract void initialize();
	public abstract void destroy();
	public abstract Object getValue(String name);
	public abstract Result interpret(String st);
	public abstract void cancel();
	public abstract void bindValue(String name, Object o);
	
	public Reader getReader() {
		return reader;
	}

	public void setReader(Reader reader) {
		this.reader = reader;
	}

	public Writer getWriter() {
		return writer;
	}

	public void setWriter(Writer writer) {
		this.writer = writer;
	}
	
	
	
}
