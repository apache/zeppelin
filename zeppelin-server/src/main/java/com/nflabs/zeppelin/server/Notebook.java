package com.nflabs.zeppelin.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.hadoop.fs.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.repl.Repl;
import com.nflabs.zeppelin.repl.ReplFactory;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;

/**
 * Consist of Notes 
 *
 */
public class Notebook {
	Logger logger = LoggerFactory.getLogger(Notebook.class);
	
	private FileSystem fs;

	private SchedulerFactory schedulerFactory;

	private ReplFactory replFactory;
	
	Map<String, Note> notes = new HashMap<String, Note>();

	private ZeppelinConfiguration conf;

	public Notebook(ZeppelinConfiguration conf, FileSystem fs, SchedulerFactory schedulerFactory, ReplFactory replFactory) {
		this.conf = conf;
		this.fs = fs;		
		this.schedulerFactory = schedulerFactory;
		this.replFactory = replFactory;
	}
	
	/**
	 * Create new note
	 * @param name
	 * @return
	 */
	public Note createNote() {
		Scheduler scheduler = schedulerFactory.createOrGetFIFOScheduler("note_"+System.currentTimeMillis());		
		Note note = new Note(conf, fs, "Untitled Note "+notes.size(), replFactory, scheduler);
		synchronized(notes){
			notes.put(note.id(), note);
		}
		return note;
	}

	
	public Note getNote(String id){
		synchronized(notes){
			return notes.get(id);
		}
	}
	
	public void removeNote(String id){
		Note note;
		synchronized(notes){
			note = notes.remove(id);
		}
		note.getNoteReplLoader().destroyAll();
	}
	
}
