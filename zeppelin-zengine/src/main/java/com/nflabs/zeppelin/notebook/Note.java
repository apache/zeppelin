package com.nflabs.zeppelin.notebook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
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
import com.nflabs.zeppelin.interpreter.Interpreter;
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
	
	private transient NoteInterpreterLoader replLoader;
	private transient ZeppelinConfiguration conf;

    private Map<String, Object> config = new HashMap<String, Object>(); // note config like looknfeel, etc.

	public Note(){		
	}
	
	public Note(ZeppelinConfiguration conf, NoteInterpreterLoader replLoader){
		this.conf = conf;
		this.replLoader = replLoader;
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

	public NoteInterpreterLoader getNoteReplLoader() {
		return replLoader;
	}

	public void setReplLoader(NoteInterpreterLoader replLoader) {
		this.replLoader = replLoader;
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
	
	/**
	 * Move paragraph into the new index (order from 0 ~ n-1)
	 * @param paragraphId
	 * @param index new index
	 */
	public void moveParagraph(String paragraphId, int index) {
		synchronized(paragraphs){
			int oldIndex = -1;
			Paragraph p = null;
			
			if (index < 0 || index >= paragraphs.size()) return;
			
			for(int i=0; i<paragraphs.size(); i++){
				if(paragraphs.get(i).getId().equals(paragraphId)) {
					oldIndex = i;
					if(oldIndex==index) return;
					p = paragraphs.remove(i);					
				}
			}
			
			if (p==null) {
				return;
			} else {
				if (oldIndex < index) {
					paragraphs.add(index, p);	
				} else {
					paragraphs.add(index, p);	
				}				
			}
		}
	}
	
  public boolean isLastParagraph(String paragraphId) {
    if (!paragraphs.isEmpty()) {
      synchronized (paragraphs) {
        if (paragraphId.equals(paragraphs.get(paragraphs.size() - 1).getId())) {
          return true;
        }
      }
      return false;
    }
    /** because empty list, cannot remove nothing right? */
    return true;
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
	 * @param jobListener 
	 */
	public void runAll(JobListener jobListener){
		synchronized(paragraphs){
			for (Paragraph p : paragraphs) {
				p.setNoteReplLoader(replLoader);
				p.setListener(jobListener);
				Interpreter intp = replLoader.getRepl(p.getRequiredReplName());		
				intp.getScheduler().submit(p);
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
		Interpreter intp = replLoader.getRepl(p.getRequiredReplName());		
		intp.getScheduler().submit(p);
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
	
	public static Note load(String id, ZeppelinConfiguration conf, NoteInterpreterLoader replLoader, Scheduler scheduler) throws IOException{
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
		for(Paragraph p : note.paragraphs){
			if(p.getStatus() == Status.PENDING || p.getStatus() == Status.RUNNING){
				p.setStatus(Status.ABORT);
			}
		}
		
		return note;
	}

	public Map<String, Object> getConfig() {
		return config;
	}

	public void setConfig(Map<String, Object> config) {
		this.config = config;
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

	@Override
	public void onProgressUpdate(Job job, int progress) {
	}
	
}
