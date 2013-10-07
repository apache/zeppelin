package com.nflabs.zeppelin.zai;

import java.net.URI;

import com.nflabs.zeppelin.zdd.ZDD;

public class Output {
	ZDD [] data;
	Resource [] resources;
	
	public Output(ZDD[] data, Resource[] resources) {
		super();
		this.data = data;
		this.resources = resources;
	}

	
	public ZDD[] getData() {
		return data;
	}
	public void setData(ZDD[] data) {
		this.data = data;
	}
	public Resource[] getResources() {
		return resources;
	}
	public void setResources(Resource[] resources) {
		this.resources = resources;
	}
	
	
}
