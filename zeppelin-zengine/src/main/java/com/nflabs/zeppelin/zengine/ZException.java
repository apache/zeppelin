package com.nflabs.zeppelin.zengine;

public class ZException extends Exception{
    private static final long serialVersionUID = -2039301692456478552L;
    
    public ZException(Throwable e){
		super(e);
	}
	public ZException(String m){
		super(m);
	}

}
