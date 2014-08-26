package com.nflabs.zeppelin.socket;

import java.util.HashMap;
import java.util.Map;

public class Message {
	public static enum OP {
		GET_NOTE,      // [c-s] client load note
		               // @param noteId
		
		NOTE,          // [s-c] note info 
		               // @param note
		
		NEW_NOTE,      // [c-s] create new notebook
	}	
	public OP op;
	public Map<String, Object> data = new HashMap<String, Object>();;
	
	public Message(OP op){
		this.op = op;
	}
	
	public Message put(String k, Object v){
		data.put(k, v);
		return this;
	}
	
	public Object get(String k){
		return data.get(k);
	}
}
