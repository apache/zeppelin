package org.apache.zeppelin.job;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;



public class JobHistoryManager {
	private File path;
	private Gson gson;
	SimpleDateFormat sd = new SimpleDateFormat("yyyyMMdd");
	
	public JobHistoryManager(File path){
		this.path = path;
		gson = new GsonBuilder().setPrettyPrinting().create();
	}
	
	private File getDirectory(JobId jobId){
		return getDirectory(jobId.getDate());
	}
	
	private File getDirectory(Date date){
		String dateString = sd.format(date);
		return new File(path.getAbsolutePath()+"/"+dateString);
	}
	
	
	
	private File getFile(JobId jobId){
		return new File(getDirectory(jobId).getAbsolutePath()+"/"+jobId.toString());
	}
	
	
	public synchronized void save(JobInfo jobInfo) throws IOException{
		JobId jobId = jobInfo.getJob().getJobId();
		
		getDirectory(jobId).mkdirs();
		File file = getFile(jobId);
		

		String str = gson.toJson(jobInfo);
		FileWriter fw = new FileWriter(file);
		fw.append(str);
		fw.close();
	}
	
	public JobInfo load(JobId jobId) throws IOException{
		StringBuilder contents = new StringBuilder();
		File file = getFile(jobId);
		BufferedReader fr = new BufferedReader(new FileReader(file));
		String line = null;
		while (( line = fr.readLine()) != null){
			contents.append(line);
	        contents.append(System.getProperty("line.separator"));
	    }
		fr.close();
		JobInfo jobInfo = gson.fromJson(contents.toString(), JobInfo.class);
		return jobInfo;
	}
	
	public List<JobInfo> getAll(Date fromInclusive, Date toExclusive) throws IOException{
		List<JobInfo> jobList = new LinkedList<JobInfo>();
		Date p = fromInclusive;
		while(p.before(toExclusive)){
			File dir = getDirectory(p);
			p = new Date(p.getTime()+1000*60*60*24);
			
			if(dir.isDirectory()==false) continue;
			File [] files = dir.listFiles();
			Arrays.sort(files, new SortByFileName());
			
			for(File jobFile : files){
				JobId id = new JobId(jobFile.getName());
				
				if(id.getDate().before(fromInclusive) || id.getDate().before(toExclusive)==false){
					break;
				}
				JobInfo jobInfo = load(id);				
				jobList.add(jobInfo);
			}
			
		}
		return jobList; 
	}
	class SortByFileName implements Comparator<File>{

	     public int compare(File f1, File f2) {
	    	 return f1.getName().compareTo(f2.getName());
	     }
	}

}
