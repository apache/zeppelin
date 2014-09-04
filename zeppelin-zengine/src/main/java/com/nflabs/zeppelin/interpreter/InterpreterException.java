package com.nflabs.zeppelin.interpreter;

public class InterpreterException extends RuntimeException {
	
    public InterpreterException(Throwable e){
		super(e);
	}

	public InterpreterException(String m) {
		super(m);
	}

}
