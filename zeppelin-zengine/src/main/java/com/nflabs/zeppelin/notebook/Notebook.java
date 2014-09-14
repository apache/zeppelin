package com.nflabs.zeppelin.notebook;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.interpreter.InterpreterFactory;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;

/**
 * Consist of Notes 
 *
 */
public class Notebook {
	Logger logger = LoggerFactory.getLogger(Notebook.class);

	private SchedulerFactory schedulerFactory;
	private InterpreterFactory replFactory;
	/** Keep the order */
	Map<String, Note> notes = new LinkedHashMap<String, Note>();

	private ZeppelinConfiguration conf;

	public Notebook(ZeppelinConfiguration conf, SchedulerFactory schedulerFactory, InterpreterFactory replFactory) throws IOException {
		this.conf = conf;
		this.schedulerFactory = schedulerFactory;
		this.replFactory = replFactory;
		loadAllNotes();
	}
	
	private boolean isLoaderStatic(){
		return "share".equals(conf.getString(ConfVars.ZEPPELIN_INTERPRETER_MODE));
	}
	
	/**
	 * Create new note
	 * @param name
	 * @return
	 */
	public Note createNote() {
		Note note = new Note(conf, new NoteInterpreterLoader(replFactory, schedulerFactory, isLoaderStatic()));
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
		try {
			note.unpersist();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void loadAllNotes() throws IOException{
		File notebookDir = new File(conf.getNotebookDir());
		File[] dirs = notebookDir.listFiles();
		if(dirs==null) return;
		for(File f : dirs) {
			if(f.isDirectory()) {
				Scheduler scheduler = schedulerFactory.createOrGetFIFOScheduler("note_"+System.currentTimeMillis());
				logger.info("Loading note from "+f.getName());
				Note n = Note.load(f.getName(), conf, new NoteInterpreterLoader(replFactory, schedulerFactory, isLoaderStatic()), scheduler);
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
