package com.nflabs.zeppelin.zengine;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Q stands for Query
 * @author moon
 *
 */
public class Q extends Z{
	private String name;
	protected String query;
	private List<URI> resources = new LinkedList<URI>();
	private boolean cache;
	transient static final String PREV_VAR_NAME="arg";
	transient static final String NAME_PREFIX="zp_";
	transient static final Pattern templatePattern = Pattern.compile(".*[$][{]"+PREV_VAR_NAME+"[}].*");
	
	public Q(String query){
		this.query = query;
		name = NAME_PREFIX + this.hashCode();
		cache = false;
	}

	public Q withResource(URI r){
		resources.add(r);
		return this;
	}
	
	public Q withName(String name){
		this.name = name;
		return this;
	}
	
	public Q withCache(boolean cache){
		this.cache  = cache;
		return this;
	}
	
	public boolean isCache(){
		return cache;
	}
		
	@Override
	public String getQuery() throws ZException{
		String q = null;
		if(prev()!=null && prev().name()!=null){
			String prevName = prev().name();
			q = query.replaceAll("[$][{]"+PREV_VAR_NAME+"[}]", prevName.trim());
		} else {
			q = query;
		}
		
		String tableCreation = null;
		if(name==null){
			tableCreation = "";
		} else {
			if(cache){
				tableCreation = "CREATE TABLE "+name+" AS ";
			} else {
				tableCreation = "CREATE VIEW "+name+" AS ";
			}
		}
		
		return tableCreation+q;
	}

	@Override
	public List<URI> getResources() throws ZException {	
		if(prev()==null){
			return resources;
		} else {
			List<URI> r = new LinkedList<URI>();
			r.addAll(resources);
			r.addAll(prev().getResources());
			return r;
		}
	}
	
	@Override
	public String name(){
		return name;
	}
	
	@Override
	public String getCleanQuery() throws ZException {
		if(name==null) return null;
		if(cache==true){
			return "DROP TABLE "+name;
		} else {
			return "DROP VIEW "+name;
		}
	}

}
