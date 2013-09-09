package com.nflabs.zeppelin.zql;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nflabs.zeppelin.job.Job;
import com.nflabs.zeppelin.job.JobId;

/**
 * 
 * select * from tbl
 * 
 * select * from tbl | select * from (${zql})
 * 
 * select * from tbl1 && select * from tbl2
 * 
 * select * from tbl1 || select * from tbl2
 * 
 * select * from tbl1 ; select * from tbl2
 * 
 * select * from tbl1 | exec wc -l 
 * 
 * select * from tbl1 > tbl3
 * 
 * select * from tbl1 >> tbl3
 * 
 * select * from tbl1 | wc -l | as table test(a int b a
 * 
 * select * from tbl1 && exec --- && exec |   // pipe trying to read last one
 * 
 * email 
 * 
 * 
 * @author moon
 *
 */
public class Zql {
	private String zql;
	private File appRoot;
	transient static Pattern appPattern = Pattern.compile("([^ ()]*)\\s*([(][^)]*[)])?\\s*(.*)");
	transient static Pattern templatePattern = Pattern.compile(".*[$][{](pql|zql)[}].*");
	
	public Zql(String zql, File appRoot){
		this.zql = zql;
		this.appRoot = appRoot;
	}
	
	public List<Job> compile() throws IOException{
		List<Job> jobList = new LinkedList<Job>();
		String[] qls = split(zql, ";");
		
		for(String ql : qls){
			Job job = getJob(ql);
			jobList.add(job);
		}
		return jobList;
	}
	
	private Job getJob(String zql) throws IOException{
		List<File> resources = new LinkedList<File>();
		String[] qls = split(zql, "\\|");
		String sql = "";
		for(String qr : qls){
			if(qr==null) continue;
			qr = qr.trim();
			if(qr.length()==0) continue;
			
			// check if query has template or not
			if(templatePattern.matcher(qr).matches()==true){ // ${pql} found
				sql = qr.replaceAll("[$][{](pql|zql)[}]", sql.trim());				
			} else { // add pql at the tail
				if(sql==null){
					sql = qr;
				} else {
					sql = qr + " " + sql;
				}
			}
			Matcher matcher = appPattern.matcher(sql);
			if(matcher.matches()==true && matcher.groupCount()>0){
				String appName = matcher.group(1);
				if(appName!=null){
					App app = App.load(new File(appRoot.getAbsoluteFile()+"/"+appName));
					if(app!=null){
						sql = app.getQuery(getAppParams(matcher.group(2)), matcher.group(3));
						List<File> files = app.getResources();
						resources.addAll(files);
					}
				}
			}
			
		}
		sql = sql.trim();
		
		return new Job(new JobId(), sql, (File[])resources.toArray(new File[]{}));
	}
	
	private Map<String, String> getAppParams(String param){
		Map<String, String> params = new HashMap<String, String>();

		if (param == null || param.length() == 0)
			return params;
		param = param.trim();
		param = param.substring(1, param.length() - 1);
		param = param.trim();
		for (String kvs : param.split(",")) {
			int p = kvs.indexOf('=');
			if (p < 0)
				continue;
			String key = kvs.substring(0, p);
			String value = "";
			if (p < kvs.length())
				value = kvs.substring(p + 1);
			params.put(key.trim(), value.trim());
		}
		return params;
		
	}
	
	private String [] split(String str, String splitter){
		return str.split(splitter+"(?=([^\"']*\"[^\"']*\")*[^\"']*$)");
	}
}
