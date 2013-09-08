package org.apache.zeppelin.job;

public class JobResult {
	String query; // query to select result
	boolean table; // true if query is table
	
	public JobResult(String query, boolean table) {
		super();
		this.query = query;
		this.table = table;
	}
	
	public String getQuery() {
		return query;
	}
	public void setQuery(String query) {
		this.query = query;
	}
	public boolean isTable() {
		return table;
	}
	public void setTable(boolean table) {
		this.table = table;
	}
	
}
