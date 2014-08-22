package com.nflabs.zeppelin.repl;

public class ReplException extends RuntimeException {
	
    public ReplException(Throwable e){
		super(e);
	}

	public ReplException(String m) {
		super(m);
	}

}
