package com.nflabs.zeppelin.driver;

public class ZeppelinDriverException extends RuntimeException{
    private static final long serialVersionUID = 8523926733092740130L;

    public ZeppelinDriverException(Throwable e){
		super(e);
	}

	public ZeppelinDriverException(String m) {
		super(m);
	}
}
