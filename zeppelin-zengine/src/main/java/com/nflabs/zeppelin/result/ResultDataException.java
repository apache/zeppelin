package com.nflabs.zeppelin.result;

public class ResultDataException extends Exception{
	public ResultDataException(Exception e){
		super(e);
	}
	
	public ResultDataException(String m){
		super(m);
	}
}
