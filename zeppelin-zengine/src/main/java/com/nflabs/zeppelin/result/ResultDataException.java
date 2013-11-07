package com.nflabs.zeppelin.result;

public class ResultDataException extends Exception{
    private static final long serialVersionUID = -6503372200666271740L;

    public ResultDataException(Exception e){
		super(e);
	}
	
	public ResultDataException(String m){
		super(m);
	}
}
