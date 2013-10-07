package com.nflabs.zeppelin.zai;

public class ApplicationExecutionException extends Exception{

	public ApplicationExecutionException(String m){
		super(m);
	}
	
	public ApplicationExecutionException(Throwable t){
		super(t);
	}
}
