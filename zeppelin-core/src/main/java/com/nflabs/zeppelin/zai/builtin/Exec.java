package com.nflabs.zeppelin.zai.builtin;

import java.util.LinkedList;
import java.util.List;

import org.apache.spark.rdd.RDD;

import shark.api.RDDTable;

import com.nflabs.zeppelin.zai.ColumnSpec;
import com.nflabs.zeppelin.zai.Param;
import com.nflabs.zeppelin.zai.ParamSpec;
import com.nflabs.zeppelin.zai.ZeppelinApplication;
import com.nflabs.zeppelin.zai.ParamSpec.StringParamSpec;
import com.nflabs.zeppelin.zdd.ZDD;
import com.nflabs.zeppelin.zrt.ZeppelinRuntimeException;

/**
 * Generic exec application
 * 
 * @author moon
 *
 */
public class Exec extends ZeppelinApplication{

	public Exec(){

	}
	
	@Override
	public String name() {
		return "exec";
	}

	@Override
	public String version() {
		return "0.1";
	}

	@Override
	public String description() {
		return "exec";
	}

	@Override
	public ParamSpec [] getParamSpec() {
		return new ParamSpec[]{
			new ParamSpec.StringParamSpec("command")
						  .withDescription("Command")
						  .withAllowAny(true),
		};			
	}

	@Override
	public ColumnSpec[][] getInputSpec() {
		return null;
	}

	@Override
	public ColumnSpec[][] getOutputSpec() {
		return null;
	}

	@Override
	public ZDD[] execute(ZDD[] input) {
		if(input==null || input.length==0) return new ZDD[]{};;

		RDD rdd;
	//	RDDTable rddTable = new RDDTable(rdd);
		
		return null;
	}
	



}
