package com.nflabs.zeppelin.zengine;

public class ZException extends Exception{
	public ZException(Throwable e){
		super(e);
	}
	public ZException(String m){
		super(m);
	}

}
