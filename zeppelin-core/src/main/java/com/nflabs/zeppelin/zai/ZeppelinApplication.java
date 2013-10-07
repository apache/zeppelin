package com.nflabs.zeppelin.zai;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.zdd.ZDD;
import com.nflabs.zeppelin.zrt.ZeppelinRuntime;

public abstract class ZeppelinApplication {
	
	public ZeppelinApplication(ZeppelinRuntime runtime){
		this.runtime = runtime;
	}
	
	private ZeppelinApplication(){
		
	}

	public ZeppelinRuntime getRuntime(){
		return runtime;
	}
	
	private ZeppelinRuntime runtime;


	private Param[] params;

	public abstract String name();
	public abstract String version();
	public abstract String description();
	
	
	public abstract ParamSpec [] getParamSpec();
	public abstract ColumnSpec [][] getInputSpec();
	public abstract ColumnSpec [][] getOutputSpec();
	

	public Output run(Input input) throws ParamSpecException{
		this.params = input.getParams();
		return execute(input);
	}
	
	
	
	public Object getParam(String name) throws ParamSpecException{
		if(params==null) return null;
		
		// first find param
		Param param = null;		
		for(Param p : params){
			if(name.equals(p.getName())){
				param = p;
				break;
			}
		}
		if(param==null) return null;
		
		ParamSpec[] paramSpecs = getParamSpec();
		if(paramSpecs!=null){
			for(ParamSpec ps : getParamSpec()){
				if(name.equals(ps.getName())){
					return ps.validate(param.getValue());
				}
			}
		}
		
		return param.getValue();
	}
	
	
	protected abstract Output execute(Input input) throws ParamSpecException;
}
