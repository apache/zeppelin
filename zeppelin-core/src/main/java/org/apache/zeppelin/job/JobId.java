package org.apache.zeppelin.job;

import java.util.Date;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JobId {
	private String id;

	/**
	 * Generate new JobId
	 */
	public JobId(){
		Random random = new Random();
		id = "zeppelin-job_"+System.currentTimeMillis()+"_"+random.nextInt(100);
	}
	
	public int hashCode(){
		return id.hashCode();
	}
	
	/**
	 * Set job id
	 * @param id
	 */
	public JobId(String id){
		this.id = id;
	}
	
	public String toString(){
		return id;
	}
	
	public String id(){
		return id;
	}
	
	public boolean equals(Object o){
		return id.equals(((JobId)o).id());
	}
	
	public Date getDate(){
		Pattern p = Pattern.compile("zeppelin-job_([0-9]*)_[0-9]*");		
		Matcher m = p.matcher(id);
		if(m.matches()){
			return new Date(Long.parseLong(m.group(1)));
		} else {
			return null;
		}
	}
}
