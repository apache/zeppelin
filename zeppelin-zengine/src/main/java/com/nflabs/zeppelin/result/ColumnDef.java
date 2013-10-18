package com.nflabs.zeppelin.result;

public class ColumnDef {
	String name;
	int type;
	String typeName;
	public ColumnDef(String name, int type, String typeName) {
		super();
		this.name = name;
		this.type = type;
		this.typeName = typeName;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getType() {
		return type;
	}
	public void setType(int type) {
		this.type = type;
	}
	public String getTypeName() {
		return typeName;
	}
	public void setTypeName(String typeName) {
		this.typeName = typeName;
	}
	
	
}
