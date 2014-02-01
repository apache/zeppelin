package com.nflabs.zeppelin.zengine;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.nflabs.zeppelin.driver.LazyConnection;
import com.nflabs.zeppelin.driver.ZeppelinConnection;
import com.nflabs.zeppelin.driver.ZeppelinDriverFactory;
import com.nflabs.zeppelin.result.Result;
import com.nflabs.zeppelin.zengine.stmt.Z;

public class ZPlan extends LinkedList<Z>{
	
	public ZPlan(){
		
	}
	public LinkedList<Result> execute(Zengine zengine) throws Exception{
		return execute(zengine, null);
	}
	
	public LinkedList<Result> execute(Zengine zengine, List<Map<String, Object>> params) throws Exception{
		LinkedList<Result> results = new LinkedList<Result>();
		
		ZeppelinDriverFactory driverFactory = zengine.getDriverFactory();

		
		for (int i=0; i<this.size(); i++) {
			Z zz = this.get(i);
			Map<String, Object> p = new HashMap<String, Object>();
			if(params!=null && params.size()>=i+1){
				p = params.get(i);
			}
			
			try {
				zz.withParams(p);
				
				ZeppelinConnection conn = zz.getConnection();
				if (conn instanceof LazyConnection) {
					((LazyConnection)conn).initialize(driverFactory);
				}
				zz.execute(conn);
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
			ZeppelinConnection conn = zz.getConnection();
			if (conn!=null) {
				if (conn.isConnected()){
					conn.close();
				}
			}
		}
	}
}
