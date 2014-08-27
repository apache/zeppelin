package com.nflabs.zeppelin.server.form;

import com.nflabs.zeppelin.server.form.Form.Type;

public class Base {
	private Object value;

	public Type type;
	
	public Base(){		
	}
	
	public Base(Type type, Object value) {
		this.type = type;
		this.value = value;
	}
	
	public Object getValue(){
		return value;
	}
	
	public void setValue(Object value){
		this.value = value;
	}
}
