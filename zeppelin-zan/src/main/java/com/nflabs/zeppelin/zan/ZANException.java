package com.nflabs.zeppelin.zan;

public class ZANException extends Exception{
	public ZANException(Throwable e){
		super(e);
	}
	
	public ZANException(String m){
		super(m);
	}

}
