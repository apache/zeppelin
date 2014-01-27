package com.nflabs.zeppelin.zengine.api;

import com.nflabs.zeppelin.util.Util;
import com.nflabs.zeppelin.zengine.ZQLException;

/**
 * 
 * @author moon
 */
public class RedirectStatement {
	@SuppressWarnings("unused")
    private String stmt;
	private String name;
	private boolean table = false;

	/**
	 * following form
	 * 
	 * table [tablename]
	 * view [viewname]
	 * [tablename]
	 * [viewname]
	 * 
	 * @param stmt
	 * @throws ZQLException 
	 */
	public RedirectStatement(String stmt) throws ZQLException{
		this.stmt = stmt;
		String[] ss = Util.split(stmt, ' ');
		if(ss!=null){
			if(ss.length==2){
				if(ss[0].toLowerCase().equals("table")){
					table = true;
				} else if(ss[0].toLowerCase().equals("view")){
					table = false;
				} else {
					throw new ZQLException("Invalid redirection statement "+ss[0]);
				}
				name = ss[1];
			} else if(ss.length==1){
				name = ss[0];
			} else {
				throw new ZQLException("Invalid redirection statment. "+stmt);
			}
		}
	}
	
	/**
	 * Get table(view) name
	 * @return
	 */
	public String getName(){
		return name;
	}
	
	/**
	 * check if it is table or view 
	 * @return
	 */
	public boolean isTable(){
		return table;
	}
}
