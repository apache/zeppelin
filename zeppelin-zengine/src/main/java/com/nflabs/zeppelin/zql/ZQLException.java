package com.nflabs.zeppelin.zql;

public class ZQLException extends Exception{
	public ZQLException(Exception e){
		super(e);
	}
	
	public ZQLException(String m){
		super(m);
	}

}
