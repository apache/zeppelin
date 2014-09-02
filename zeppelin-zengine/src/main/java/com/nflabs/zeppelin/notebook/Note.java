package com.nflabs.zeppelin.notebook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.interpreter.InterpreterFactory;
import com.nflabs.zeppelin.notebook.utility.IdHashes;
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
	
	private transient NoteReplLoader replLoader;
	private transient Scheduler scheduler;
	private transient ZeppelinConfiguration conf;
	
	public Note(){		
	}
	
	public Note(ZeppelinConfiguration conf, NoteReplLoader replLoader, Scheduler scheduler){
		this.conf = conf;
		this.replLoader = replLoader;
		this.scheduler = scheduler;
		generateId();
	}
	
	private void generateId(){
		//id = "note_"+System.currentTimeMillis()+"_"+new Random(System.currentTimeMillis()).nextInt();
	  /** This is actually more humain readable */
	  id = IdHashes.encode(System.currentTimeMillis() + new Random(System.currentTimeMillis()).nextInt());
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

	public NoteReplLoader getNoteReplLoader() {
		return replLoader;
	}

	public void setReplLoader(NoteReplLoader replLoader) {
		this.replLoader = replLoader;
	}
	
	public void setScheduler(Scheduler scheduler) {
		this.scheduler = scheduler;
	}
	
	public void setZeppelinConfiguration(ZeppelinConfiguration conf){
		this.conf = conf;
	}
	
	/**
	 * Add paragraph last
	 * @param p
	 */
	public Paragraph addParagraph() {
		Paragraph p = new Paragraph(this, replLoader);
		synchronized(paragraphs){
			paragraphs.add(p);
		}
		return p;
	}
	
	/**
	 * Insert paragraph in given index
	 * @param index
	 * @param p
	 */
	public Paragraph insertParagraph(int index) {
		Paragraph p = new Paragraph(this, replLoader);
		synchronized(paragraphs){
			paragraphs.add(index, p);
		}
		return p;
	}
	
	/**
	 * Remove paragraph by id
	 * @param paragraphId
	 * @return
	 */
	public Paragraph removeParagraph(String paragraphId) {
		synchronized(paragraphs){
			for(int i=0; i<paragraphs.size(); i++){
				Paragraph p = paragraphs.get(i);
				if(p.getId().equals(paragraphId)) {
					paragraphs.remove(i);
					return p;
				}
			}
		}
		return null;
	}
	
	public Paragraph getParagraph(String paragraphId) {
		synchronized(paragraphs) {
			for(Paragraph p : paragraphs) {
				if(p.getId().equals(paragraphId)) {
					return p;
				}
			}
		}
		return null;
	}
	
	public Paragraph getLastParagraph(){
		synchronized(paragraphs) {
			return paragraphs.get(paragraphs.size()-1);
		}
	}
	
	/**
	 * Run all paragraphs sequentially
	 */
	public void runAll(){
		synchronized(paragraphs){
			for (Paragraph p : paragraphs) {
				p.setNoteReplLoader(replLoader);
				p.setListener(this);
				scheduler.submit(p);
			}
		}
	}
	
	/**
	 * Run a single paragraph
	 * @param paragraphId
	 */
	public void run(String paragraphId, JobListener listener) {
		Paragraph p = getParagraph(paragraphId);
		p.setNoteReplLoader(replLoader);
		p.setListener(listener);
		scheduler.submit(p);
	}
	
	public void run(String paragraphId){
		run(paragraphId, this);
	}

	
	public List<Paragraph> getParagraphs(){
		synchronized(paragraphs) {
			return new LinkedList<Paragraph>(paragraphs);
		}
	}
	
	
	public void persist() throws IOException{
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		Gson gson = gsonBuilder.create();
		
		File dir = new File(conf.getNotebookDir()+"/"+id);
		if(!dir.exists()){
			dir.mkdirs();
		} else if(dir.isFile()) {
			throw new RuntimeException("File already exists"+dir.toString());
		}
				
		File file = new File(conf.getNotebookDir()+"/"+id+"/note.json");
		logger().info("Persist note {} into {}", id, file.getAbsolutePath());
		
		String json = gson.toJson(this);
		FileOutputStream out = new FileOutputStream(file);
		out.write(json.getBytes(conf.getString(ConfVars.ZEPPELIN_ENCODING)));
		out.close();
	}
	
	public void unpersist() throws IOException{
		File dir = new File(conf.getNotebookDir()+"/"+id);

		FileUtils.deleteDirectory(dir);
	}
	
	public static Note load(String id, ZeppelinConfiguration conf, NoteReplLoader replLoader, Scheduler scheduler) throws IOException{
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		Gson gson = gsonBuilder.create();
		
		File file = new File(conf.getNotebookDir()+"/"+id+"/note.json");
		logger().info("Load note {} from {}", id, file.getAbsolutePath());
		
		if(!file.isFile()){
			return null;
		}
		
		FileInputStream ins = new FileInputStream(file);
		String json = IOUtils.toString(ins, conf.getString(ConfVars.ZEPPELIN_ENCODING));
		Note note = gson.fromJson(json, Note.class);
		note.setZeppelinConfiguration(conf);
		note.setReplLoader(replLoader);
		note.setScheduler(scheduler);
		for(Paragraph p : note.paragraphs){
			if(p.getStatus() == Status.PENDING || p.getStatus() == Status.RUNNING){
				p.setStatus(Status.ABORT);
			}
		}
		
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
	
	private static Logger logger(){
		Logger logger = LoggerFactory.getLogger(Note.class);
		return logger;
	}
	
}
