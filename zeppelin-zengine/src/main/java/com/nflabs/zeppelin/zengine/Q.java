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
	private String query;
	private List<URI> resources = new LinkedList<URI>();
	transient static final String PREV_VAR_NAME="arg";
	transient static final Pattern templatePattern = Pattern.compile(".*[$][{]"+PREV_VAR_NAME+"[}].*");
	
	public Q(String query){
		this.query = query;
	}

	public Q withResource(URI r){
		resources.add(r);
		return this;
	}
	
	@Override
	public String getQuery() throws ZException{
		if(prev()==null){
			return query;
		} else if(query==null){
			return prev().getQuery();
		} else {
			String prevQuery = prev().getQuery();
			Matcher m = templatePattern.matcher(query);
			if(m.matches()){
				return query.replaceAll("[$][{]"+PREV_VAR_NAME+"[}]", prevQuery.replaceAll("\\$", "\\\\\\$").trim());
			} else {
				return query+" "+prevQuery; 
			}			
		}
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
	public void clean() throws ZException {
		if(prev()!=null){
			prev().clean();
		}
	}

}
