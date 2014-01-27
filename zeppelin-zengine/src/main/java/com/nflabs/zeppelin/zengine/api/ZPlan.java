package com.nflabs.zeppelin.zengine.api;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.nflabs.zeppelin.result.Result;

public class ZPlan extends LinkedList<Z>{
	
	public ZPlan(){
		
	}
	public LinkedList<Result> execute() throws Exception{
		return execute(null);
	}
	
	public LinkedList<Result> execute(List<Map<String, Object>> params) throws Exception{
		LinkedList<Result> results = new LinkedList<Result>();
		
		for (int i=0; i<this.size(); i++) {
			Z zz = this.get(i);
			Map<String, Object> p = new HashMap<String, Object>();
			if(params!=null && params.size()>=i+1){
				p = params.get(i);
			}
			
			try {
				zz.withParams(p);				
				zz.execute();
				results.add(zz.result());
				zz.release();
			} catch (Exception e) {
				closeAllConnections();
				throw e;
			}
		}
		
		closeAllConnections();
		
		return results;
	}
	
	private void closeAllConnections(){
		for (int i=0; i<this.size(); i++) {
			Z zz = this.get(i);
			zz.driver.close();
		}
	}
}
