package com.nflabs.zeppelin.server;

import java.util.List;

/**
 * Persist ZQL Job order (nested) information 
 */
public class ZQLJobTree{
	private String id;
	private List<ZQLJobTree> children;
	
	public ZQLJobTree(){		
	}

	public ZQLJobTree(String id){
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<ZQLJobTree> getChildren() {
		return children;
	}

	public void setChildren(List<ZQLJobTree> children) {
		this.children = children;
	}
}
