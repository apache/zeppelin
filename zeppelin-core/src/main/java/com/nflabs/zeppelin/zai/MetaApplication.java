package com.nflabs.zeppelin.zai;

import java.util.List;

import com.nflabs.zeppelin.zdd.ZDD;
import com.nflabs.zeppelin.zrt.ZeppelinRuntime;

public class MetaApplication extends ZeppelinApplication {
	String name;
	String version;
	String description;
	ParamSpec [] paramSpec;
	ColumnSpec [][] inputSpec;
	ColumnSpec [][] outputSpec;
	
	public MetaApplication(ZeppelinRuntime zr){
		super(zr);
	}
	
	
	@Override
	public String name() {
		return name;
	}

	@Override
	public String version() {
		return version;
	}

	@Override
	public String description() {
		return description;
	}

	@Override
	public ParamSpec [] getParamSpec() {
		return paramSpec;
	}

	@Override
	public ColumnSpec [][] getInputSpec() {
		return inputSpec;
	}

	@Override
	public ColumnSpec [][] getOutputSpec() {
		return outputSpec;
	}


	@Override
	public Output execute(Input input) {
		// TODO Auto-generated method stub
		return null;
	}

}
