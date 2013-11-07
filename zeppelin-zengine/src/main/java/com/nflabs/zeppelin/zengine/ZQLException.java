package com.nflabs.zeppelin.zengine;

public class ZQLException extends Exception{
    private static final long serialVersionUID = -1706289522346311416L;

    public ZQLException(Exception e){
		super(e);
	}
	
	public ZQLException(String m){
		super(m);
	}

}
