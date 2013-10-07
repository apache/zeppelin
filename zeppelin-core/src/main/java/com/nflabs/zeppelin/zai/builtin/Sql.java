package com.nflabs.zeppelin.zai.builtin;

import com.nflabs.zeppelin.zai.ColumnSpec;
import com.nflabs.zeppelin.zai.Input;
import com.nflabs.zeppelin.zai.Output;
import com.nflabs.zeppelin.zai.ParamSpec;
import com.nflabs.zeppelin.zai.ParamSpecException;
import com.nflabs.zeppelin.zai.ZeppelinApplication;
import com.nflabs.zeppelin.zdd.ZDD;
import com.nflabs.zeppelin.zrt.ZeppelinRuntime;

public class Sql extends ZeppelinApplication{

	public Sql(ZeppelinRuntime runtime) {
		super(runtime);
	}

	@Override
	public String name() {
		return "sql";
	}

	@Override
	public String version() {
		return "0.1";
	}

	@Override
	public String description() {
		return "Run sql over the data";
	}

	@Override
	public ParamSpec[] getParamSpec() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ColumnSpec[][] getInputSpec() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ColumnSpec[][] getOutputSpec() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected Output execute(Input input) throws ParamSpecException {
		ZeppelinRuntime rt = getRuntime();
		ZDD[] zdds = input.getData();
		if(zdds == null || zdds.length==0) return null;
		
		String query = (String) getParam("query");
		if(query == null) throw new ParamSpecException("Param 'query' not defined");
		
		ZDD[] outs = new ZDD[zdds.length];
		
		// replace ${name} to zdd's table name
		for(int i=0; i<zdds.length; i++){
			// run query
			outs[i] = rt.fromSql(replaceName(query, zdds[0].name()));
		}
		
		return new Output(outs, null);
	}
	
	private String replaceName(String query, String tableName){
		return query;
	}
	

}
