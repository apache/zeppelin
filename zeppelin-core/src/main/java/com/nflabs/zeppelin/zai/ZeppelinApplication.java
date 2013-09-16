package com.nflabs.zeppelin.zai;

import java.util.List;

import com.nflabs.zeppelin.zdd.ZDD;

public abstract class ZeppelinApplication {
	
	public abstract String name();
	public abstract String version();
	public abstract String description();
	
	
	public abstract ParamSpec [] getParamSpec();
	public abstract ColumnSpec [][] getInputSpec();
	public abstract ColumnSpec [][] getOutputSpec();
	

	public ZDD [] run(ZDD [] input, Param [] params){
		return execute(input, params);
	}
	
	protected abstract ZDD [] execute(ZDD [] input, Param [] params);
}
