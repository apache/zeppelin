package com.nflabs.zeppelin.zql;

public class ZException extends Exception{
	public ZException(Exception e){
		super(e);
	}
	public ZException(String m){
		super(m);
	}

}
