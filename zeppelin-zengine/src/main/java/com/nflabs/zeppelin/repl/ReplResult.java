package com.nflabs.zeppelin.repl;

import java.io.Serializable;

public class ReplResult implements Serializable{
	public static enum Code {
		SUCCESS,
		INCOMPLETE,
		ERROR
	}
	
	Code code;
	String msg;
	Serializable data;
	
	public ReplResult(Code code) {
		this.code = code;
		this.msg = null;
	}
	
	public ReplResult(Code code, String msg) {
		this.code = code;
		this.msg = msg;
	}
	
	public ReplResult(Code code, String msg, Serializable data) {
		this.code = code;
		this.msg = msg;
		this.data = data;
	}
	
	public Code code(){
		return code;
	}
	
	public String message(){
		return msg;
	}
	
	public Object data(){
		return data;
	}
}
