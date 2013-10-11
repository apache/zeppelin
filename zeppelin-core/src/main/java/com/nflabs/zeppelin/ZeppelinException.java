package com.nflabs.zeppelin;

public class ZeppelinException extends Exception{
	public ZeppelinException(Throwable e){
		super(e);
	}
	
	public ZeppelinException(String s){
		super(s);
	}
}
