package com.nflabs.zeppelin.zai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nflabs.zeppelin.zdd.ZDD;

public abstract class ZeppelinApplication {
	
	private Param[] params;

	public abstract String name();
	public abstract String version();
	public abstract String description();
	
	
	public abstract ParamSpec [] getParamSpec();
	public abstract ColumnSpec [][] getInputSpec();
	public abstract ColumnSpec [][] getOutputSpec();
	

	public ZDD [] run(ZDD [] input, Param [] params){
		this.params = params;
		return execute(input);
	}
	
	public Object getParam(String name) throws ParamSpecException{
		// first find param
		Param param = null;
		for(Param p : params){
			if(name.equals(p.getName())){
				param = p;
				break;
			}
		}
		if(param==null) return null;
		
		for(ParamSpec ps : getParamSpec()){
			if(name.equals(ps.getName())){
				return ps.validate(param.getValue());
			}
		}
		
		return param.getValue();
	}
	
	
	protected abstract ZDD [] execute(ZDD [] input);
}
