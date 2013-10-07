package com.nflabs.zeppelin.zai;

import java.net.URI;

import com.nflabs.zeppelin.zdd.ZDD;

public class Input {
	ZDD [] data;
	Param [] params;
	Resource [] resources;
	public Input(ZDD[] data, Param[] params, Resource[] resources) {
		super();
		this.data = data;
		this.params = params;
		this.resources = resources;
	}
	public ZDD[] getData() {
		return data;
	}
	public void setData(ZDD[] data) {
		this.data = data;
	}
	public Param[] getParams() {
		return params;
	}
	public void setParams(Param[] params) {
		this.params = params;
	}
	public Resource[] getResources() {
		return resources;
	}
	public void setResources(Resource[] resources) {
		this.resources = resources;
	}
	
	
}
