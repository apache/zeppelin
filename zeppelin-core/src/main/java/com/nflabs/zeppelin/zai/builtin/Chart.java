package com.nflabs.zeppelin.zai.builtin;

import com.nflabs.zeppelin.zai.ColumnSpec;
import com.nflabs.zeppelin.zai.DataSpec;
import com.nflabs.zeppelin.zai.Input;
import com.nflabs.zeppelin.zai.Output;
import com.nflabs.zeppelin.zai.ParamSpec;
import com.nflabs.zeppelin.zai.ZeppelinApplication;
import com.nflabs.zeppelin.zdd.ZDD;
import com.nflabs.zeppelin.zrt.ZeppelinRuntime;

public class Chart extends ZeppelinApplication {

	public Chart(ZeppelinRuntime runtime) {
		super(runtime);
	}

	@Override
	public String name() {
		return "chart";
	}

	@Override
	public String version() {
		return "0.1";
	}

	@Override
	public String description() {
		return "Draw chart";
	}

	@Override
	public ParamSpec[] getParamSpec() {
		return null;
	}

	@Override
	public DataSpec[] getInputSpec() {
		return null;
	}

	@Override
	public DataSpec[] getOutputSpec() {
		return null;
	}

	@Override
	protected Output execute(Input input) {
		
		return null;
	}



}
