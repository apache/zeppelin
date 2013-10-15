package com.nflabs.zeppelin.zai;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.zdd.ColumnDesc;
import com.nflabs.zeppelin.zdd.Schema;
import com.nflabs.zeppelin.zdd.ZDD;
import com.nflabs.zeppelin.zrt.ZeppelinRuntime;
import com.nflabs.zeppelin.zrt.ZeppelinRuntimeException;

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
	public abstract DataSpec [] getInputSpec();
	public abstract DataSpec [] getOutputSpec();
	

	public Output run(Input input) throws ParamSpecException, DataSpecException, ZeppelinRuntimeException{
		// validate input and params
		validateParam(input.getParams());
		validateInputData(input.getData());
		
		return execute(input);
	}
	
	public void validateInputData(ZDD [] data) throws DataSpecException, ZeppelinRuntimeException{
		DataSpec[] specs = getInputSpec();
		// no specs. any input is valid
		if(specs==null || specs.length==0) return;
		
		for(int i=0; i<specs.length;i++){
			DataSpec spec = specs[i];
			
			if(data==null || data.length <i+1){
				// no data
				throw new DataSpecException("Requires data");
			}
			
			ColumnSpec[] columnSpecs = spec.getColumnSpecs();
			
			// no column spec assume data is valid
			if(columnSpecs==null) continue;
						
			Schema schema = data[i].schema();
			
			for(ColumnSpec col : columnSpecs){
				ColumnDesc [] descs = schema.getColumns();
				
				ColumnDesc d = null;
				if(col.getColumnIndex()>=0){
					if(descs==null || descs.length<col.getColumnIndex()+1){
						throw new DataSpecException("Require column index: "+col.getColumnIndex());
					} else {
						d = descs[col.getColumnIndex()];
					}
				}
				
				if(col.getColumnName()!=null){
					if(d==null && descs!=null){
						// find columnDesc by name
						for(ColumnDesc cd : descs){
							if(cd.name().equals(col.getColumnName())){
								d = cd;
								break;
							}
						}
					}
					
					if(d==null){
						throw new DataSpecException("Require column name : "+col.getColumnName());
					} else {
						if(d.name().equals(col.getColumnName())==false){
							throw new DataSpecException("Column name expected "+col.getColumnName()+" but found "+d.name());
						}
					}
				}
			}
		}
	}
	
	public void validateParam(Param []params) throws ParamSpecException{
		ParamSpec[] spec = getParamSpec();
		
		// if param spec is not defined, any param is valid
		if(spec==null) return;      
		
		for(ParamSpec s : spec){
			// find parameter
			Param param = null;
			if(params!=null){
				for(Param p :params){
					if(s.getName().equals(p.getName())){
						param = p;
						break;
					}
				}
			}
			
			s.validate(param);
			
		}
	}
	
	
	

	
	
	protected abstract Output execute(Input input) throws ParamSpecException;
}
