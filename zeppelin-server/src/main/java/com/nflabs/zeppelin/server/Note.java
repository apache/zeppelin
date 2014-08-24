package com.nflabs.zeppelin.server;

import java.io.IOException;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.repl.Repl;
import com.nflabs.zeppelin.scheduler.Job;
import com.nflabs.zeppelin.scheduler.Job.Status;
import com.nflabs.zeppelin.scheduler.JobListener;
import com.nflabs.zeppelin.scheduler.Scheduler;

/**
 * Consist of Paragraphs with independent context
 *
 */
public class Note implements Serializable, JobListener {
	List<Paragraph> paragraphs = new LinkedList<Paragraph>();
	private String name;
	private String id;
	
	private transient Map<String, Repl> repls;
	private transient Scheduler scheduler;
	private transient ZeppelinConfiguration conf;
	private transient FileSystem fs;
	
	public Note(ZeppelinConfiguration conf, FileSystem fs, String name, Map<String, Repl> repls, Scheduler scheduler){
		this.conf = conf;
		this.fs = fs;
		this.name = name;
		this.repls = repls;
		this.scheduler = scheduler;
		generateId();
	}
	
	private void generateId(){
		id = "note_"+System.currentTimeMillis()+"_"+new Random(System.currentTimeMillis()).nextInt();
	}
	
	public String id(){
		return id;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Map<String, Repl> getRepls() {
		return repls;
	}

	public void setRepls(Map<String, Repl> repls) {
		this.repls = repls;
	}
	
	public void setScheduler(Scheduler scheduler) {
		this.scheduler = scheduler;
	}
	
	public void setZeppelinConfiguration(ZeppelinConfiguration conf){
		this.conf = conf;
	}
	
	public void setFileSystem(FileSystem fs) {
		this.fs = fs;
	}

	/**
	 * Run all paragraphs sequentially
	 */
	public void runAll(){
		for (Paragraph p : paragraphs) {
			p.setRepls(repls);
			p.setListener(this);
			scheduler.submit(p);
		}
	}
	
	/**
	 * Run a single paragraph
	 * @param paragraphId
	 */
	public void run(String paragraphId) {
		Paragraph p = getParagraph(paragraphId);
		p.setRepls(repls);
		p.setListener(this);
		scheduler.submit(p);
	}
	
	public Paragraph getParagraph(String paragraphId) {
		for(Paragraph p : paragraphs) {
			if(p.getId().equals(paragraphId)) {
				return p;
			}
		}
		return null;
	}
	
	public List<Paragraph> getParagraphs(){
		return paragraphs;
	}
	
	
	public void persist() throws IOException{
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		Gson gson = gsonBuilder.create();
		
		Path dir = new Path(conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_DIR)+"/"+id);
		if(!fs.exists(dir)){
			fs.mkdirs(dir);
		} else if(fs.isFile(dir)) {
			throw new RuntimeException("File already exists"+dir.toString());
		}
		
		Path file = new Path(conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_DIR)+"/"+id+"/note.json");
		
		String json = gson.toJson(this);
		FSDataOutputStream out;
		out = fs.create(file, true);
		out.write(json.getBytes(conf.getString(ConfVars.ZEPPELIN_ENCODING)));
		out.close();
	}
	
	public static Note load(String id, ZeppelinConfiguration conf, FileSystem fs, Map<String, Repl> repls, Scheduler scheduler) throws IOException{
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		Gson gson = gsonBuilder.create();
		
		Path file = new Path(conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_DIR)+"/"+id+"/note.json");
		if(!fs.isFile(file)){
			return null;
		}
		
		FSDataInputStream ins = fs.open(file);
		String json = IOUtils.toString(ins, conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_DIR));
		Note note = gson.fromJson(json, Note.class);
		note.setZeppelinConfiguration(conf);
		note.setFileSystem(fs);
		note.setRepls(repls);
		note.setScheduler(scheduler);
		
		return note;
	}
	
	@Override
	public void beforeStatusChange(Job job, Status before, Status after) {
		Paragraph p = (Paragraph) job;
	}

	@Override
	public void afterStatusChange(Job job, Status before, Status after) {
		Paragraph p = (Paragraph) job;
	}
	
	
}
