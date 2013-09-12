package com.nflabs.zeppelin.zai;

import java.util.List;

import com.nflabs.zeppelin.zdd.ZDD;

public abstract class ZeppelinApplication {
	
	public ZeppelinApplication(){
		
	}
	
	public abstract String name();
	public abstract String version();
	
	
	public abstract ParamSpec getParamSpec();	
	public abstract SchemaSpec getInputSpec();
	public abstract SchemaSpec getOutputSpec();
	

	public abstract List<ZDD> execute(List<ZDD> input, List<Param> params);
}
