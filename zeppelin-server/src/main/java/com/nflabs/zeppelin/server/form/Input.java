package com.nflabs.zeppelin.server.form;

import com.nflabs.zeppelin.server.form.Form.Type;

public class Input implements Base {
	private Object value;

	public final Type type = Type.INPUT;
	
	public Input(Object value) {
		this.value = value;
	}
	
	public Object getValue(){
		return value;
	}
	
	public void setValue(Object value){
		this.value = value;
	}
}
