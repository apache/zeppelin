package com.nflabs.zeppelin.socket;

import java.util.HashMap;
import java.util.Map;

public class Message {
	public static enum OP {
		GET_NOTE,      // [c-s] client load note
		               // @param id note id
		
		NOTE,          // [s-c] note info 
		               // @param note serlialized Note object
		
		NEW_NOTE,      // [c-s] create new notebook
		
		RUN_PARAGRAPH, // [c-s] run paragraph
		               // @param id paragraph id
		
		LIST_NOTES,    // [c-s] ask list of note
		
		NOTES_INFO,    // [s-c] list of note infos
		               // @param notes serialized List<NoteInfo> object
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
