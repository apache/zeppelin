package com.nflabs.zeppelin.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
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

	public Notebook(ZeppelinConfiguration conf, FileSystem fs, SchedulerFactory schedulerFactory, ReplFactory replFactory) throws IOException {
		this.conf = conf;
		this.fs = fs;		
		this.schedulerFactory = schedulerFactory;
		this.replFactory = replFactory;
		loadAllNotes();
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
	
	private void loadAllNotes() throws IOException{
		Path notebookDir = new Path(conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_DIR));
		FileStatus[] dirs = fs.listStatus(notebookDir);
		for(FileStatus f : dirs) {
			if(f.isDir()) {
				Scheduler scheduler = schedulerFactory.createOrGetFIFOScheduler("note_"+System.currentTimeMillis());
				logger.info("Loading note from "+f.getPath().getName());
				Note n = Note.load(f.getPath().getName(), conf, fs, replFactory, scheduler);
				synchronized(notes){
					notes.put(n.id(), n);
				}
			}
		}
	}
	
	public List<Note> getAllNotes(){
		synchronized(notes){
			return new LinkedList<Note>(notes.values());
		}
	}
}
